package com.easyeat.order.service;

import com.easyeat.operations.domain.PaymentMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dynamic resolver for payment gateways based on PaymentMethod enum.
 * Uses Strategy Pattern to inject appropriate PaymentGatewayPort implementation.
 */
@Component
public class PaymentGatewayResolver {

    private final Map<PaymentMethod, PaymentGatewayPort> adapters = new EnumMap<>(PaymentMethod.class);

    @Autowired
    public PaymentGatewayResolver(List<PaymentGatewayPort> gatewayAdapters) {
        if (gatewayAdapters != null) {
            for (PaymentGatewayPort adapter : gatewayAdapters) {
                if (adapter.getPaymentMethod() != null) {
                    this.adapters.put(adapter.getPaymentMethod(), adapter);
                }
            }
        }
    }

    public PaymentGatewayPort resolve(PaymentMethod method) {
        PaymentMethod targetMethod = method != null ? method : PaymentMethod.MERCADO_PAGO;
        return findAdapter(targetMethod)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No payment gateway adapter registered for method: " + targetMethod));
    }

    /**
     * Looks up a registered adapter for the given {@link PaymentMethod} without throwing
     * or falling back to a default gateway when {@code method} is {@code null}.
     */
    public Optional<PaymentGatewayPort> findAdapter(PaymentMethod method) {
        if (method == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(adapters.get(method));
    }

    public PaymentGatewayPort resolveDefault() {
        return resolve(PaymentMethod.MERCADO_PAGO);
    }

    public void registerAdapter(PaymentGatewayPort adapter) {
        if (adapter != null && adapter.getPaymentMethod() != null) {
            this.adapters.put(adapter.getPaymentMethod(), adapter);
        }
    }
}

