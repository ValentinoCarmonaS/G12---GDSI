package com.easyeat.auth.controller;

import com.easyeat.auth.exception.AccountSuspendedException;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.auth.exception.InvalidCredentialsException;
import com.easyeat.auth.exception.InvalidTokenException;
import com.easyeat.auth.exception.RateLimitExceededException;
import com.easyeat.auth.exception.TokenAlreadyConsumedException;
import com.easyeat.auth.exception.TokenExpiredException;
import com.easyeat.dish.exception.AcknowledgementRequiredException;
import com.easyeat.dish.exception.DishAlreadyDecidedException;
import com.easyeat.dish.exception.DishStateConflictException;
import com.easyeat.dish.exception.InvalidRejectionCommentException;
import com.easyeat.dish.exception.InvalidRetailPriceException;
import com.easyeat.dish.exception.PublicationGateException;
import com.easyeat.dish.exception.StaleDishPriceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.exception.InvalidSearchQueryException;
import com.easyeat.dish.exception.*;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.inventory.exception.NegativeStockException;
import com.easyeat.operations.exception.InvalidCapacityException;
import com.easyeat.operations.exception.InvalidScheduleException;
import com.easyeat.operations.exception.SlotSoldOutException;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.exception.PaymentMethodUnavailableException;
import com.easyeat.profile.exception.CooldownRateLimitException;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.exception.EmailVerificationException;
import com.easyeat.staff.exception.OrphanedAdminException;
import com.easyeat.order.exception.ActiveReservationExistsException;
import com.easyeat.order.exception.DishPausedByChefException;
import com.easyeat.order.exception.InvalidFulfillmentModeException;
import com.easyeat.order.exception.InvalidOrderStateException;
import com.easyeat.order.exception.LeadTimeViolationException;
import com.easyeat.order.exception.SlotCapacityExceededException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "One or more fields failed validation.");
        problem.setType(URI.create("https://easyeat.com/errors/validation-failed"));
        problem.setTitle("Validation Failed");
        if (request instanceof ServletWebRequest servletWebRequest) {
            problem.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }

        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            Map<String, String> err = new HashMap<>();
            err.put("field", fieldError.getField());
            err.put("message", fieldError.getDefaultMessage());
            errors.add(err);
        }
        problem.setProperty("errors", errors);

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /**
     * 400: spec 007's {@code expectedRetailPrice} was absent from the request body (FR-013,
     * FR-025). Its field carries {@code @JsonProperty(required = true)}, which Jackson checks for
     * presence rather than non-nullity, so this fires only when the key itself is missing — an
     * explicit {@code null} deserializes normally and never reaches here. Any other malformed-body
     * failure falls through to Spring's default handling, unchanged.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (!isMissingExpectedRetailPrice(ex)) {
            return super.handleHttpMessageNotReadable(ex, headers, status, request);
        }

        String detail = "El campo 'expectedRetailPrice' es obligatorio en el cuerpo de la solicitud, aunque su valor sea null.";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://easyeat.com/errors/expected-retail-price-required"));
        problem.setTitle("Expected Retail Price Required");
        if (request instanceof ServletWebRequest servletWebRequest) {
            problem.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }
        problem.setProperty("errors", List.of(fieldError("expectedRetailPrice", detail)));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * Matched on the message Jackson emits for a missing required creator property, rather than
     * on jackson-databind's own exception types, so this stays correct across a minor Jackson
     * version bump without adding an import this file does not otherwise need.
     */
    private static boolean isMissingExpectedRetailPrice(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause != null && cause.getMessage() != null
                && cause.getMessage().contains("Missing required creator property 'expectedRetailPrice'");
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/email-already-registered"));
        problem.setTitle("Email Already Registered");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-credentials"));
        problem.setTitle("Invalid Credentials");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<ProblemDetail> handleAccountSuspended(AccountSuspendedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/account-suspended"));
        problem.setTitle("Account Suspended");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/rate-limit-exceeded"));
        problem.setTitle("Too Many Requests");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/token-invalid"));
        problem.setTitle("Invalid Reset Token");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ProblemDetail> handleTokenExpired(TokenExpiredException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/token-expired"));
        problem.setTitle("Reset Token Expired");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(TokenAlreadyConsumedException.class)
    public ResponseEntity<ProblemDetail> handleTokenAlreadyConsumed(TokenAlreadyConsumedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/token-already-consumed"));
        problem.setTitle("Reset Token Already Used");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(NegativeStockException.class)
    public ResponseEntity<ProblemDetail> handleNegativeStock(NegativeStockException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/negative-stock"));
        problem.setTitle("Negative Stock Invariant Violated");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getCurrentStock() != null) {
            problem.setProperty("currentStock", ex.getCurrentStock());
        }
        if (ex.getAttemptedDelta() != null) {
            problem.setProperty("attemptedDelta", ex.getAttemptedDelta());
        }
        if (ex.getResultingStock() != null) {
            problem.setProperty("resultingStock", ex.getResultingStock());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(OrphanedAdminException.class)
    public ResponseEntity<ProblemDetail> handleOrphanedAdmin(OrphanedAdminException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/orphaned-admin"));
        problem.setTitle("Orphaned Administrator Constraint Violated");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getTenantId() != null) {
            problem.setProperty("tenantId", ex.getTenantId().toString());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(TenantViolationException.class)
    public ResponseEntity<ProblemDetail> handleTenantViolation(TenantViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/tenant-violation"));
        problem.setTitle("Cross-Tenant Access Prohibited");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied: insufficient privileges.");
        problem.setType(URI.create("https://easyeat.com/errors/forbidden"));
        problem.setTitle("Forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/not-found"));
        problem.setTitle("Resource Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalState(IllegalStateException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/conflict"));
        problem.setTitle("Operation Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(EmailVerificationException.class)
    public ResponseEntity<ProblemDetail> handleEmailVerification(EmailVerificationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-verification-token"));
        problem.setTitle("Invalid Email Verification Token");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(CooldownRateLimitException.class)
    public ResponseEntity<ProblemDetail> handleCooldownRateLimit(CooldownRateLimitException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/email-cooldown-active"));
        problem.setTitle("Email Verification Cooldown Active");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(problem);
    }

    @ExceptionHandler(DietaryTaxonomyException.class)
    public ResponseEntity<ProblemDetail> handleDietaryTaxonomy(DietaryTaxonomyException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-dietary-taxonomy"));
        problem.setTitle("Invalid Dietary Taxonomy");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(DishNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDishNotFound(DishNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-not-found"));
        problem.setTitle("Dish Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex instanceof com.easyeat.dish.exception.DishNotFoundException dishEx && dishEx.getDishId() != null) {
            problem.setProperty("dishId", dishEx.getDishId().toString());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DishLockedException.class)
    public ResponseEntity<ProblemDetail> handleDishLocked(DishLockedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-locked"));
        problem.setTitle("Dish Locked");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getDishId() != null) {
            problem.setProperty("dishId", ex.getDishId().toString());
        }
        if (ex.getCurrentStatus() != null) {
            problem.setProperty("currentStatus", ex.getCurrentStatus());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DuplicateDishNameException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateDishName(DuplicateDishNameException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-name-duplicate"));
        problem.setTitle("Duplicate Dish Name");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getDishName() != null) {
            problem.setProperty("dishName", ex.getDishName());
        }
        if (ex.getTenantId() != null) {
            problem.setProperty("tenantId", ex.getTenantId().toString());
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(IncompleteDishException.class)
    public ResponseEntity<ProblemDetail> handleIncompleteDish(IncompleteDishException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-incomplete"));
        problem.setTitle("Incomplete Dish Technical File");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getDishId() != null) {
            problem.setProperty("dishId", ex.getDishId().toString());
        }
        if (ex.getMissingConditions() != null) {
            problem.setProperty("missingConditions", ex.getMissingConditions());
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    @ExceptionHandler(InvalidIngredientException.class)
    public ResponseEntity<ProblemDetail> handleInvalidIngredient(InvalidIngredientException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-ingredient"));
        problem.setTitle("Invalid Recipe Ingredient");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getIngredientId() != null) {
            problem.setProperty("ingredientId", ex.getIngredientId().toString());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(com.easyeat.dish.exception.EmptyRecipeException.class)
    public ResponseEntity<ProblemDetail> handleEmptyRecipe(com.easyeat.dish.exception.EmptyRecipeException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/empty-recipe"));
        problem.setTitle("Empty Recipe");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getDishId() != null) {
            problem.setProperty("dishId", ex.getDishId().toString());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidQuantityException.class)
    public ResponseEntity<ProblemDetail> handleInvalidQuantity(InvalidQuantityException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-quantity"));
        problem.setTitle("Invalid Recipe Quantity");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getQuantity() != null) {
            problem.setProperty("quantity", ex.getQuantity());
        }
        if (ex.getUnitOfMeasure() != null) {
            problem.setProperty("unitOfMeasure", ex.getUnitOfMeasure());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(EstablishmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEstablishmentNotFound(EstablishmentNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/establishment-not-found"));
        problem.setTitle("Establishment Not Found");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getEstablishmentId() != null) {
            problem.setProperty("establishmentId", ex.getEstablishmentId().toString());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(EstablishmentInactiveException.class)
    public ResponseEntity<ProblemDetail> handleEstablishmentInactive(EstablishmentInactiveException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/establishment-inactive"));
        problem.setTitle("Establishment Inactive");
        problem.setInstance(URI.create(request.getRequestURI()));
        if (ex.getEstablishmentId() != null) {
            problem.setProperty("establishmentId", ex.getEstablishmentId().toString());
        }
        if (ex.getCurrentStatus() != null) {
            problem.setProperty("currentStatus", ex.getCurrentStatus());
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(PhotoStorageException.class)
    public ResponseEntity<ProblemDetail> handlePhotoStorage(PhotoStorageException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/photo-storage-error"));
        problem.setTitle("Photo Storage Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(InvalidSearchQueryException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSearchQuery(InvalidSearchQueryException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-search-query"));
        problem.setTitle("Invalid Search Query");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // ================================================================
    // Spec 007 - Administrative Back-Office: PVP, approval and audit.
    //
    // Seven handlers, all for this feature's own exceptions. There is
    // deliberately NO handler for ObjectOptimisticLockingFailureException:
    // R-005-4 is withdrawn, no @Version exists anywhere in this feature, so
    // Spring never raises it and a handler would be dead code asserting a
    // mechanism that is not there. FR-025 is enforced by conditional UPDATEs
    // whose zero-row outcome raises the two 409s below.
    // ================================================================

    /** 409: the action was never legal from this state (FR-019, FR-024). */
    @ExceptionHandler(DishStateConflictException.class)
    public ResponseEntity<ProblemDetail> handleDishStateConflict(DishStateConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-invalid-state"));
        problem.setTitle("Dish State Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("currentStatus", ex.getCurrentStatus());
        problem.setProperty("requiredStatus", ex.getRequiredStatus());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * 409: the action was legal, and another Administrator took it first (FR-025).
     * Distinct from the above so the client knows to refresh its list rather than
     * report an illegal operation.
     */
    @ExceptionHandler(DishAlreadyDecidedException.class)
    public ResponseEntity<ProblemDetail> handleDishAlreadyDecided(DishAlreadyDecidedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-already-decided"));
        problem.setTitle("Dish Already Decided");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("currentStatus", ex.getCurrentStatus());
        problem.setProperty("decidedAt", ex.getDecidedAt());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * 409: the retail price changed between being read and being saved (FR-025).
     * Both prices may legitimately be null, so the keys are always set - an absent
     * key would mean something different from "the dish was unpriced".
     */
    @ExceptionHandler(StaleDishPriceException.class)
    public ResponseEntity<ProblemDetail> handleStaleDishPrice(StaleDishPriceException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-stale-price"));
        problem.setTitle("Stale Dish Price");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("expectedRetailPrice", ex.getExpectedRetailPrice());
        problem.setProperty("currentRetailPrice", ex.getCurrentRetailPrice());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * 422: the publication gate blocked the transition (FR-015). Carries every unmet
     * condition, so the Administrator fixes them in one pass rather than discovering
     * them one at a time.
     */
    @ExceptionHandler(PublicationGateException.class)
    public ResponseEntity<ProblemDetail> handlePublicationGate(PublicationGateException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/publication-gate-failed"));
        problem.setTitle("Publication Gate Failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("unmetConditions", ex.getUnmetConditions().stream().map(Enum::name).toList());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /**
     * 422: an advisory needs explicit confirmation (FR-014, FR-017). A different type
     * URI from the gate failure on purpose: the acknowledgement is an additional
     * precondition and never an override, so a client must not treat a gate failure
     * as something a confirmation can clear.
     */
    @ExceptionHandler(AcknowledgementRequiredException.class)
    public ResponseEntity<ProblemDetail> handleAcknowledgementRequired(AcknowledgementRequiredException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/acknowledgement-required"));
        problem.setTitle("Acknowledgement Required");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("requiredAcknowledgements", ex.getRequiredAcknowledgements().stream().map(Enum::name).toList());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /**
     * 400, not 422: FR-013 states 400, and the specification is the authority even
     * though this codebase's bean-validation path returns 422 (Principle I). The
     * errors[] shape matches what handleMethodArgumentNotValid already emits, so one
     * client parser serves both.
     */
    @ExceptionHandler(InvalidRetailPriceException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRetailPrice(InvalidRetailPriceException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-retail-price"));
        problem.setTitle("Invalid Retail Price");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", List.of(fieldError("retailPrice", ex.getMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** 400: FR-021 states 400, for the same reason as the price above. */
    @ExceptionHandler(InvalidRejectionCommentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRejectionComment(InvalidRejectionCommentException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-rejection-comment"));
        problem.setTitle("Invalid Rejection Comment");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", List.of(fieldError("comment", ex.getMessage())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @Override
    public ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "El archivo supera el tamaño máximo permitido de 5 MB."
        );
        problem.setType(URI.create("https://easyeat.com/errors/photo-storage-failed"));
        problem.setTitle("Maximum Upload Size Exceeded");
        if (request instanceof ServletWebRequest servletWebRequest) {
            problem.setInstance(URI.create(servletWebRequest.getRequest().getRequestURI()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** The errors[] entry shape already emitted for bean validation failures. */
    // ================================================================
    // Spec 010 - Restaurant Operation Configuration.
    //
    // The optimistic-lock failure is not mapped here as such: the operations
    // services translate it into OperatingConfigConflictException, so the 007
    // decision of having no global ObjectOptimisticLockingFailureException
    // handler stays untouched.
    // ================================================================

    /** 400: opening time not strictly before closing time (INV-7). */
    @ExceptionHandler(InvalidScheduleException.class)
    public ResponseEntity<ProblemDetail> handleInvalidSchedule(InvalidScheduleException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-schedule"));
        problem.setTitle("Parámetros de horario inválidos");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** 400: negative capacity (INV-6) or a slot start off the 15-minute grid (INV-5). */
    @ExceptionHandler(InvalidCapacityException.class)
    public ResponseEntity<ProblemDetail> handleInvalidCapacity(InvalidCapacityException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-capacity"));
        problem.setTitle("Capacidad inválida");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** 409: the slot reached its capacity, also when a concurrent approval took the last place (INV-2, EC-016). */
    @ExceptionHandler(SlotSoldOutException.class)
    public ResponseEntity<ProblemDetail> handleSlotSoldOut(SlotSoldOutException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/slot-sold-out"));
        problem.setTitle("Franja horaria agotada");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** 409: the payment method was disabled before the order was confirmed (INV-9, EC-010). */
    @ExceptionHandler(PaymentMethodUnavailableException.class)
    public ResponseEntity<ProblemDetail> handlePaymentMethodUnavailable(PaymentMethodUnavailableException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/payment-method-unavailable"));
        problem.setTitle("Método de pago no disponible");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** 409: the configuration was changed by another administrator since it was loaded (EC-012). */
    @ExceptionHandler(OperatingConfigConflictException.class)
    public ResponseEntity<ProblemDetail> handleOperatingConfigConflict(OperatingConfigConflictException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/operating-config-conflict"));
        problem.setTitle("Conflicto de concurrencia");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }


    // ================================================================
    // Spec 012 - Diner Order Fulfillment Mode & Pickup Time Slots
    // ================================================================

    /** 409: slot capacity exceeded / sold out under concurrent FCFS (FR-017, FR-018). */
    @ExceptionHandler(SlotCapacityExceededException.class)
    public ResponseEntity<ProblemDetail> handleSlotCapacityExceeded(SlotCapacityExceededException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/slot-sold-out"));
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "SLOT_SOLD_OUT");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** 409: diner already has an active pending reservation (FR-027). */
    @ExceptionHandler(ActiveReservationExistsException.class)
    public ResponseEntity<ProblemDetail> handleActiveReservationExists(ActiveReservationExistsException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/active-reservation-exists"));
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "ACTIVE_RESERVATION_EXISTS");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /** 422: selected slot violates minimum lead time (FR-009). */
    @ExceptionHandler(LeadTimeViolationException.class)
    public ResponseEntity<ProblemDetail> handleLeadTimeViolation(LeadTimeViolationException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/lead-time-violation"));
        problem.setTitle("Unprocessable Entity");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "LEAD_TIME_VIOLATION");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /** 422: dish paused by kitchen (FR-030 / Spec 013). */
    @ExceptionHandler(DishPausedByChefException.class)
    public ResponseEntity<ProblemDetail> handleDishPausedByChef(DishPausedByChefException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/dish-paused-by-chef"));
        problem.setTitle("Unprocessable Entity");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "DISH_PAUSED_BY_CHEF");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    /** 400: invalid fulfillment mode payload (FR-001, FR-002). */
    @ExceptionHandler(InvalidFulfillmentModeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidFulfillmentMode(InvalidFulfillmentModeException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-fulfillment-mode"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_FULFILLMENT_MODE");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** 409: invalid order state transition (e.g. cancelling non-pending order). */
    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrderState(InvalidOrderStateException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/invalid-order-state"));
        problem.setTitle("Conflict");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_ORDER_STATE");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    /**
     * 500 catch-all: any error not mapped above. Logged here and answered as a ProblemDetail so it is not
     * mistaken for an authentication failure. Authentication errors are rethrown to Spring Security.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
        if (ex instanceof AuthenticationException) {
            throw ex;
        }
        LOGGER.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intentá nuevamente más tarde.");
        problem.setType(URI.create("https://easyeat.com/errors/internal-server-error"));
        problem.setTitle("Internal Server Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    private static Map<String, String> fieldError(String field, String message) {
        Map<String, String> error = new HashMap<>();
        error.put("field", field);
        error.put("message", message);
        return error;
    }
}
