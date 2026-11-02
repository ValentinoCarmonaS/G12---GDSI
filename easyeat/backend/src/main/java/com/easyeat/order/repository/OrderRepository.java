package com.easyeat.order.repository;

import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("""
        SELECT COUNT(o) FROM Order o
        WHERE o.establishmentId = :establishmentId
          AND o.pickupDate = :pickupDate
          AND o.timeSlotStart = :timeSlotStart
          AND (
            o.status IN (com.easyeat.order.domain.OrderStatus.CONFIRMED, com.easyeat.order.domain.OrderStatus.PAID)
            OR (o.status = com.easyeat.order.domain.OrderStatus.PENDING_PAYMENT AND (o.expiresAt IS NULL OR o.expiresAt > :now))
          )
    """)
    long countActiveOrdersBySlot(
        @Param("establishmentId") UUID establishmentId,
        @Param("pickupDate") LocalDate pickupDate,
        @Param("timeSlotStart") LocalTime timeSlotStart,
        @Param("now") Instant now
    );

    @Query("""
        SELECT COUNT(o) > 0 FROM Order o
        WHERE o.userId = :userId
          AND o.status = com.easyeat.order.domain.OrderStatus.PENDING_PAYMENT
          AND (o.expiresAt IS NULL OR o.expiresAt > :now)
    """)
    boolean hasActivePendingOrder(
        @Param("userId") UUID userId,
        @Param("now") Instant now
    );

    @Query("""
        SELECT o FROM Order o
        WHERE o.status = com.easyeat.order.domain.OrderStatus.PENDING_PAYMENT
          AND o.expiresAt IS NOT NULL
          AND o.expiresAt <= :now
    """)
    List<Order> findExpiredPendingOrders(@Param("now") Instant now);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
