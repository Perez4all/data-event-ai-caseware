package com.caseware.util;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
public final class JsonUtil {

    @SneakyThrows
    public static <T> Optional<T> readJson(String path, Class<T> clazz){
        Path targetPath = Path.of(path);
        Path parent = targetPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        log.info(targetPath.toString());
        if (!Files.exists(targetPath)) {
            return Optional.empty();
        }

        try {
            T readValue = new ObjectMapper().readValue(targetPath, clazz);
            return Optional.of(readValue);
        } catch (JacksonException e) {
            log.warn(e.getLocalizedMessage());
            return Optional.empty();
        }

    }
}
