package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.dto.*;
import com.easyeat.order.service.CheckoutConfirmationService;
import com.easyeat.order.service.CheckoutPaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutPaymentController {

    private final CheckoutPaymentService checkoutPaymentService;
    private final CheckoutConfirmationService checkoutConfirmationService;

    public CheckoutPaymentController(
            CheckoutPaymentService checkoutPaymentService,
            CheckoutConfirmationService checkoutConfirmationService
    ) {
        this.checkoutPaymentService = checkoutPaymentService;
        this.checkoutConfirmationService = checkoutConfirmationService;
    }

    @PostMapping("/pay")
    @PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")
    public ResponseEntity<PaymentInitiationResponse> initiatePayment(
            @Valid @RequestBody PaymentInitiationRequest request,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        PaymentInitiationResponse response = checkoutPaymentService.initiatePayment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/cancel-payment")
    @PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")
    public ResponseEntity<CancelPaymentResponse> cancelPayment(
            @Valid @RequestBody CancelPaymentRequest request,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        CancelPaymentResponse response = checkoutPaymentService.cancelPaymentAttempt(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm-payment")
    @PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")
    public ResponseEntity<PaymentConfirmationResponse> confirmPayment(
            @Valid @RequestBody PaymentConfirmationRequest request,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        PaymentConfirmationResponse response = checkoutConfirmationService.confirmPayment(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pending-payment")
    @PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")
    public ResponseEntity<PendingPaymentResponse> pendingPayment(
            @Valid @RequestBody PendingPaymentRequest request,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        PendingPaymentResponse response = checkoutConfirmationService.registerPendingPayment(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm-zero-amount")
    @PreAuthorize("hasRole('ROLE_COMENSAL') or hasRole('COMENSAL')")
    public ResponseEntity<ZeroAmountOrderResponse> confirmZeroAmount(
            @Valid @RequestBody ZeroAmountOrderRequest request,
            @AuthenticationPrincipal User user,
            Authentication authentication
    ) {
        UUID userId = resolveUserId(user, authentication);
        ZeroAmountOrderResponse response = checkoutConfirmationService.confirmZeroAmountOrder(userId, request);
        return ResponseEntity.ok(response);
    }

    private UUID resolveUserId(User user, Authentication authentication) {
        if (user != null && user.getId() != null) {
            return user.getId();
        }
        if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User u) {
                return u.getId();
            }
            if (principal instanceof UUID uid) {
                return uid;
            }
            if (principal instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        throw new AccessDeniedException("No authenticated diner found in security context");
    }
}
