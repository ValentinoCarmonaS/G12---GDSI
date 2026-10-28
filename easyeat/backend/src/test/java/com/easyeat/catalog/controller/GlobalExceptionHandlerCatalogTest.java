package com.easyeat.catalog.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.exception.InvalidSearchQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerCatalogTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/establishments/test");
    }

    @Test
    @DisplayName("Should map EstablishmentNotFoundException to 404 NOT_FOUND ProblemDetail")
    void handleEstablishmentNotFoundException() {
        UUID establishmentId = UUID.randomUUID();
        EstablishmentNotFoundException ex = new EstablishmentNotFoundException(establishmentId);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleEstablishmentNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/establishment-not-found"));
        assertThat(body.getTitle()).isEqualTo("Establishment Not Found");
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getDetail()).contains(establishmentId.toString());
        assertThat(body.getInstance()).isEqualTo(URI.create("/api/v1/establishments/test"));
        assertThat(body.getProperties()).containsEntry("establishmentId", establishmentId.toString());
    }

    @Test
    @DisplayName("Should map EstablishmentInactiveException to 403 FORBIDDEN ProblemDetail with status details")
    void handleEstablishmentInactiveException() {
        UUID establishmentId = UUID.randomUUID();
        EstablishmentInactiveException ex = new EstablishmentInactiveException(establishmentId, "PAUSED");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleEstablishmentInactive(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/establishment-inactive"));
        assertThat(body.getTitle()).isEqualTo("Establishment Inactive");
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getDetail()).contains("PAUSED");
        assertThat(body.getInstance()).isEqualTo(URI.create("/api/v1/establishments/test"));
        assertThat(body.getProperties()).containsEntry("establishmentId", establishmentId.toString());
        assertThat(body.getProperties()).containsEntry("currentStatus", "PAUSED");
    }

    @Test
    @DisplayName("Should map InvalidSearchQueryException to 400 BAD_REQUEST ProblemDetail")
    void handleInvalidSearchQueryException() {
        InvalidSearchQueryException ex = new InvalidSearchQueryException("Search query must contain at least 3 non-whitespace characters.");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleInvalidSearchQuery(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getType()).isEqualTo(URI.create("https://easyeat.com/errors/invalid-search-query"));
        assertThat(body.getTitle()).isEqualTo("Invalid Search Query");
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getDetail()).contains("at least 3");
        assertThat(body.getInstance()).isEqualTo(URI.create("/api/v1/establishments/test"));
    }
}
