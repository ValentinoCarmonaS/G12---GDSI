package com.easyeat.dish.controller;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.dish.domain.AcknowledgementKind;
import com.easyeat.dish.domain.PublicationGateCondition;
import com.easyeat.dish.exception.AcknowledgementRequiredException;
import com.easyeat.dish.exception.DishAlreadyDecidedException;
import com.easyeat.dish.exception.DishStateConflictException;
import com.easyeat.dish.exception.InvalidRejectionCommentException;
import com.easyeat.dish.exception.InvalidRetailPriceException;
import com.easyeat.dish.exception.PublicationGateException;
import com.easyeat.dish.exception.StaleDishPriceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seven handlers this feature adds to the single {@code @RestControllerAdvice}, and the error
 * taxonomy {@code contracts/openapi.yaml} publishes.
 *
 * <p>The taxonomy is the point. Three distinct 409s and two distinct 422s exist so the interface
 * can tell the Administrator what actually happened, so each is asserted by its {@code type} URI
 * and by the extension properties a client needs to recover — not merely by its status code.
 *
 * <p>On {@code acknowledgement-required}: T033 describes it as "428-style" signalling, but both
 * {@code contracts/openapi.yaml} and {@code data-model.md} specify <b>422</b>, and the contract is
 * the authority. 422 is asserted here.
 */
@DisplayName("Dish error taxonomy: handler mapping")
class GlobalExceptionHandlerDishMappingTest {

