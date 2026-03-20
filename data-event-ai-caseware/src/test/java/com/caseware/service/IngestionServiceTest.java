package com.caseware.service;

import com.caseware.dto.Checkpoint;
import com.caseware.dto.IngestionManifest;
import com.caseware.client.SearchClient;
import com.caseware.model.Cases;
import com.caseware.model.Customer;
import com.caseware.repository.CaseRepository;
import com.caseware.repository.CustomerRepository;
import com.caseware.service.handler.LakeHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    private LakeHandler lakeHandler;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private SearchClient searchClient;

    @InjectMocks
    private IngestionService ingestionService;

    private static final OffsetDateTime EPOCH = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    // -- dry run --

    @Test
    void process_dryRun_returnsCounts_withoutWriting() {
        Checkpoint cp = new Checkpoint(EPOCH);
        when(lakeHandler.readCheckPoint()).thenReturn(cp);
        when(customerRepository.countByUpdatedAtGreaterThan(any())).thenReturn(30L);
        when(caseRepository.countByUpdatedAtGreaterThan(any())).thenReturn(200L);

        IngestionManifest manifest = ingestionService.process(true);

        assertNotNull(manifest);
        assertEquals(30L, manifest.customers().deltaRowCount());
        assertEquals(200L, manifest.cases().deltaRowCount());
        assertTrue(manifest.customers().lakePaths().isEmpty(), "Dry run must not write lake files");
        assertTrue(manifest.cases().lakePaths().isEmpty(), "Dry run must not write lake files");

        verify(lakeHandler, never()).writeLakeDeltaRows(any(), any());
        verify(lakeHandler, never()).writeCheckPoint();
        verify(lakeHandler, never()).writeEvents(any());
    }

    // -- real run with data --

    @Test
    void process_realRun_writesLakeAndAdvancesCheckpoint() {
        Checkpoint oldCp = new Checkpoint(EPOCH);
        OffsetDateTime newCpTime = OffsetDateTime.now(ZoneOffset.UTC);
        Checkpoint newCp = new Checkpoint(newCpTime);

        Customer c1 = Customer.builder().customerId(1L).name("A").email("a@t.com").country("US")
                .updatedAt(OffsetDateTime.now()).build();
        Cases case1 = Cases.builder().caseId(1L).customer(c1).title("T").description("D").status("open")
                .updatedAt(OffsetDateTime.now()).build();

        IngestionManifest.TableManifest custManifest = new IngestionManifest.TableManifest(1, List.of("/lake/customers/2026-03-20/data.jsonl"), "fp1");
        IngestionManifest.TableManifest caseManifest = new IngestionManifest.TableManifest(1, List.of("/lake/cases/2026-03-20/data.jsonl"), "fp2");

        when(lakeHandler.readCheckPoint()).thenReturn(oldCp);
        when(customerRepository.findByUpdatedAtGreaterThan(EPOCH)).thenReturn(List.of(c1));
        when(caseRepository.findByUpdatedAtGreaterThan(EPOCH)).thenReturn(List.of(case1));
        when(lakeHandler.writeLakeDeltaRows(List.of(c1), List.of(case1)))
                .thenReturn(new IngestionManifest.TableManifest[]{custManifest, caseManifest});
        when(lakeHandler.writeCheckPoint()).thenReturn(newCp);

        IngestionManifest manifest = ingestionService.process(false);

        assertNotNull(manifest);
        assertEquals(1, manifest.customers().deltaRowCount());
        assertEquals(1, manifest.cases().deltaRowCount());
        assertEquals(EPOCH, manifest.checkpointBefore());
        assertEquals(newCpTime, manifest.checkpointAfter());

        verify(lakeHandler).writeLakeDeltaRows(List.of(c1), List.of(case1));
        verify(lakeHandler).writeCheckPoint();
        verify(lakeHandler).writeEvents(any(IngestionManifest.class));
    }

    // -- real run with no changes --

    @Test
    void process_realRun_noChanges_writesEmptyManifest() {
        Checkpoint cp = new Checkpoint(OffsetDateTime.now(ZoneOffset.UTC));
        IngestionManifest.TableManifest empty = new IngestionManifest.TableManifest(0, List.of(), "fp");

        when(lakeHandler.readCheckPoint()).thenReturn(cp);
        when(customerRepository.findByUpdatedAtGreaterThan(cp.dateTime())).thenReturn(List.of());
        when(caseRepository.findByUpdatedAtGreaterThan(cp.dateTime())).thenReturn(List.of());
        when(lakeHandler.writeLakeDeltaRows(List.of(), List.of()))
                .thenReturn(new IngestionManifest.TableManifest[]{empty, empty});
        when(lakeHandler.writeCheckPoint()).thenReturn(cp);

        IngestionManifest manifest = ingestionService.process(false);

        assertEquals(0, manifest.customers().deltaRowCount());
        assertEquals(0, manifest.cases().deltaRowCount());
    }

    // -- checkpoint not advanced on failure --

    @Test
    void process_realRun_checkpointNotAdvanced_whenLakeWriteFails() {
        Checkpoint cp = new Checkpoint(EPOCH);
        Customer c1 = Customer.builder().customerId(1L).name("A").email("a@t.com").country("US")
                .updatedAt(OffsetDateTime.now()).build();

        when(lakeHandler.readCheckPoint()).thenReturn(cp);
        when(customerRepository.findByUpdatedAtGreaterThan(EPOCH)).thenReturn(List.of(c1));
        when(caseRepository.findByUpdatedAtGreaterThan(EPOCH)).thenReturn(List.of());
        when(lakeHandler.writeLakeDeltaRows(any(), any()))
                .thenThrow(new RuntimeException("disk full"));

        assertThrows(RuntimeException.class, () -> ingestionService.process(false));

        verify(lakeHandler, never()).writeCheckPoint();
        verify(lakeHandler, never()).writeEvents(any());
    }
}

