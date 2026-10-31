package com.easyeat.dish.dto;

import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.PublicationGateCondition;

import java.util.List;

/**
 * The publication gate of FR-015, reported as status on a read.
 *
 * <p>Advisory, not binding. FR-015 requires all three conditions to be evaluated again at the
 * instant the approval action is received, and states that no earlier evaluation is binding on the
 * transition — so this is what the Administrator was shown, never what the transition relied on.
 *
 * @param satisfied                true when no condition is unmet. It stays true while
 *                                 acknowledgements are outstanding, because FR-017 makes an
 *                                 acknowledgement an additional precondition and never a gate
 *                                 failure.
 * @param unmetConditions          every unmet condition at once, so the Administrator can fix
 *                                 them in one pass instead of discovering them one at a time.
 * @param requiredAcknowledgements advisories that will need explicit confirmation before the
 *                                 transition is accepted (FR-014, FR-017).
 */
public record PublicationGateStatus(
        boolean satisfied,
        List<PublicationGateCondition> unmetConditions,
        List<AcknowledgementKind> requiredAcknowledgements
) {
}
