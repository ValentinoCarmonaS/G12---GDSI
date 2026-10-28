package com.easyeat.auth.controller;

import com.easyeat.auth.exception.AccountSuspendedException;
import com.easyeat.auth.exception.EmailAlreadyRegisteredException;
import com.easyeat.auth.exception.InvalidCredentialsException;
import com.easyeat.auth.exception.InvalidTokenException;
import com.easyeat.auth.exception.RateLimitExceededException;
import com.easyeat.auth.exception.TokenAlreadyConsumedException;
import com.easyeat.auth.exception.TokenExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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
import com.easyeat.dish.exception.*;
import com.easyeat.common.exception.TenantViolationException;
import com.easyeat.inventory.exception.NegativeStockException;
import com.easyeat.profile.exception.CooldownRateLimitException;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.exception.EmailVerificationException;
import com.easyeat.staff.exception.OrphanedAdminException;
import org.springframework.security.access.AccessDeniedException;


@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

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

    @ExceptionHandler(PhotoStorageException.class)
    public ResponseEntity<ProblemDetail> handlePhotoStorage(PhotoStorageException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/photo-storage-error"));
        problem.setTitle("Photo Storage Error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
}

