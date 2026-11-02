package com.easyeat.operations.dto;

import java.util.Objects;

/**
 * {@code expectedVersion} is null while the method has no row yet. A missing {@code isEnabled} is
 * rejected by the constructor, which the JSON reader turns into a 400 as the contract documents;
 * Bean Validation would answer 422 through the global handler instead.
 */
public record TogglePaymentMethodRequest(Boolean isEnabled, Long expectedVersion) {

    public TogglePaymentMethodRequest {
        Objects.requireNonNull(isEnabled, "isEnabled is required");
    }
}
