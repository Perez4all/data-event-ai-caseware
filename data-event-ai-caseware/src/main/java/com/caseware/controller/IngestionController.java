package com.caseware.controller;

import com.caseware.dto.IngestionManifest;
import com.caseware.service.IngestionService;
import jakarta.annotation.Nonnull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService){
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<IngestionManifest> ingest(@Nonnull @RequestParam(name = "dry_run") Boolean dryRun){
        return ResponseEntity.ok(ingestionService.process(dryRun));
    }

}
