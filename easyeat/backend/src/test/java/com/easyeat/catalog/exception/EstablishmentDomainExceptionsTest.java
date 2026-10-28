package com.easyeat.catalog.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EstablishmentDomainExceptionsTest {

    @Test
    @DisplayName("EstablishmentNotFoundException should retain establishmentId and descriptive message")
    void establishmentNotFoundException() {
        UUID establishmentId = UUID.randomUUID();
        EstablishmentNotFoundException exWithId = new EstablishmentNotFoundException(establishmentId);

        assertThat(exWithId.getEstablishmentId()).isEqualTo(establishmentId);
        assertThat(exWithId.getMessage()).contains(establishmentId.toString());

        EstablishmentNotFoundException exWithMessage = new EstablishmentNotFoundException("Establecimiento inexistente");
        assertThat(exWithMessage.getMessage()).isEqualTo("Establecimiento inexistente");
    }

    @Test
    @DisplayName("EstablishmentInactiveException should retain establishmentId, currentStatus, and descriptive message")
    void establishmentInactiveException() {
        UUID establishmentId = UUID.randomUUID();
        EstablishmentInactiveException exWithStatus = new EstablishmentInactiveException(establishmentId, "PAUSED");

        assertThat(exWithStatus.getEstablishmentId()).isEqualTo(establishmentId);
        assertThat(exWithStatus.getCurrentStatus()).isEqualTo("PAUSED");
        assertThat(exWithStatus.getMessage()).contains(establishmentId.toString());
        assertThat(exWithStatus.getMessage()).contains("PAUSED");

        EstablishmentInactiveException exDisabled = new EstablishmentInactiveException(establishmentId, "DISABLED");
        assertThat(exDisabled.getCurrentStatus()).isEqualTo("DISABLED");

        EstablishmentInactiveException exCustom = new EstablishmentInactiveException("Establecimiento pausado temporalmente");
        assertThat(exCustom.getMessage()).isEqualTo("Establecimiento pausado temporalmente");
    }
}
