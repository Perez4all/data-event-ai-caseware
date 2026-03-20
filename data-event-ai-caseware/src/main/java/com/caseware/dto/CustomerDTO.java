package com.caseware.dto;

import com.caseware.model.Customer;
import jakarta.annotation.Nonnull;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

public record CustomerDTO(
        Long customerId,
        String name,
        String email,
        String country,
        OffsetDateTime updatedAt
) {
    @Nonnull
    @Override
    public String toString() {
       return new ObjectMapper().writeValueAsString(this);
    }
    public static CustomerDTO from(Customer entity) {
        return new CustomerDTO(
                entity.getCustomerId(),
                entity.getName(),
                entity.getEmail(),
                entity.getCountry(),
                entity.getUpdatedAt()
        );
    }
}