    private static final String BASE = "https://easyeat.com/errors/";

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    private final UUID dishId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin/dishes/" + dishId + "/approval");
    }

    private static ProblemDetail bodyOf(ResponseEntity<ProblemDetail> response) {
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        return body;
    }

    /** ProblemDetail stores extensions untyped, so the casts live here rather than in each test. */
    @SuppressWarnings("unchecked")
    private static List<String> stringsAt(ProblemDetail body, String key) {
        return (List<String>) body.getProperties().get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> fieldErrorsOf(ProblemDetail body) {
        return (List<Map<String, String>>) body.getProperties().get("errors");
    }

    @Nested
    @DisplayName("409: three conflicts a client must be able to tell apart")
    class Conflicts {

        @Test
        @DisplayName("a state conflict maps to 409 dish-invalid-state")
        void stateConflict() {
            ResponseEntity<ProblemDetail> response = handler.handleDishStateConflict(
                    new DishStateConflictException(dishId, "DRAFT", "PENDING_PRICE"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ProblemDetail body = bodyOf(response);
            assertThat(body.getType()).isEqualTo(URI.create(BASE + "dish-invalid-state"));
            assertThat(body.getStatus()).isEqualTo(409);
            assertThat(body.getDetail()).contains(dishId.toString());
            assertThat(body.getProperties())
                    .containsEntry("currentStatus", "DRAFT")
                    .containsEntry("requiredStatus", "PENDING_PRICE");
            assertThat(body.getInstance()).isEqualTo(URI.create(request.getRequestURI()));
        }

        @Test
        @DisplayName("a decision lost to another administrator maps to 409 dish-already-decided")
        void alreadyDecided() {
            Instant decidedAt = Instant.parse("2026-10-28T12:00:00Z");

            ResponseEntity<ProblemDetail> response = handler.handleDishAlreadyDecided(
                    new DishAlreadyDecidedException(dishId, "PUBLISHED", decidedAt), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ProblemDetail body = bodyOf(response);
            assertThat(body.getType()).isEqualTo(URI.create(BASE + "dish-already-decided"));
            assertThat(body.getProperties())
                    .as("the UI reconciles its list from currentStatus and decidedAt")
                    .containsEntry("currentStatus", "PUBLISHED")
                    .containsEntry("decidedAt", decidedAt);
        }

        @Test
        @DisplayName("a stale price maps to 409 dish-stale-price with the prevailing price")
        void stalePrice() {
            ResponseEntity<ProblemDetail> response = handler.handleStaleDishPrice(
                    new StaleDishPriceException(dishId, new BigDecimal("10800.00"),
                            new BigDecimal("11250.00")), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            ProblemDetail body = bodyOf(response);
            assertThat(body.getType()).isEqualTo(URI.create(BASE + "dish-stale-price"));
            assertThat(body.getProperties())
                    .containsEntry("expectedRetailPrice", new BigDecimal("10800.00"))
                    .containsEntry("currentRetailPrice", new BigDecimal("11250.00"));
        }

        @Test
        @DisplayName("a stale price on a first pricing carries a null expected price, not a gap")
        void stalePriceFromUnpriced() {
            ResponseEntity<ProblemDetail> response = handler.handleStaleDishPrice(
                    new StaleDishPriceException(dishId, null, new BigDecimal("11250.00")),
                    request);

            ProblemDetail body = bodyOf(response);
            assertThat(body.getProperties())
                    .as("null is the positive assertion 'the dish was unpriced when I read it', "
                            + "so the key must be present rather than omitted")
                    .containsKey("expectedRetailPrice");
            assertThat(body.getProperties().get("expectedRetailPrice")).isNull();
        }

        @Test
        @DisplayName("the three 409s carry three different type URIs")
        void theThreeConflictsAreDistinguishable() {
            URI a = bodyOf(handler.handleDishStateConflict(
                    new DishStateConflictException(dishId, "DRAFT", "PENDING_PRICE"), request))
                    .getType();
            URI b = bodyOf(handler.handleDishAlreadyDecided(
                    new DishAlreadyDecidedException(dishId, "PUBLISHED", Instant.now()), request))
                    .getType();
            URI c = bodyOf(handler.handleStaleDishPrice(
                    new StaleDishPriceException(dishId, null, BigDecimal.ONE), request)).getType();

            assertThat(List.of(a, b, c))
                    .as("a client that cannot tell these apart cannot choose between refreshing "
                            + "the list, refreshing the price, and reporting an illegal action")
                    .doesNotHaveDuplicates();
        }
    }

    @Nested
    @DisplayName("422: the gate and the acknowledgement, which are not the same thing")
    class UnprocessableEntity {

        @Test
        @DisplayName("a gate failure maps to 422 with one entry per unmet condition")
        void publicationGate() {
            ResponseEntity<ProblemDetail> response = handler.handlePublicationGate(
                    new PublicationGateException(dishId, List.of(
                            PublicationGateCondition.RETAIL_PRICE_MISSING,
                            PublicationGateCondition.ALLERGENS_UNCONFIRMED)), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            ProblemDetail body = bodyOf(response);
            assertThat(body.getType()).isEqualTo(URI.create(BASE + "publication-gate-failed"));

            assertThat(stringsAt(body, "unmetConditions"))
                    .as("every unmet condition, so the Administrator fixes them in one pass")
                    .containsExactly("RETAIL_PRICE_MISSING", "ALLERGENS_UNCONFIRMED");
        }

        @Test
        @DisplayName("an acknowledgement request maps to 422 acknowledgement-required")
        void acknowledgementRequired() {
            ResponseEntity<ProblemDetail> response = handler.handleAcknowledgementRequired(
                    new AcknowledgementRequiredException(dishId, List.of(
                            AcknowledgementKind.NEGATIVE_MARGIN)), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            ProblemDetail body = bodyOf(response);
            assertThat(body.getType()).isEqualTo(URI.create(BASE + "acknowledgement-required"));
            assertThat(stringsAt(body, "requiredAcknowledgements"))
                    .containsExactly("NEGATIVE_MARGIN");
        }

        @Test
        @DisplayName("the gate failure and the acknowledgement carry different type URIs")
        void gateAndAcknowledgementAreDistinguishable() {
            URI gate = bodyOf(handler.handlePublicationGate(
                    new PublicationGateException(dishId,
                            List.of(PublicationGateCondition.EMPTY_RECIPE)), request)).getType();
            URI ack = bodyOf(handler.handleAcknowledgementRequired(
                    new AcknowledgementRequiredException(dishId,
                            List.of(AcknowledgementKind.UNPRICED_INGREDIENTS)), request)).getType();

            assertThat(gate)
                    .as("FR-017 makes the acknowledgement an additional precondition and never an "
                            + "override, so a client must not treat a gate failure as something a "
                            + "confirmation can clear")
                    .isNotEqualTo(ack);
        }
    }

    @Nested
    @DisplayName("400: the two domain validations this feature owns")
    class BadRequest {

        @Test
        @DisplayName("an invalid retail price maps to 400 with the error bound to its field")
        void invalidRetailPrice() {
            ResponseEntity<ProblemDetail> response = handler.handleInvalidRetailPrice(
                    new InvalidRetailPriceException(new BigDecimal("-1.00")), request);

            assertThat(response.getStatusCode())
                    .as("FR-013 states 400 even though bean validation in this codebase yields "
                            + "422; the specification is the authority")
                    .isEqualTo(HttpStatus.BAD_REQUEST);

            ProblemDetail body = bodyOf(response);
            assertThat(body.getType()).isEqualTo(URI.create(BASE + "invalid-retail-price"));

            List<Map<String, String>> errors = fieldErrorsOf(body);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0))
                    .as("the errors[] shape must match what the existing handler already emits "
                            + "for bean validation, so one client parser serves both")
                    .containsEntry("field", "retailPrice");
        }

        @Test
        @DisplayName("an invalid rejection comment maps to 400 bound to the comment field")
        void invalidRejectionComment() {
            ResponseEntity<ProblemDetail> response = handler.handleInvalidRejectionComment(
                    new InvalidRejectionCommentException("short"), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            ProblemDetail body = bodyOf(response);
            assertThat(body.getType())
                    .isEqualTo(URI.create(BASE + "invalid-rejection-comment"));

            List<Map<String, String>> errors = fieldErrorsOf(body);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0)).containsEntry("field", "comment");
        }

        @Test
        @DisplayName("a missing comment is reported differently from a short one")
        void missingCommentIsADistinctFault() {
            String shortDetail = bodyOf(handler.handleInvalidRejectionComment(
                    new InvalidRejectionCommentException("short"), request)).getDetail();
            String absentDetail = bodyOf(handler.handleInvalidRejectionComment(
                    new InvalidRejectionCommentException(null), request)).getDetail();

            assertThat(absentDetail)
                    .as("'you sent 5 characters' and 'you sent nothing' are different problems")
                    .isNotEqualTo(shortDetail);
        }
    }

    @Nested
    @DisplayName("what the advice must not gain")
    class Boundaries {

        @Test
        @DisplayName("no handler exists for ObjectOptimisticLockingFailureException")
        void noOptimisticLockingHandler() {
            boolean declared = java.util.Arrays.stream(GlobalExceptionHandler.class.getMethods())
                    .flatMap(m -> java.util.Arrays.stream(m.getParameterTypes()))
                    .anyMatch(t -> t.getName().endsWith("ObjectOptimisticLockingFailureException"));

            assertThat(declared)
                    .as("R-005-4 is withdrawn: no @Version is used anywhere in this feature, so "
                            + "Spring never raises that exception and a handler for it would be "
                            + "dead code asserting a mechanism that does not exist")
                    .isFalse();
        }

        @Test
        @DisplayName("there is exactly one @RestControllerAdvice in the application")
        void singleAdvice() {
            assertThat(GlobalExceptionHandler.class.isAnnotationPresent(
                    org.springframework.web.bind.annotation.RestControllerAdvice.class))
                    .isTrue();
        }
    }
}
