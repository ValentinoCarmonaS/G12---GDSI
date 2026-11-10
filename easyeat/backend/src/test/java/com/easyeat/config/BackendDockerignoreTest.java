package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendDockerignoreTest {

    @Test
    @DisplayName("Should have .dockerignore in backend directory containing expected exclusion patterns")
    void shouldHaveBackendDockerignoreWithRequiredPatterns() throws IOException {
        Path dockerignorePath = Paths.get("easyeat", "backend", ".dockerignore");
        if (!Files.exists(dockerignorePath)) {
            dockerignorePath = Paths.get(".dockerignore");
        }

        assertTrue(Files.exists(dockerignorePath), "easyeat/backend/.dockerignore file must exist");

        List<String> lines = Files.readAllLines(dockerignorePath)
                .stream()
                .map(String::trim)
                .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                .toList();

        assertTrue(lines.contains("target/"), "Must exclude target/");
        assertTrue(lines.contains(".git/"), "Must exclude .git/");
        assertTrue(lines.contains(".idea/"), "Must exclude .idea/");
        assertTrue(lines.contains(".env*"), "Must exclude .env*");
    }
}

