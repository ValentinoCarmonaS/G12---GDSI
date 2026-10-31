package com.easyeat.dish.exception;

import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.PublicationGateCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The seven exceptions spec 007 introduces, exercised where their behaviour is more than a
 * message: the branches that choose <em>which</em> message, the guards that refuse a nonsensical
 * construction, and the defensive copies.
 *
 * <p>These cases exist because T031's and T029's commits claim the classes behave this way. A
 * claim in a commit message that no test exercises is just a hope.
 */
@DisplayName("Spec 007 dish exceptions")
class AdminDishExceptionsTest {

    private final UUID dishId = UUID.randomUUID();

    @Nested
    @DisplayName("InvalidRetailPriceException distinguishes its three faults (FR-013)")
    class InvalidRetailPrice {

        @Test
        @DisplayName("an absent price is reported as mandatory, not as invalid")
        void absentPrice() {
            InvalidRetailPriceException ex = new InvalidRetailPriceException(null);

            assertThat(ex.getMessage()).contains("obligatorio");
            assertThat(ex.getSubmittedPrice()).isNull();
        }

        @Test
        @DisplayName("a non-positive price is reported as needing to exceed zero")
        void nonPositivePrice() {
            assertThat(new InvalidRetailPriceException(BigDecimal.ZERO).getMessage())
                    .contains("mayor que cero");
            assertThat(new InvalidRetailPriceException(new BigDecimal("-12.50")).getMessage())
                    .contains("mayor que cero")
                    .contains("-12.50");
        }

        @Test
        @DisplayName("an over-precise price is reported as a decimals problem, which is different")
        void tooManyDecimals() {
            InvalidRetailPriceException ex =
                    new InvalidRetailPriceException(new BigDecimal("11250.001"));

            assertThat(ex.getMessage())
                    .as("'more than two decimals' and 'not positive' are different mistakes and "
                            + "the Administrator fixes them differently")
                    .contains("decimales")
                    .doesNotContain("mayor que cero");
            assertThat(ex.getSubmittedPrice()).isEqualByComparingTo("11250.001");
        }

        @Test
        @DisplayName("a price that is positive and well-scaled still has a fallback message")
        void fallbackMessage() {
            // Reachable when a caller rejects a price for a reason outside this class's three
            // tests. The branch exists so the exception never carries an empty explanation.
            InvalidRetailPriceException ex =
                    new InvalidRetailPriceException(new BigDecimal("11250.00"));

            assertThat(ex.getMessage())
                    .isNotBlank()
                    .contains("11250.00");
        }
    }

    @Nested
    @DisplayName("InvalidRejectionCommentException separates absent from too short (FR-021)")
    class InvalidRejectionComment {

        @Test
        @DisplayName("a null comment yields a null length, which marks a different fault")
        void nullComment() {
            InvalidRejectionCommentException ex = new InvalidRejectionCommentException(null);

            assertThat(ex.getTrimmedLength()).isNull();
            assertThat(ex.getMessage()).contains("No se recibió");
        }

        @Test
        @DisplayName("a whitespace-only comment is treated as absent, since length is post-trim")
        void whitespaceOnly() {
            InvalidRejectionCommentException ex = new InvalidRejectionCommentException("     ");

            assertThat(ex.getTrimmedLength()).isZero();
            assertThat(ex.getMessage()).contains("espacios en blanco");
        }

        @Test
        @DisplayName("a short comment reports the trimmed length, not the raw one")
        void shortComment() {
            InvalidRejectionCommentException ex =
                    new InvalidRejectionCommentException("   corto   ");

            assertThat(ex.getTrimmedLength())
                    .as("the raw string is 11 characters; the one that matters is 5")
                    .isEqualTo(5);
            assertThat(ex.getMessage()).contains("5");
        }

        @Test
        @DisplayName("an over-long comment reports its length too")
        void longComment() {
            assertThat(new InvalidRejectionCommentException("x".repeat(1001)).getTrimmedLength())
                    .isEqualTo(1001);
        }
    }

    @Nested
    @DisplayName("PublicationGateException refuses to report a failure with no cause")
    class PublicationGate {

