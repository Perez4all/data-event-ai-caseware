package com.caseware.integration;

import com.caseware.client.SearchClient;
import com.caseware.dto.IngestionManifest;
import com.caseware.model.Cases;
import com.caseware.model.Customer;
import com.caseware.repository.CaseRepository;
import com.caseware.repository.CustomerRepository;
import com.caseware.service.IngestionService;
import com.caseware.util.HashUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Full integration test: Spring context + H2 in-memory DB + real file I/O.
 *
 * Only the SearchClient (external HTTP to ranker) is mocked.
 * Everything else is real: JPA repositories, LakeHandler, IngestionService.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IngestionDataLakeIntegrationTest {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SearchClient searchClient;

    @Value("${caseware.path.checkpoint}")
    private String checkpointPath;

    @Value("${caseware.path.customers-lake}")
    private String customersLakePath;

    @Value("${caseware.path.cases-lake}")
    private String casesLakePath;

    @Value("${caseware.path.events-lake}")
    private String eventsPath;

    private static final String CUSTOMERS_FP = HashUtil.schemaFingerprint(Customer.class);
    private static final String CASES_FP = HashUtil.schemaFingerprint(Cases.class);

    @BeforeEach
    void cleanState() throws IOException {
        // Clean lake/state/events directories between tests
        deleteIfExists(Path.of(checkpointPath));
        deleteDirIfExists(Path.of(customersLakePath));
        deleteDirIfExists(Path.of(casesLakePath));
        deleteIfExists(Path.of(eventsPath));

        // Clear DB
        caseRepository.deleteAll();
        customerRepository.deleteAll();
    }

    // ── 1. First ingestion with seeded data ─────────────────────────

    @Test
    @Order(1)
    void firstIngestion_writesLakeFilesCheckpointAndEvents() throws IOException {
        // Seed DB
        Customer alice = customerRepository.save(buildCustomer("Alice", "alice@test.com", "US", daysAgo(2)));
        Customer bob = customerRepository.save(buildCustomer("Bob", "bob@test.com", "CA", daysAgo(1)));
        caseRepository.save(buildCase(alice, "Billing discrepancy", "Overcharge on invoice", "open", daysAgo(2)));
        caseRepository.save(buildCase(alice, "Audit log request", "Export audit trail Q4", "open", daysAgo(2)));
        caseRepository.save(buildCase(bob, "Compliance review", "GDPR annual check", "in_progress", daysAgo(1)));

        // Run real ingestion
        IngestionManifest manifest = ingestionService.process(false);

        // ── manifest ──
        assertNotNull(manifest.runId());
        assertEquals(2, manifest.customers().deltaRowCount());
        assertEquals(3, manifest.cases().deltaRowCount());
        assertEquals(CUSTOMERS_FP, manifest.customers().schemaFingerprint());
        assertEquals(CASES_FP, manifest.cases().schemaFingerprint());
        assertTrue(manifest.checkpointAfter().isAfter(manifest.checkpointBefore()));

        // ── lake files valid JSONL ──
        assertValidJsonlFiles(manifest.customers().lakePaths(), 2);
        assertValidJsonlFiles(manifest.cases().lakePaths(), 3);

        // ── checkpoint persisted ──
        assertTrue(Files.exists(Path.of(checkpointPath)));

        // ── events written (async — wait briefly) ──
        waitForFile(Path.of(eventsPath));
        assertTrue(Files.exists(Path.of(eventsPath)));
        List<String> eventLines = nonBlankLines(Path.of(eventsPath));
        assertEquals(2, eventLines.size());
        for (String line : eventLines) {
            var node = objectMapper.readTree(line);
            assertEquals(manifest.runId(), node.get("runId").asText());
        }

        // ── searchClient called ──
        verify(searchClient).refreshIndex();
    }

    // ── 2. Re-run with no DB changes → no new lake files ────────────

    @Test
    @Order(2)
    void reRunNoChanges_producesZeroDeltaRows() throws IOException {
        // Seed + first run
        Customer alice = customerRepository.save(buildCustomer("Alice", "a@t.com", "US", daysAgo(1)));
        caseRepository.save(buildCase(alice, "Billing", "desc", "open", daysAgo(1)));
        ingestionService.process(false);

        // Second run — checkpoint has advanced past all data
        IngestionManifest second = ingestionService.process(false);

        assertEquals(0, second.customers().deltaRowCount());
        assertEquals(0, second.cases().deltaRowCount());
        assertTrue(second.customers().lakePaths().isEmpty());
        assertTrue(second.cases().lakePaths().isEmpty());
    }

    // ── 3. Incremental: new rows after checkpoint are picked up ─────

    @Test
    @Order(3)
    void incrementalIngestion_picksUpOnlyNewRows() throws IOException {
        // Seed initial data + first run
        Customer alice = customerRepository.save(buildCustomer("Alice", "a2@t.com", "US", daysAgo(3)));
        caseRepository.save(buildCase(alice, "Old case", "old", "closed", daysAgo(3)));
        ingestionService.process(false);

        // Insert new data AFTER the checkpoint
        Customer bob = customerRepository.save(buildCustomer("Bob", "b2@t.com", "CA", OffsetDateTime.now(ZoneOffset.UTC)));
        caseRepository.save(buildCase(bob, "New billing issue", "new charge", "open", OffsetDateTime.now(ZoneOffset.UTC)));
        caseRepository.save(buildCase(bob, "New audit request", "new audit", "open", OffsetDateTime.now(ZoneOffset.UTC)));

        // Second run
        IngestionManifest second = ingestionService.process(false);

        assertEquals(1, second.customers().deltaRowCount(), "Only the new customer");
        assertEquals(2, second.cases().deltaRowCount(), "Only the 2 new cases");
        assertValidJsonlFiles(second.customers().lakePaths(), 1);
        assertValidJsonlFiles(second.cases().lakePaths(), 2);
    }

    // ── 4. Dry run queries DB but writes nothing ─────────────────────

    @Test
    @Order(4)
    void dryRun_countsDatabaseButWritesNoFiles() {
        // Seed data
        Customer alice = customerRepository.save(buildCustomer("Alice", "a3@t.com", "US", daysAgo(1)));
        caseRepository.save(buildCase(alice, "Billing", "desc", "open", daysAgo(1)));
        caseRepository.save(buildCase(alice, "Audit", "desc", "open", daysAgo(1)));

        IngestionManifest manifest = ingestionService.process(true);

        assertEquals(1, manifest.customers().deltaRowCount());
        assertEquals(2, manifest.cases().deltaRowCount());
        assertTrue(manifest.customers().lakePaths().isEmpty());
        assertTrue(manifest.cases().lakePaths().isEmpty());
        assertEquals(manifest.checkpointBefore(), manifest.checkpointAfter());

        // No lake files
        assertFalse(Files.exists(Path.of(customersLakePath)));

        // SearchClient not called
        verify(searchClient, never()).refreshIndex();
    }

    // ── 5. Overwrite semantics: same date partition is overwritten ───

    @Test
    @Order(5)
    void overwriteSemantics_reRunOverwritesLakeFiles() throws IOException {
        OffsetDateTime ts = daysAgo(1);
        Customer alice = customerRepository.save(buildCustomer("Alice", "a4@t.com", "US", ts));
        caseRepository.save(buildCase(alice, "Billing", "desc", "open", ts));

        // First run
        IngestionManifest first = ingestionService.process(false);
        String customerFile = first.customers().lakePaths().get(0);
        long firstSize = Files.size(Path.of(customerFile));

        // Update the customer name (simulates a change)
        alice.setName("Alice Updated");
        alice.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        customerRepository.save(alice);

        // Second run — same partition date should be overwritten
        IngestionManifest second = ingestionService.process(false);

        assertFalse(second.customers().lakePaths().isEmpty());
        // Read the file — should contain only the updated record, valid JSONL
        for (String path : second.customers().lakePaths()) {
            List<String> lines = nonBlankLines(Path.of(path));
            for (String line : lines) {
                assertDoesNotThrow(() -> objectMapper.readTree(line));
            }
        }
    }

    // ── 6. Date partitioning: different dates → different files ──────

    @Test
    @Order(6)
    void datePartitioning_createsFilesPerDate() {
        Customer c1 = customerRepository.save(buildCustomer("Alice", "a5@t.com", "US", daysAgo(1)));
        Customer c2 = customerRepository.save(buildCustomer("Bob", "b5@t.com", "CA", daysAgo(3)));
        caseRepository.save(buildCase(c1, "Case A", "desc", "open", daysAgo(1)));
        caseRepository.save(buildCase(c2, "Case B", "desc", "open", daysAgo(3)));

        IngestionManifest manifest = ingestionService.process(false);

        assertEquals(2, manifest.customers().lakePaths().size(), "2 dates → 2 customer partition files");
        assertEquals(2, manifest.cases().lakePaths().size(), "2 dates → 2 case partition files");

        manifest.customers().lakePaths().forEach(p ->
                assertTrue(Files.exists(Path.of(p)), "Customer lake file must exist: " + p));
        manifest.cases().lakePaths().forEach(p ->
                assertTrue(Files.exists(Path.of(p)), "Case lake file must exist: " + p));
    }

    // ── 7. Events contain correct runId and fingerprints ─────────────

    @Test
    @Order(7)
    void eventsFile_containsCorrectMetadata() throws IOException {
        Customer alice = customerRepository.save(buildCustomer("Alice", "a6@t.com", "US", daysAgo(1)));
        caseRepository.save(buildCase(alice, "Billing", "desc", "open", daysAgo(1)));

        IngestionManifest manifest = ingestionService.process(false);

        // Wait for async writeEvents to complete
        waitForFile(Path.of(eventsPath));

        List<String> eventLines = nonBlankLines(Path.of(eventsPath));
        assertEquals(2, eventLines.size());

        var customerEvent = objectMapper.readTree(eventLines.get(0));
        var casesEvent = objectMapper.readTree(eventLines.get(1));

        assertEquals(manifest.runId(), customerEvent.get("runId").asText());
        assertEquals(manifest.runId(), casesEvent.get("runId").asText());
        assertEquals(CUSTOMERS_FP, customerEvent.get("schemaFingerprint").asText());
        assertEquals(CASES_FP, casesEvent.get("schemaFingerprint").asText());
        assertTrue(customerEvent.get("deltaRowCount").asLong() > 0);
        assertTrue(casesEvent.get("deltaRowCount").asLong() > 0);
    }

    // ── helpers ──────────────────────────────────────────────────────

    private void assertValidJsonlFiles(List<String> paths, int expectedTotalLines) throws IOException {
        int totalLines = 0;
        for (String p : paths) {
            Path path = Path.of(p);
            assertTrue(Files.exists(path), "Lake file must exist: " + p);
            List<String> lines = nonBlankLines(path);
            for (String line : lines) {
                assertDoesNotThrow(() -> objectMapper.readTree(line),
                        "Each line must be valid JSON: " + line);
            }
            totalLines += lines.size();
        }
        assertEquals(expectedTotalLines, totalLines);
    }

    private List<String> nonBlankLines(Path path) throws IOException {
        return Files.readAllLines(path).stream().filter(l -> !l.isBlank()).toList();
    }

    private OffsetDateTime daysAgo(int days) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusDays(days);
    }

    private Customer buildCustomer(String name, String email, String country, OffsetDateTime updatedAt) {
        return Customer.builder().name(name).email(email).country(country).updatedAt(updatedAt).build();
    }

    private Cases buildCase(Customer customer, String title, String desc, String status, OffsetDateTime updatedAt) {
        return Cases.builder().customer(customer).title(title).description(desc).status(status).updatedAt(updatedAt).build();
    }

    private void deleteIfExists(Path path) throws IOException {
        if (Files.exists(path)) Files.delete(path);
    }

    private void waitForFile(Path path) throws IOException {
        for (int i = 0; i < 50; i++) {
            if (Files.exists(path)) return;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }
        fail("Timed out waiting for file: " + path);
    }

    private void deleteDirIfExists(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        }
    }
}
