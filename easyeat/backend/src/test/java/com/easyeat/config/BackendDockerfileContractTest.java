package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("T008 / T009: Contrato estático y estructural de Dockerfile Backend (HU-01)")
class BackendDockerfileContractTest {

    private final File dockerfile = new File("Dockerfile");

    @Test
    @DisplayName("Debe existir el archivo Dockerfile en el directorio raíz del backend")
    void dockerfileExists() {
        assertThat(dockerfile)
                .withFailMessage("El archivo Dockerfile debe existir en easyeat/backend/Dockerfile")
                .exists()
                .isFile();
    }

    @Test
    @DisplayName("Dockerfile debe definir etapa de build con maven:3.9.9-eclipse-temurin-21-alpine")
    void dockerfileBuildStage() throws IOException {
        assertThat(dockerfile).exists();
        List<String> lines = Files.readAllLines(dockerfile.toPath());

        boolean hasBuildStage = lines.stream().anyMatch(line ->
                line.matches("(?i)^FROM\\s+maven:3\\.9\\.9-eclipse-temurin-21-alpine.*"));
        assertThat(hasBuildStage)
                .withFailMessage("Dockerfile debe usar maven:3.9.9-eclipse-temurin-21-alpine en la etapa de compilación")
                .isTrue();

        boolean hasPackageCommand = lines.stream().anyMatch(line ->
                line.contains("mvn") && line.contains("package") && line.contains("-DskipTests"));
        assertThat(hasPackageCommand)
                .withFailMessage("Dockerfile debe compilar con mvn package omitiendo tests (-DskipTests)")
                .isTrue();
    }

    @Test
    @DisplayName("Dockerfile debe definir etapa de runtime con JRE Alpine, usuario easyeat y flags JVM de memoria")
    void dockerfileRuntimeStage() throws IOException {
        assertThat(dockerfile).exists();
        List<String> lines = Files.readAllLines(dockerfile.toPath());

        boolean hasRuntimeStage = lines.stream().anyMatch(line ->
                line.matches("(?i)^FROM\\s+eclipse-temurin:21-jre-alpine.*"));
        assertThat(hasRuntimeStage)
                .withFailMessage("Dockerfile debe usar eclipse-temurin:21-jre-alpine en la etapa de runtime")
                .isTrue();

        boolean createsUser = lines.stream().anyMatch(line ->
                line.contains("adduser") && line.contains("easyeat"));
        assertThat(createsUser)
                .withFailMessage("Dockerfile debe crear un usuario no privilegiado 'easyeat'")
                .isTrue();

        boolean switchesUser = lines.stream().anyMatch(line ->
                line.matches("(?i)^USER\\s+easyeat"));
        assertThat(switchesUser)
                .withFailMessage("Dockerfile debe conmutar la identidad de ejecución a 'USER easyeat'")
                .isTrue();

        boolean copiesJar = lines.stream().anyMatch(line ->
                line.contains("COPY") && line.contains("/app/app.jar"));
        assertThat(copiesJar)
                .withFailMessage("Dockerfile debe copiar el artefacto empaquetado como /app/app.jar")
                .isTrue();

        boolean hasJvmFlags = lines.stream().anyMatch(line ->
                line.contains("-XX:+UseContainerSupport") && line.contains("-XX:MaxRAMPercentage=75.0"));
        assertThat(hasJvmFlags)
                .withFailMessage("Dockerfile ENTRYPOINT debe incluir -XX:+UseContainerSupport y -XX:MaxRAMPercentage=75.0")
                .isTrue();
    }
}