        @Test
        @DisplayName("an empty condition list is rejected: it would mean the gate passed")
        void emptyConditionsRejected() {
            assertThatThrownBy(() -> new PublicationGateException(dishId, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one");

            assertThatThrownBy(() -> new PublicationGateException(dishId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("carries every condition, and the dish it is about")
        void carriesConditions() {
            PublicationGateException ex = new PublicationGateException(dishId, List.of(
                    PublicationGateCondition.RETAIL_PRICE_MISSING,
                    PublicationGateCondition.EMPTY_RECIPE,
                    PublicationGateCondition.ALLERGENS_UNCONFIRMED));

            assertThat(ex.getDishId()).isEqualTo(dishId);
            assertThat(ex.getUnmetConditions()).containsExactly(
                    PublicationGateCondition.RETAIL_PRICE_MISSING,
                    PublicationGateCondition.EMPTY_RECIPE,
                    PublicationGateCondition.ALLERGENS_UNCONFIRMED);
            assertThat(ex.getMessage()).contains(dishId.toString());
        }

        @Test
        @DisplayName("the condition list cannot be modified after the fact")
        void conditionsAreUnmodifiable() {
            List<PublicationGateCondition> mutable = new ArrayList<>();
            mutable.add(PublicationGateCondition.EMPTY_RECIPE);
            PublicationGateException ex = new PublicationGateException(dishId, mutable);

            mutable.add(PublicationGateCondition.ALLERGENS_UNCONFIRMED);
            assertThat(ex.getUnmetConditions())
                    .as("the reason a request failed must not change under the caller's feet")
                    .containsExactly(PublicationGateCondition.EMPTY_RECIPE);

            assertThatThrownBy(() -> ex.getUnmetConditions()
                    .add(PublicationGateCondition.RETAIL_PRICE_MISSING))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("AcknowledgementRequiredException, same guards (FR-014, FR-017)")
    class AcknowledgementRequired {

        @Test
        @DisplayName("an empty list is rejected: it would mean nothing needed confirming")
        void emptyListRejected() {
            assertThatThrownBy(() -> new AcknowledgementRequiredException(dishId, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one");

            assertThatThrownBy(() -> new AcknowledgementRequiredException(dishId, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("carries both advisory kinds and is unmodifiable")
        void carriesKinds() {
            AcknowledgementRequiredException ex = new AcknowledgementRequiredException(
                    dishId, List.of(AcknowledgementKind.UNPRICED_INGREDIENTS,
                            AcknowledgementKind.NEGATIVE_MARGIN));

            assertThat(ex.getDishId()).isEqualTo(dishId);
            assertThat(ex.getRequiredAcknowledgements()).containsExactly(
                    AcknowledgementKind.UNPRICED_INGREDIENTS,
                    AcknowledgementKind.NEGATIVE_MARGIN);
            assertThatThrownBy(() -> ex.getRequiredAcknowledgements()
                    .add(AcknowledgementKind.NEGATIVE_MARGIN))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("the three conflict exceptions carry what the handler returns")
    class Conflicts {

        @Test
        @DisplayName("DishStateConflictException names both the actual and the required state")
        void stateConflict() {
            DishStateConflictException ex =
                    new DishStateConflictException(dishId, "DRAFT", "PENDING_PRICE");

            assertThat(ex.getDishId()).isEqualTo(dishId);
            assertThat(ex.getCurrentStatus()).isEqualTo("DRAFT");
            assertThat(ex.getRequiredStatus()).isEqualTo("PENDING_PRICE");
            assertThat(ex.getMessage()).contains("DRAFT", "PENDING_PRICE");
        }

        @Test
        @DisplayName("DishAlreadyDecidedException carries the instant of the winning decision")
        void alreadyDecided() {
            Instant decidedAt = Instant.parse("2026-10-28T12:00:00Z");
            DishAlreadyDecidedException ex =
                    new DishAlreadyDecidedException(dishId, "PUBLISHED", decidedAt);

            assertThat(ex.getDishId()).isEqualTo(dishId);
            assertThat(ex.getCurrentStatus()).isEqualTo("PUBLISHED");
            assertThat(ex.getDecidedAt()).isEqualTo(decidedAt);
        }

        @Test
        @DisplayName("StaleDishPriceException renders an absent price as words, not as 'null'")
        void stalePriceDescribesNull() {
            StaleDishPriceException ex =
                    new StaleDishPriceException(dishId, null, new BigDecimal("11250.00"));

            assertThat(ex.getMessage())
                    .as("the Administrator should not be shown the word null")
                    .contains("sin precio")
                    .doesNotContain("null");
            assertThat(ex.getExpectedRetailPrice()).isNull();
            assertThat(ex.getCurrentRetailPrice()).isEqualByComparingTo("11250.00");
            assertThat(ex.getDishId()).isEqualTo(dishId);
        }

        @Test
        @DisplayName("StaleDishPriceException renders both prices when both exist")
        void stalePriceWithBothPrices() {
            StaleDishPriceException ex = new StaleDishPriceException(
                    dishId, new BigDecimal("10800.00"), new BigDecimal("11250.00"));

            assertThat(ex.getMessage()).contains("10800.00", "11250.00");
        }
    }
}
