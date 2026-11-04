package com.easyeat.operations.controller;

import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.operations.dto.PaymentMethodResponse;
import com.easyeat.operations.dto.TogglePaymentMethodRequest;
import com.easyeat.operations.service.PaymentMethodService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payment-methods")
@PreAuthorize("hasRole('ADMIN') or hasRole('ADMINISTRADOR')")
public class AdminPaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    public AdminPaymentMethodController(PaymentMethodService paymentMethodService) {
        this.paymentMethodService = paymentMethodService;
    }

    @GetMapping
    public List<PaymentMethodResponse> getPaymentMethods() {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return paymentMethodService.getAdminView(tenantId).stream().map(PaymentMethodResponse::from).toList();
    }

    @PutMapping("/{paymentMethod}")
    public PaymentMethodResponse togglePaymentMethod(@PathVariable PaymentMethod paymentMethod,
                                                     @RequestBody TogglePaymentMethodRequest request) {
        UUID tenantId = TenantContextHolder.getRequiredTenantId();
        return PaymentMethodResponse.from(
                paymentMethodService.toggle(tenantId, paymentMethod, request.isEnabled(), request.expectedVersion()));
    }
}
