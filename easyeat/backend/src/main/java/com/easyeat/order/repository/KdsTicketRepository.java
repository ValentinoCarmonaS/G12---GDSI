package com.easyeat.order.repository;

import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
public interface KdsTicketRepository extends JpaRepository<KdsTicket, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM KdsTicket k WHERE k.id = :id")
    Optional<KdsTicket> findByIdForUpdate(@Param("id") UUID id);

    Optional<KdsTicket> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    Page<KdsTicket> findByEstablishmentId(UUID establishmentId, Pageable pageable);

    Page<KdsTicket> findByEstablishmentIdAndStatus(
            UUID establishmentId,
            KdsTicketStatus status,
            Pageable pageable
    );

    List<KdsTicket> findByEstablishmentIdAndPickupDateOrderByTimeSlotStartAsc(
            UUID establishmentId,
            LocalDate pickupDate
    );

    long countByEstablishmentIdAndStatus(UUID establishmentId, KdsTicketStatus status);

    @Query("""
        SELECT COUNT(k) FROM KdsTicket k
        WHERE k.establishment.id = :establishmentId
          AND k.status = :status
          AND k.pickupDate = :pickupDate
    """)
    long countByEstablishmentIdAndStatusAndPickupDate(
            @Param("establishmentId") UUID establishmentId,
            @Param("status") KdsTicketStatus status,
            @Param("pickupDate") LocalDate pickupDate
    );

    @Query("""
        SELECT MAX(k.ticketNumber) FROM KdsTicket k
        WHERE k.establishment.id = :establishmentId
    """)
    Optional<Integer> findMaxTicketNumberByEstablishmentId(@Param("establishmentId") UUID establishmentId);

    @Query("""
        SELECT k FROM KdsTicket k
        WHERE k.establishment.id = :establishmentId
          AND k.status IN (com.easyeat.order.domain.KdsTicketStatus.QUEUED, com.easyeat.order.domain.KdsTicketStatus.IN_PREPARATION)
          AND k.pickupDate <= :today
        ORDER BY k.pickupDate ASC, k.timeSlotStart ASC, k.queuedAt ASC, k.id ASC
    """)
    List<KdsTicket> findActiveQueueTickets(
            @Param("establishmentId") UUID establishmentId,
            @Param("today") LocalDate today
    );

    @Query("""
        SELECT COUNT(k) FROM KdsTicket k
        WHERE k.establishment.id = :establishmentId
          AND k.status = com.easyeat.order.domain.KdsTicketStatus.QUEUED
          AND k.pickupDate <= :today
          AND (
              k.pickupDate < :pickupDate
              OR (k.pickupDate = :pickupDate AND k.timeSlotStart < :timeSlotStart)
              OR (k.pickupDate = :pickupDate AND k.timeSlotStart = :timeSlotStart AND k.queuedAt < :queuedAt)
              OR (k.pickupDate = :pickupDate AND k.timeSlotStart = :timeSlotStart AND k.queuedAt = :queuedAt AND k.id < :ticketId)
          )
    """)
    long countQueuedTicketsAhead(
            @Param("establishmentId") UUID establishmentId,
            @Param("pickupDate") LocalDate pickupDate,
            @Param("timeSlotStart") LocalTime timeSlotStart,
            @Param("queuedAt") Instant queuedAt,
            @Param("ticketId") UUID ticketId,
            @Param("today") LocalDate today
    );
}
