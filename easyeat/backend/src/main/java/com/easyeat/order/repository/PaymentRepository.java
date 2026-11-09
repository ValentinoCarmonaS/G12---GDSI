package com.easyeat.order.repository;

import com.easyeat.order.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByGatewayPaymentId(String gatewayPaymentId);

    Optional<Payment> findByGatewayPreferenceId(String gatewayPreferenceId);

    List<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findTopByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}

