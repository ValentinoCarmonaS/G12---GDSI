package com.easyeat.profile.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.profile.exception.CooldownRateLimitException;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.exception.EmailVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerProfileTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/profile/test");
    }

    @Test
    @DisplayName("Should map EmailVerificationException to 400 Bad Request ProblemDetail")
    void handleEmailVerificationException() {
        EmailVerificationException ex = new EmailVerificationException("Token expired");
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleEmailVerification(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Email Verification Token");
        assertThat(response.getBody().getDetail()).isEqualTo("Token expired");
    }

    @Test
    @DisplayName("Should map CooldownRateLimitException to 429 Too Many Requests with Retry-After header")
    void handleCooldownRateLimitException() {
        CooldownRateLimitException ex = new CooldownRateLimitException(45);
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleCooldownRateLimit(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("45");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Email Verification Cooldown Active");
        assertThat(response.getBody().getProperties()).containsEntry("retryAfterSeconds", 45L);
    }

    @Test
    @DisplayName("Should map DietaryTaxonomyException to 400 Bad Request ProblemDetail")
    void handleDietaryTaxonomyException() {
        DietaryTaxonomyException ex = DietaryTaxonomyException.forInvalidCode("UNKNOWN");
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleDietaryTaxonomy(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Invalid Dietary Taxonomy");
        assertThat(response.getBody().getDetail()).contains("UNKNOWN");
    }

    @Test
    @DisplayName("Should map DishNotFoundException to 404 Not Found ProblemDetail")
    void handleDishNotFoundException() {
        UUID dishId = UUID.randomUUID();
        DishNotFoundException ex = new DishNotFoundException(dishId);
        ResponseEntity<ProblemDetail> response = exceptionHandler.handleDishNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Dish Not Found");
        assertThat(response.getBody().getDetail()).contains(dishId.toString());
    }

    @Test
    @DisplayName("Should map MaxUploadSizeExceededException to 400 Bad Request ProblemDetail")
    void handleMaxUploadSizeExceededException() {
        org.springframework.web.multipart.MaxUploadSizeExceededException ex =
                new org.springframework.web.multipart.MaxUploadSizeExceededException(10485760);
        org.springframework.web.context.request.ServletWebRequest webRequest =
                new org.springframework.web.context.request.ServletWebRequest(request);
        ResponseEntity<Object> response = exceptionHandler.handleMaxUploadSizeExceededException(
                ex, new org.springframework.http.HttpHeaders(), HttpStatus.BAD_REQUEST, webRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body.getTitle()).isEqualTo("Maximum Upload Size Exceeded");
        assertThat(body.getDetail()).isEqualTo("El archivo supera el tamaño máximo permitido de 5 MB.");
    }
}
