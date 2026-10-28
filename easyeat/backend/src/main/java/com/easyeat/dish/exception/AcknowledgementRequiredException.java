package com.easyeat.dish.exception;

import com.easyeat.dish.domain.AcknowledgementKind;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The operation carries an advisory the Administrator has not acknowledged (FR-014, FR-017).
 *
 * <p>Subordinate to the publication gate by design. FR-017 states the acknowledgement is "an
 * additional precondition, never an override", so this is raised only once
 * {@link PublicationGateException} would not be: a dish that fails the gate stays blocked no
 * matter how many advisories are acknowledged, and the dialog must not be offered as a way past
 * it.
 *
 * <p>Maps to 422 {@code acknowledgement-required}.
 */
public class AcknowledgementRequiredException extends RuntimeException {

    private final UUID dishId;
    private final List<AcknowledgementKind> requiredAcknowledgements;

    public AcknowledgementRequiredException(UUID dishId,
                                            List<AcknowledgementKind> requiredAcknowledgements) {
        super(String.format(
                "El plato con ID %s requiere confirmación explícita antes de continuar: %s.",
                dishId, requiredAcknowledgements));
        if (requiredAcknowledgements == null || requiredAcknowledgements.isEmpty()) {
            throw new IllegalArgumentException(
                    "An acknowledgement request must name at least one advisory: an empty list "
                            + "would mean nothing needed confirming.");
        }
        this.dishId = dishId;
        this.requiredAcknowledgements = List.copyOf(requiredAcknowledgements);
    }

    public UUID getDishId() {
        return dishId;
    }

    public List<AcknowledgementKind> getRequiredAcknowledgements() {
        return Collections.unmodifiableList(requiredAcknowledgements);
    }
}
