package com.caseware.service.handler;

import com.caseware.dto.CasesDTO;
import com.caseware.dto.Checkpoint;
import com.caseware.dto.CustomerDTO;
import com.caseware.dto.EventDTO;
import com.caseware.dto.IngestionManifest;
import com.caseware.exception.FingerprintSchemaNotValidException;
import com.caseware.model.Cases;
import com.caseware.model.Customer;
import com.caseware.util.HashUtil;
import com.caseware.util.JsonUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class LakeHandler {

    @Value("${caseware.path.checkpoint}")
    private String checkPointPath;

    @Value("${caseware.path.cases-lake}")
    private String caseLakePath;

    @Value("${caseware.path.customers-lake}")
    private String customerLakePath;

    @Value("${caseware.path.lakeOutputFilename}")
    private String lakeOutputFilename;

    @Value("${caseware.schema.fingerprint.cases}")
    private String fingerprintCases;

    @Value("${caseware.schema.fingerprint.customers}")
    private String fingerprintCustomers;

    @Value("${caseware.path.events-lake}")
    private String eventsPath;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void initialize(){
        String casesHash = HashUtil.schemaFingerprint(Cases.class);
        String customerHash = HashUtil.schemaFingerprint(Customer.class);
        log.info(String.format("CASES SCHEMA FINGERPRINT %s <--> %s", casesHash, fingerprintCases));
        log.info(String.format("CUSTOMERS SCHEMA FINGERPRINT %s <--> %s", customerHash, fingerprintCustomers));

        if(!casesHash.equalsIgnoreCase(fingerprintCases)){
            throw new FingerprintSchemaNotValidException("CASES SCHEMA FINGERPRINT DOES NOT MATCH, PLEASE FIX TO PROVIDE DATA INTEGRITY");
        }

        if(!customerHash.equalsIgnoreCase(fingerprintCustomers)){
            throw new FingerprintSchemaNotValidException("CUSTOMER SCHEMA FINGERPRINT DOES NOT MATCH, PLEASE FIX TO PROVIDE DATA INTEGRITY");
        }
    }

    public Checkpoint readCheckPoint(){
        Optional<Checkpoint> checkpoint = JsonUtil.readJson(checkPointPath, Checkpoint.class);
        return checkpoint.orElse(new Checkpoint(OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC)));
    }

    public Checkpoint writeCheckPoint(){
        Checkpoint checkpoint = new Checkpoint(OffsetDateTime.now());
        try {
            Files.writeString(Path.of(checkPointPath), objectMapper.writeValueAsString(checkpoint));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return checkpoint;
    }

    public IngestionManifest.TableManifest[] writeLakeDeltaRows(List<Customer> customers, List<Cases> cases) {


        String customerOutputPath = customerLakePath + "/%s/" +  lakeOutputFilename;
        Map<String, String> customersByDate = customers.stream().collect(Collectors
                .groupingBy(customer -> String.format(customer.getUpdatedAt().toLocalDate().toString(), customerOutputPath),
                        LinkedHashMap::new, Collectors.mapping(c -> CustomerDTO.from(c).toString(),
                                Collectors.joining("\n", "", "\n"))));

        String casesOutputPath = caseLakePath + "/%s/" +  lakeOutputFilename;
        Map<String, String> casesByDate = cases.stream().collect(Collectors
                .groupingBy(cs -> String.format(cs.getUpdatedAt().toLocalDate().toString(), casesOutputPath),
                        LinkedHashMap::new, Collectors.mapping(cs -> CasesDTO.from(cs).toString(),
                                Collectors.joining("\n", "", "\n"))));

        IngestionManifest.TableManifest customerTableManifest = writeToDataLake(customersByDate, fingerprintCustomers);
        IngestionManifest.TableManifest casesTableManifest = writeToDataLake(casesByDate, fingerprintCases);

        return new IngestionManifest.TableManifest[]{customerTableManifest, casesTableManifest};

    }


    @Async
    public void writeEvents(IngestionManifest ingestionManifest){

        IngestionManifest.TableManifest customersManifest = ingestionManifest.customers();
        EventDTO eventCustomersDTO = new EventDTO(ingestionManifest.runId(), customersManifest.schemaFingerprint(), customersManifest.deltaRowCount(),
                customersManifest.lakePaths(), ingestionManifest.checkpointAfter());

        IngestionManifest.TableManifest casesManifest = ingestionManifest.customers();
        EventDTO eventCasesDTO = new EventDTO(ingestionManifest.runId(), casesManifest.schemaFingerprint(), casesManifest.deltaRowCount(),
                casesManifest.lakePaths(), ingestionManifest.checkpointAfter());
        try {

            Path eventsRoute = Path.of(eventsPath);
            String customerEventData = objectMapper.writeValueAsString(eventCustomersDTO);
            String casesEventData = objectMapper.writeValueAsString(eventCasesDTO);

            log.info("--- NEW CUSTOMER DATA LAKE WRITTEN ---");
            log.info(customerEventData);

            log.info("--- NEW CASES DATA LAKE WRITTEN ---");
            log.info(casesEventData);

            Files.writeString(eventsRoute, customerEventData, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(eventsRoute, casesEventData, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private IngestionManifest.TableManifest writeToDataLake(Map<String, String> lakeJsonsByDatePath, String schemaFingerprint){
        lakeJsonsByDatePath.forEach((datePath, jsonData) -> {
            try {
                Files.writeString(Path.of(datePath), jsonData);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        return new IngestionManifest.TableManifest(lakeJsonsByDatePath.size(), lakeJsonsByDatePath.keySet().stream().
                toList(), schemaFingerprint);
    }


}
