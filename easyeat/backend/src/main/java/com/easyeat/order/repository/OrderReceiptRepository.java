package com.easyeat.order.repository;

import com.easyeat.order.domain.OrderReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderReceiptRepository extends JpaRepository<OrderReceipt, UUID> {

    Optional<OrderReceipt> findByOrderId(UUID orderId);

    Optional<OrderReceipt> findByIdempotencyKey(String idempotencyKey);

    boolean existsByOrderId(UUID orderId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
