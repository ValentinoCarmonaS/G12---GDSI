package com.easyeat.operations.repository;

import com.easyeat.operations.domain.EstablishmentPaymentMethod;
import com.easyeat.operations.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<EstablishmentPaymentMethod, UUID> {

    List<EstablishmentPaymentMethod> findByEstablishmentId(UUID establishmentId);

    Optional<EstablishmentPaymentMethod> findByEstablishmentIdAndPaymentMethod(
            UUID establishmentId, PaymentMethod paymentMethod);
}
