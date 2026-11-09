package com.easyeat.order.repository;

import com.easyeat.order.domain.ReservationStatus;
import com.easyeat.order.domain.TemporaryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemporaryReservationRepository extends JpaRepository<TemporaryReservation, UUID> {

    @Query("""
        SELECT r FROM TemporaryReservation r
        WHERE r.order.id = :orderId
          AND r.status = com.easyeat.order.domain.ReservationStatus.ACTIVE
    """)
    Optional<TemporaryReservation> findActiveByOrderId(@Param("orderId") UUID orderId);

    @Query("""
        SELECT r FROM TemporaryReservation r
        WHERE r.status = com.easyeat.order.domain.ReservationStatus.ACTIVE
          AND r.expiresAt <= :now
    """)
    List<TemporaryReservation> findExpiredActiveReservations(@Param("now") Instant now);

    @Query("""
        SELECT COALESCE(SUM(oi.quantity), 0)
        FROM TemporaryReservation r
        JOIN r.order.items oi
        WHERE oi.dishId = :dishId
          AND r.status = com.easyeat.order.domain.ReservationStatus.ACTIVE
          AND r.expiresAt > :now
    """)
    int sumReservedPortionsByDishId(@Param("dishId") UUID dishId, @Param("now") Instant now);

    Optional<TemporaryReservation> findByIdempotencyKey(String idempotencyKey);
}

