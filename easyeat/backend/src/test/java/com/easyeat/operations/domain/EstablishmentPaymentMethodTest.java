package com.easyeat.operations.domain;

import com.easyeat.operations.OperationsPostgresTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EstablishmentPaymentMethod entity mapping")
class EstablishmentPaymentMethodTest extends OperationsPostgresTestBase {

    private static final UUID TRATTORIA_ID = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22");

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreSeed() {
        jdbcTemplate.update("UPDATE establishment_payment_methods SET is_enabled = TRUE, version = 0 "
                + "WHERE establishment_id = ?", TRATTORIA_ID);
    }

    @Test
    @DisplayName("maps the seeded row as an enabled method at version 0")
    void mapsSeededRow() {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            var stripe = entityManager.createQuery(
                            "select p from EstablishmentPaymentMethod p "
                                    + "where p.establishmentId = :e and p.paymentMethod = :m",
                            EstablishmentPaymentMethod.class)
                    .setParameter("e", TRATTORIA_ID)
                    .setParameter("m", PaymentMethod.STRIPE)
                    .getSingleResult();

            assertThat(stripe.isEnabled()).isTrue();
            assertThat(stripe.getVersion()).isZero();
            assertThat(stripe.getPaymentMethod()).isEqualTo(PaymentMethod.STRIPE);
            assertThat(stripe.getEstablishmentId()).isEqualTo(TRATTORIA_ID);
            assertThat(stripe.getId()).isNotNull();
            assertThat(stripe.getCreatedAt()).isNotNull();
            assertThat(stripe.getUpdatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("INV-4: toggling flips the flag and bumps the version on flush")
    void toggleBumpsVersion() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(s -> entityManager.createQuery(
                        "select p from EstablishmentPaymentMethod p "
                                + "where p.establishmentId = :e and p.paymentMethod = :m",
                        EstablishmentPaymentMethod.class)
                .setParameter("e", TRATTORIA_ID)
                .setParameter("m", PaymentMethod.MERCADO_PAGO)
                .getSingleResult()
                .setEnabled(false));

        tx.executeWithoutResult(s -> {
            var reloaded = entityManager.createQuery(
                            "select p from EstablishmentPaymentMethod p "
                                    + "where p.establishmentId = :e and p.paymentMethod = :m",
                            EstablishmentPaymentMethod.class)
                    .setParameter("e", TRATTORIA_ID)
                    .setParameter("m", PaymentMethod.MERCADO_PAGO)
                    .getSingleResult();
            assertThat(reloaded.isEnabled()).isFalse();
            assertThat(reloaded.getVersion()).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("a new instance records establishment, method and flag")
    void constructorRecordsFields() {
        var method = new EstablishmentPaymentMethod(TRATTORIA_ID, PaymentMethod.STRIPE, false);

        assertThat(method.getEstablishmentId()).isEqualTo(TRATTORIA_ID);
        assertThat(method.getPaymentMethod()).isEqualTo(PaymentMethod.STRIPE);
        assertThat(method.isEnabled()).isFalse();
    }
}
