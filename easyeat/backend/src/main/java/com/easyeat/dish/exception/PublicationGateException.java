package com.easyeat.dish.exception;

import com.easyeat.dish.domain.PublicationGateCondition;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The publication gate of FR-015 blocked the transition.
 *
 * <p>Carries <b>every</b> unmet condition rather than the first one found, because an
 * Administrator who fixes one condition and is then told about a second has been made to work
 * twice for information the system already had.
 *
 * <p>This is never raised to request an acknowledgement — that is
 * {@link AcknowledgementRequiredException}. FR-017 makes the distinction load-bearing: an
 * acknowledgement is an additional precondition, never an override, so no number of
 * acknowledgements can get a dish past this exception.
 *
 * <p>Maps to 422 {@code publication-gate-failed}.
 */
public class PublicationGateException extends RuntimeException {

    private final UUID dishId;
    private final List<PublicationGateCondition> unmetConditions;

    public PublicationGateException(UUID dishId, List<PublicationGateCondition> unmetConditions) {
        super(String.format(
                "El plato con ID %s no cumple la compuerta de publicación. Condiciones no "
                        + "satisfechas: %s.",
                dishId, unmetConditions));
        if (unmetConditions == null || unmetConditions.isEmpty()) {
            throw new IllegalArgumentException(
                    "A publication gate failure must name at least one unmet condition: an empty "
                            + "list would mean the gate passed.");
        }
        this.dishId = dishId;
        this.unmetConditions = List.copyOf(unmetConditions);
    }

    public UUID getDishId() {
        return dishId;
    }

    /** Unmodifiable: the reason a request failed is not something a handler should edit. */
    public List<PublicationGateCondition> getUnmetConditions() {
        return Collections.unmodifiableList(unmetConditions);
    }
}
