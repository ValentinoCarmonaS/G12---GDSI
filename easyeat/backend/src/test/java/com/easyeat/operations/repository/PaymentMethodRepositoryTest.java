package com.easyeat.operations.repository;

import com.easyeat.operations.OperationsPostgresTestBase;
import com.easyeat.operations.domain.EstablishmentPaymentMethod;
import com.easyeat.operations.domain.PaymentMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentMethodRepository queries over the seeded rows")
class PaymentMethodRepositoryTest extends OperationsPostgresTestBase {

    private static final UUID BISTRO_ID = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");

    @Autowired
    private PaymentMethodRepository repository;

    @Test
    @DisplayName("lists the two seeded methods of one establishment")
    void listsSeededMethods() {
        assertThat(repository.findByEstablishmentId(BISTRO_ID))
                .extracting(EstablishmentPaymentMethod::getPaymentMethod)
                .containsExactlyInAnyOrder(PaymentMethod.MERCADO_PAGO, PaymentMethod.STRIPE);
    }

    @Test
    @DisplayName("finds one method by establishment, and returns empty for an unknown establishment")
    void findsOneMethod() {
        assertThat(repository.findByEstablishmentIdAndPaymentMethod(BISTRO_ID, PaymentMethod.STRIPE))
                .hasValueSatisfying(method -> assertThat(method.isEnabled()).isTrue());
        assertThat(repository.findByEstablishmentIdAndPaymentMethod(UUID.randomUUID(), PaymentMethod.STRIPE))
                .isEmpty();
        assertThat(repository.findByEstablishmentId(UUID.randomUUID())).isEmpty();
    }
}
