package com.caseware.controller;

import com.caseware.dto.IngestionManifest;
import com.caseware.service.IngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngestionController.class)
class IngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngestionService ingestionService;

    @Test
    void ingest_dryRun_returnsManifest() throws Exception {
        IngestionManifest manifest = buildManifest(30, 200);
        when(ingestionService.process(true)).thenReturn(manifest);

        mockMvc.perform(post("/ingest").param("dry_run", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("test-run"))
                .andExpect(jsonPath("$.customers.deltaRowCount").value(30))
                .andExpect(jsonPath("$.cases.deltaRowCount").value(200));
    }

    @Test
    void ingest_realRun_returnsManifest() throws Exception {
        IngestionManifest manifest = buildManifest(5, 10);
        when(ingestionService.process(false)).thenReturn(manifest);

        mockMvc.perform(post("/ingest").param("dry_run", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value("test-run"))
                .andExpect(jsonPath("$.customers.deltaRowCount").value(5))
                .andExpect(jsonPath("$.cases.deltaRowCount").value(10));
    }

    @Test
    void ingest_missingDryRunParam_returns400() throws Exception {
        mockMvc.perform(post("/ingest"))
                .andExpect(status().isBadRequest());
    }

    private IngestionManifest buildManifest(long customerCount, long caseCount) {
        OffsetDateTime now = OffsetDateTime.now();
        return new IngestionManifest(
                "test-run", now, now,
                new IngestionManifest.TableManifest(customerCount, List.of(), "fp-customers"),
                new IngestionManifest.TableManifest(caseCount, List.of(), "fp-cases"),
                now, now
        );
    }
}

