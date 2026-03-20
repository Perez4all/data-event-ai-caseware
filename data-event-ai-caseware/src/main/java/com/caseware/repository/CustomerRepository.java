package com.caseware.repository;

import com.caseware.model.Cases;
import com.caseware.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Long countByUpdatedAtGreaterThan(OffsetDateTime offsetDateTime);

    List<Customer> findByUpdatedAtGreaterThan(OffsetDateTime offsetDateTime);

}
