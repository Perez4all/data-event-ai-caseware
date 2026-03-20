package com.caseware.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record IngestionManifest(
        String runId,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        TableManifest customers,
        TableManifest cases,
        OffsetDateTime checkpointBefore,
        OffsetDateTime checkpointAfter
) {
    public record TableManifest(
            long deltaRowCount,
            List<String> lakePaths,
            String schemaFingerprint
    ) {}
}
