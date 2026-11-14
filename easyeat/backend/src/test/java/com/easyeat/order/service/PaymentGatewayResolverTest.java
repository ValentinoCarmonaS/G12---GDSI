package com.easyeat.order.service;

import com.easyeat.operations.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayResolverTest {

    @Mock
    private PaymentGatewayPort mercadoPagoAdapter;

    @Mock
    private PaymentGatewayPort stripeAdapter;

    @Test
    @DisplayName("Should resolve adapter matching PaymentMethod")
    void shouldResolveAdapterMatchingPaymentMethod() {
        when(mercadoPagoAdapter.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);
        when(stripeAdapter.getPaymentMethod()).thenReturn(PaymentMethod.STRIPE);

        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(mercadoPagoAdapter, stripeAdapter));

        PaymentGatewayPort resolved = resolver.resolve(PaymentMethod.MERCADO_PAGO);
        assertThat(resolved).isEqualTo(mercadoPagoAdapter);

        PaymentGatewayPort resolvedStripe = resolver.resolve(PaymentMethod.STRIPE);
        assertThat(resolvedStripe).isEqualTo(stripeAdapter);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when no adapter is registered for PaymentMethod")
    void shouldThrowExceptionWhenAdapterNotFound() {
        when(mercadoPagoAdapter.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);

        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(mercadoPagoAdapter));

        assertThatThrownBy(() -> resolver.resolve(PaymentMethod.STRIPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No payment gateway adapter registered for method: STRIPE");
    }

    @Test
    @DisplayName("Should resolve default payment gateway (MERCADO_PAGO)")
    void shouldResolveDefaultGateway() {
        when(mercadoPagoAdapter.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);

        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(mercadoPagoAdapter));

        PaymentGatewayPort defaultGateway = resolver.resolveDefault();
        assertThat(defaultGateway).isEqualTo(mercadoPagoAdapter);
    }

    @Test
    @DisplayName("findAdapter returns matching adapter when registered")
    void findAdapterReturnsPresentWhenRegistered() {
        when(mercadoPagoAdapter.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);

        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(mercadoPagoAdapter));

        assertThat(resolver.findAdapter(PaymentMethod.MERCADO_PAGO)).contains(mercadoPagoAdapter);
    }

    @Test
    @DisplayName("findAdapter returns empty for unregistered method or null without changing resolve(null) fallback")
    void findAdapterReturnsEmptyWhenUnregisteredOrNull() {
        when(mercadoPagoAdapter.getPaymentMethod()).thenReturn(PaymentMethod.MERCADO_PAGO);

        PaymentGatewayResolver resolver = new PaymentGatewayResolver(List.of(mercadoPagoAdapter));

        assertThat(resolver.findAdapter(PaymentMethod.STRIPE)).isEmpty();
        assertThat(resolver.findAdapter(null)).isEmpty();
        assertThat(resolver.resolve(null)).isEqualTo(mercadoPagoAdapter);
    }
}

