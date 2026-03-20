package com.caseware.util;

import com.caseware.dto.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    @TempDir
    Path tempDir;

    private Path jsonFile;

    @BeforeEach
    void setUp() {
        jsonFile = tempDir.resolve("state").resolve("checkpoint.json");
    }

    @Test
    void readJson_returnsEmpty_whenFileDoesNotExist() {
        Optional<Checkpoint> result = JsonUtil.readJson(jsonFile.toString(), Checkpoint.class);
        assertTrue(result.isEmpty());
    }

    @Test
    void readJson_deserializesValidJson() throws IOException {
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, "{\"dateTime\":\"2026-03-20T10:00:00Z\"}");

        Optional<Checkpoint> result = JsonUtil.readJson(jsonFile.toString(), Checkpoint.class);

        assertTrue(result.isPresent());
        assertNotNull(result.get().dateTime());
    }

    @Test
    void readJson_returnsEmpty_whenJsonIsInvalid() throws IOException {
        Files.createDirectories(jsonFile.getParent());
        Files.writeString(jsonFile, "not-valid-json{{{");

        Optional<Checkpoint> result = JsonUtil.readJson(jsonFile.toString(), Checkpoint.class);

        assertTrue(result.isEmpty());
    }
}

