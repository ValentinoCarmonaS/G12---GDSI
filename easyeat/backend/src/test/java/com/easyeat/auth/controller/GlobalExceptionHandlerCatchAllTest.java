package com.easyeat.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GlobalExceptionHandlerCatchAllTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("An unexpected error answers 500 as a ProblemDetail without leaking the exception message")
    void unexpectedErrorAnswers500() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/operating-schedules");

        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new RuntimeException("constraint xyz violated"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getTitle()).isEqualTo("Internal Server Error");
        assertThat(body.getDetail()).doesNotContain("constraint xyz");
        assertThat(body.getInstance()).hasToString("/api/v1/admin/operating-schedules");
    }

    @Test
    @DisplayName("Authentication errors are not swallowed by the catch-all handler")
    void authenticationErrorsAreRethrown() {
        BadCredentialsException ex = new BadCredentialsException("bad");

        assertThatThrownBy(() -> handler.handleUnexpected(ex, new MockHttpServletRequest()))
                .isSameAs(ex);
    }
}
