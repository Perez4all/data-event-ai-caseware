package com.caseware.service.handler;

import com.caseware.dto.Checkpoint;
import com.caseware.dto.IngestionManifest;
import com.caseware.model.Cases;
import com.caseware.model.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LakeHandlerTest {

    @TempDir
    Path tempDir;

    private LakeHandler lakeHandler;

    private Path checkpointFile;
    private Path eventsFile;
    private Path customersLake;
    private Path casesLake;

    private static final String CUSTOMERS_FP = "3fda1462b573d1b21d6b3a853a5311828f5a16dd72d912e29c1bc5d8c63ff0ad";
    private static final String CASES_FP = "001a11d0b0167f2a482c7300d68cdeb7a0fc8a0c16e76c877225514e791f70e5";

    @BeforeEach
    void setUp() throws Exception {
        lakeHandler = new LakeHandler(new ObjectMapper());

        checkpointFile = tempDir.resolve("state/checkpoint.json");
        eventsFile = tempDir.resolve("events/events.jsonl");
        customersLake = tempDir.resolve("lake/customers");
        casesLake = tempDir.resolve("lake/cases");

        Files.createDirectories(checkpointFile.getParent());
        Files.createDirectories(eventsFile.getParent());

        setField("checkPointPath", checkpointFile.toString());
        setField("eventsPath", eventsFile.toString());
        setField("customerLakePath", customersLake.toString());
        setField("caseLakePath", casesLake.toString());
        setField("lakeOutputFilename", "data.jsonl");
        setField("fingerprintCases", CASES_FP);
        setField("fingerprintCustomers", CUSTOMERS_FP);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = LakeHandler.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(lakeHandler, value);
    }

    // -- readCheckPoint --

    @Test
    void readCheckPoint_returnsEpoch_whenFileDoesNotExist() {
        Checkpoint cp = lakeHandler.readCheckPoint();
        assertNotNull(cp);
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"), cp.dateTime());
    }

    @Test
    void readCheckPoint_returnsPersistedValue_afterWrite() {
        Checkpoint written = lakeHandler.writeCheckPoint();
        Checkpoint read = lakeHandler.readCheckPoint();
        assertNotNull(read.dateTime());
        // Both should be close (written just before read)
        assertTrue(read.dateTime().isAfter(OffsetDateTime.parse("2020-01-01T00:00:00Z")));
    }

    // -- writeCheckPoint --

    @Test
    void writeCheckPoint_createsFileOnDisk() {
        lakeHandler.writeCheckPoint();
        assertTrue(Files.exists(checkpointFile));
    }

    @Test
    void writeCheckPoint_overwrites_onReRun() {
        Checkpoint first = lakeHandler.writeCheckPoint();
        Checkpoint second = lakeHandler.writeCheckPoint();
        // second call should not throw; file is overwritten
        assertNotNull(second.dateTime());
    }

    // -- writeLakeDeltaRows --

    @Test
    void writeLakeDeltaRows_createsJsonlFiles() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Customer c1 = buildCustomer(1L, "Alice", "alice@test.com", "US", now);
        Cases case1 = buildCase(1L, c1, "Billing issue", "desc", "open", now);

        IngestionManifest.TableManifest[] manifests = lakeHandler.writeLakeDeltaRows(List.of(c1), List.of(case1));

        assertEquals(2, manifests.length);
        // Customers manifest
        assertEquals(1, manifests[0].deltaRowCount());
        assertFalse(manifests[0].lakePaths().isEmpty());
        // Cases manifest
        assertEquals(1, manifests[1].deltaRowCount());
        assertFalse(manifests[1].lakePaths().isEmpty());

        // Verify files exist on disk
        manifests[0].lakePaths().forEach(p -> assertTrue(Files.exists(Path.of(p)), "Customer lake file must exist: " + p));
        manifests[1].lakePaths().forEach(p -> assertTrue(Files.exists(Path.of(p)), "Cases lake file must exist: " + p));
    }

    @Test
    void writeLakeDeltaRows_producesValidJsonl() throws IOException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Customer c1 = buildCustomer(1L, "Alice", "alice@test.com", "US", now);
        Customer c2 = buildCustomer(2L, "Bob", "bob@test.com", "CA", now);

        IngestionManifest.TableManifest[] manifests = lakeHandler.writeLakeDeltaRows(List.of(c1, c2), List.of());

        String lakePath = manifests[0].lakePaths().get(0);
        List<String> lines = Files.readAllLines(Path.of(lakePath));
        // Filter out empty trailing lines
        List<String> nonEmpty = lines.stream().filter(l -> !l.isBlank()).toList();
        assertEquals(2, nonEmpty.size(), "Should have 2 JSONL lines for 2 customers");

        ObjectMapper om = new ObjectMapper();
        for (String line : nonEmpty) {
            assertDoesNotThrow(() -> om.readTree(line), "Each line must be valid JSON: " + line);
        }
    }

    @Test
    void writeLakeDeltaRows_overwritesOnReRun_noDuplicates() throws IOException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Customer c1 = buildCustomer(1L, "Alice", "alice@test.com", "US", now);

        lakeHandler.writeLakeDeltaRows(List.of(c1), List.of());
        // Run again with same data
        IngestionManifest.TableManifest[] manifests = lakeHandler.writeLakeDeltaRows(List.of(c1), List.of());

        String lakePath = manifests[0].lakePaths().get(0);
        List<String> lines = Files.readAllLines(Path.of(lakePath)).stream().filter(l -> !l.isBlank()).toList();
        assertEquals(1, lines.size(), "Re-run must overwrite, not append — still 1 line");
    }

    @Test
    void writeLakeDeltaRows_handlesEmptyLists() {
        IngestionManifest.TableManifest[] manifests = lakeHandler.writeLakeDeltaRows(List.of(), List.of());

        assertEquals(0, manifests[0].deltaRowCount());
        assertEquals(0, manifests[1].deltaRowCount());
        assertTrue(manifests[0].lakePaths().isEmpty());
        assertTrue(manifests[1].lakePaths().isEmpty());
    }

    @Test
    void writeLakeDeltaRows_partitionsByDate() {
        OffsetDateTime day1 = OffsetDateTime.parse("2026-03-18T10:00:00Z");
        OffsetDateTime day2 = OffsetDateTime.parse("2026-03-19T10:00:00Z");
        Customer c1 = buildCustomer(1L, "Alice", "alice@test.com", "US", day1);
        Customer c2 = buildCustomer(2L, "Bob", "bob@test.com", "CA", day2);

        IngestionManifest.TableManifest[] manifests = lakeHandler.writeLakeDeltaRows(List.of(c1, c2), List.of());

        assertEquals(2, manifests[0].lakePaths().size(), "2 customers on different dates → 2 partition files");
    }

    // -- writeEvents --

    @Test
    void writeEvents_writesJsonlToEventsFile() throws IOException {
        Files.createDirectories(eventsFile.getParent());

        IngestionManifest manifest = new IngestionManifest(
                "run-1", OffsetDateTime.now(), OffsetDateTime.now(),
                new IngestionManifest.TableManifest(5, List.of("/lake/customers/2026-03-20/data.jsonl"), CUSTOMERS_FP),
                new IngestionManifest.TableManifest(10, List.of("/lake/cases/2026-03-20/data.jsonl"), CASES_FP),
                OffsetDateTime.parse("1970-01-01T00:00:00Z"),
                OffsetDateTime.now()
        );

        lakeHandler.writeEvents(manifest);

        assertTrue(Files.exists(eventsFile));
        List<String> lines = Files.readAllLines(eventsFile).stream().filter(l -> !l.isBlank()).toList();
        assertEquals(2, lines.size(), "Should have 2 event lines (customers + cases)");

        ObjectMapper om = new ObjectMapper();
        for (String line : lines) {
            assertDoesNotThrow(() -> om.readTree(line), "Each event line must be valid JSON");
        }
    }

    // -- helpers --

    private Customer buildCustomer(Long id, String name, String email, String country, OffsetDateTime updatedAt) {
        return Customer.builder()
                .customerId(id).name(name).email(email).country(country).updatedAt(updatedAt)
                .build();
    }

    private Cases buildCase(Long id, Customer customer, String title, String desc, String status, OffsetDateTime updatedAt) {
        return Cases.builder()
                .caseId(id).customer(customer).title(title).description(desc).status(status).updatedAt(updatedAt)
                .build();
    }
}

