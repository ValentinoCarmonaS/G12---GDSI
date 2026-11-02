package com.easyeat.operations.dto;

import jakarta.validation.constraints.NotNull;

/** {@code expectedVersion} is null while the method has no row yet. */
public record TogglePaymentMethodRequest(
        @NotNull(message = "isEnabled is required")
        Boolean isEnabled,

        Long expectedVersion
) {
}
