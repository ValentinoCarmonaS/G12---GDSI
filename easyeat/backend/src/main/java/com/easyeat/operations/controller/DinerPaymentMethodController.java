package com.easyeat.operations.controller;

import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.operations.dto.DinerPaymentMethodResponse;
import com.easyeat.operations.dto.ValidatePaymentRequest;
import com.easyeat.operations.dto.ValidatePaymentResponse;
import com.easyeat.operations.service.PaymentMethodService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Any authenticated user may read the methods of an establishment and revalidate one at
 * confirmation; the contract demands a bearer token but no particular role, which the global rule
 * of {@code SecurityConfig} already enforces.
 */
@RestController
@RequestMapping("/api/v1/establishments/{tenantId}")
public class DinerPaymentMethodController {

    private final PaymentMethodService paymentMethodService;
    private final EstablishmentRepository establishmentRepository;

    public DinerPaymentMethodController(PaymentMethodService paymentMethodService,
                                        EstablishmentRepository establishmentRepository) {
        this.paymentMethodService = paymentMethodService;
        this.establishmentRepository = establishmentRepository;
    }

    @GetMapping("/payment-methods")
    public List<DinerPaymentMethodResponse> getPaymentMethods(@PathVariable UUID tenantId) {
        requireEstablishment(tenantId);
        return paymentMethodService.getEnabledMethods(tenantId).stream().map(DinerPaymentMethodResponse::from).toList();
    }

    /** INV-9: checked against the current state at the moment of confirmation, never from a cache. */
    @PostMapping("/checkout/validate-payment")
    public ValidatePaymentResponse validatePayment(@PathVariable UUID tenantId,
                                                   @RequestBody ValidatePaymentRequest request) {
        requireEstablishment(tenantId);
        paymentMethodService.validateEnabled(tenantId, request.paymentMethod());
        return new ValidatePaymentResponse(true);
    }

    private void requireEstablishment(UUID tenantId) {
        if (!establishmentRepository.existsById(tenantId)) {
            throw new EstablishmentNotFoundException(tenantId);
        }
    }
}
