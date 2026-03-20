package com.caseware.dto;

import com.caseware.model.Cases;
import jakarta.annotation.Nonnull;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

public record CasesDTO(
        Long caseId,
        Long customerId,
        String title,
        String description,
        String status,
        OffsetDateTime updatedAt
) {
    @Nonnull
    @Override
    public String toString() {
        return new ObjectMapper().writeValueAsString(this);
    }
    public static CasesDTO from(Cases entity) {
        return new CasesDTO(
                entity.getCaseId(),
                entity.getCustomer().getCustomerId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getUpdatedAt()
        );
    }
}

