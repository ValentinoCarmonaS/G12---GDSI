package com.easyeat.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 028 T005: verifies HTTP GZIP compression (>= 1024 bytes for application/json
 * and application/problem+json) and CORS Access-Control-Expose-Headers: ETag.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(HttpTransportOptimizationTest.TransportProbeController.class)
@DisplayName("Spec 028 - HTTP GZIP & CORS ETag exposure (T005)")
class HttpTransportOptimizationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @TestConfiguration
    @RestController
    @RequestMapping("/api/v1/establishments/test-transport")
    static class TransportProbeController {

        @GetMapping(value = "/dishes", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<String> largeJson() {
            String padding = "x".repeat(1500);
            return ResponseEntity.ok()
                    .header(HttpHeaders.ETAG, "\"etag-probe-1\"")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"payload\":\"" + padding + "\"}");
        }

        @GetMapping(value = "/dishes/problem", produces = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        public ResponseEntity<String> largeProblemJson() {
            String padding = "y".repeat(1500);
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body("{\"title\":\"Bad Request\",\"detail\":\"" + padding + "\"}");
        }

        @GetMapping(value = "/dishes/small", produces = MediaType.APPLICATION_JSON_VALUE)
        public ResponseEntity<String> smallJson() {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"ok\":true}");
        }
    }

    @Test
    @DisplayName("compresses application/json responses >= 1024 bytes with gzip")
    void shouldCompressLargeApplicationJson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/establishments/test-transport/dishes"))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING))
                .contains("gzip");
    }

    @Test
    @DisplayName("compresses application/problem+json responses >= 1024 bytes with gzip")
    void shouldCompressLargeProblemJson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/establishments/test-transport/dishes/problem"))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING))
                .contains("gzip");
    }

    @Test
    @DisplayName("does not compress application/json responses smaller than 1024 bytes")
    void shouldNotCompressSmallJson() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/establishments/test-transport/dishes/small"))
                .header(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_ENCODING)).isEmpty();
    }

    @Test
    @DisplayName("exposes ETag header in CORS responses for allowed origin")
    void shouldExposeEtagHeaderInCorsResponse() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/establishments/test-transport/dishes"))
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .contains("http://localhost:3000");
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .isPresent()
                .get()
                .asString()
                .contains("ETag");
    }
}
