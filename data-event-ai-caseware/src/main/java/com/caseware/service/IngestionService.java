package com.caseware.service;

import com.caseware.dto.Checkpoint;
import com.caseware.dto.IngestionManifest;
import com.caseware.dto.IngestionStatus;
import com.caseware.model.Cases;
import com.caseware.model.Customer;
import com.caseware.repository.CaseRepository;
import com.caseware.repository.CustomerRepository;
import com.caseware.service.handler.LakeHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;


import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.UUID;


@Service
public class IngestionService {

    @Autowired
    private LakeHandler lakeHandler;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Value("${caseware.schema.fingerprint.cases}")
    private String fingerprintCases;

    @Value("${caseware.schema.fingerprint.customers}")
    private String fingerprintCustomers;


    public IngestionManifest process(Boolean dryRun){

        final OffsetDateTime started = OffsetDateTime.now();
        final String runId = UUID.randomUUID().toString();
        Checkpoint checkpoint = lakeHandler.readCheckPoint();

        if(dryRun){

            OffsetDateTime minDateTime = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);
            Long caseCount = caseRepository.countByUpdatedAtGreaterThan(minDateTime);
            Long customerCount = customerRepository.countByUpdatedAtGreaterThan(minDateTime);

            return new IngestionManifest(runId, started, OffsetDateTime.now(),
                    new IngestionManifest.TableManifest(customerCount, List.of(), fingerprintCustomers),
                    new IngestionManifest.TableManifest(caseCount, List.of(), fingerprintCases),
                    checkpoint.dateTime(), checkpoint.dateTime()
            );

        } else {

            List<Cases> casesByUpdateAtGreater = caseRepository.findByUpdatedAtGreaterThan(checkpoint.dateTime());
            List<Customer> customersByUpdatedAtGreater = customerRepository.findByUpdatedAtGreaterThan(checkpoint.dateTime());

            //Write Data Lakes
            IngestionManifest.TableManifest[] tableManifests = lakeHandler.writeLakeDeltaRows(customersByUpdatedAtGreater, casesByUpdateAtGreater);

            IngestionManifest ingestionManifest = new IngestionManifest(runId, started, OffsetDateTime.now(), tableManifests[0],
                    tableManifests[1], checkpoint.dateTime(), lakeHandler.writeCheckPoint().dateTime());

            //Write events before return
            lakeHandler.writeEvents(ingestionManifest);

            return ingestionManifest;

        }

    }
}
