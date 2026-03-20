package com.caseware.repository;

import com.caseware.model.Cases;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface CaseRepository extends JpaRepository<Cases, Long> {

    Long countByUpdatedAtGreaterThan(OffsetDateTime offsetDateTime);

    List<Cases> findByUpdatedAtGreaterThan(OffsetDateTime offsetDateTime);

}
