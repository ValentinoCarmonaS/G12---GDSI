package com.easyeat.order.service;

import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.order.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayPortTest {

    @Test
    @DisplayName("Should instantiate and verify PaymentGatewayPort command and result DTOs")
    void testCommandAndResultDtos() {
        PaymentGatewayPort.GatewayItem item = new PaymentGatewayPort.GatewayItem(
                "Plato Prueba", 2, new BigDecimal("1200.00"), "ARS"
        );
        PaymentGatewayPort.CreatePreferenceCommand command = new PaymentGatewayPort.CreatePreferenceCommand(
                UUID.randomUUID().toString(),
                new BigDecimal("2400.00"),
                "ARS",
                "payer@test.com",
                List.of(item),
                "key-idemp-1"
        );

        PaymentGatewayPort.PreferenceResult result = new PaymentGatewayPort.PreferenceResult(
                "pref-123",
                "https://mp.com/init",
                "https://sandbox.mp.com/init",
                true
        );

        PaymentGatewayPort.GatewayPaymentResult queryResult = new PaymentGatewayPort.GatewayPaymentResult(
                "pay-999",
                "ext-ref-1",
                PaymentStatus.APPROVED,
                "accredited",
                new BigDecimal("2400.00"),
                "ARS"
        );

        PaymentGatewayPort.WebhookPayload webhookPayload = new PaymentGatewayPort.WebhookPayload(
                "payment",
                "payment.created",
                "pay-999",
                false,
                Map.of("id", "pay-999")
        );

        assertThat(command.externalReference()).isNotNull();
        assertThat(command.items()).hasSize(1);
        assertThat(result.preferenceId()).isEqualTo("pref-123");
        assertThat(queryResult.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(webhookPayload.action()).isEqualTo("payment.created");
    }

    @Test
    @DisplayName("Should declare required methods in PaymentGatewayPort interface")
    void testInterfaceContract() {
        assertThat(PaymentGatewayPort.class.isInterface()).isTrue();
        assertThat(PaymentGatewayPort.class.getMethods())
                .extracting(java.lang.reflect.Method::getName)
                .contains("getPaymentMethod", "createPreference", "queryPayment", "verifyWebhookSignature", "parseWebhook");
    }
}

