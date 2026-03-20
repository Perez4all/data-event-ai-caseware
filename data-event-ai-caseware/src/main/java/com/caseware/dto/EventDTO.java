package com.caseware.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record EventDTO(
        String runId,
        String schemaFingerprint,
        long deltaRowCount,
        List<String> lakePaths,
        OffsetDateTime checkpointAfter
) {
}

