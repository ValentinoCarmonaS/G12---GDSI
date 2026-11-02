package com.easyeat.operations.service;

import com.easyeat.operations.domain.EstablishmentPaymentMethod;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.repository.PaymentMethodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentMethodService: write races surface as the same conflict")
class PaymentMethodServiceTest {

    private static final UUID ID = UUID.randomUUID();

    @Mock
    private PaymentMethodRepository repository;

    @Test
    @DisplayName("EC-012: two admins creating the same row, the second hits the unique constraint")
    void mapsUniqueViolationOnCreate() {
        when(repository.findByEstablishmentIdAndPaymentMethod(ID, PaymentMethod.STRIPE)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_establishment_payment_method"));

        assertThatThrownBy(() -> new PaymentMethodService(repository).toggle(ID, PaymentMethod.STRIPE, false, null))
                .isInstanceOf(OperatingConfigConflictException.class);
    }

    @Test
    @DisplayName("EC-012: a lost write race at flush time on an existing row")
    void mapsOptimisticLockOnUpdate() {
        var row = new EstablishmentPaymentMethod(ID, PaymentMethod.STRIPE, true);
        org.springframework.test.util.ReflectionTestUtils.setField(row, "version", 1L);
        when(repository.findByEstablishmentIdAndPaymentMethod(ID, PaymentMethod.STRIPE)).thenReturn(Optional.of(row));
        when(repository.saveAndFlush(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(EstablishmentPaymentMethod.class, ID));

        assertThatThrownBy(() -> new PaymentMethodService(repository).toggle(ID, PaymentMethod.STRIPE, false, 1L))
                .isInstanceOf(OperatingConfigConflictException.class);
    }
}
