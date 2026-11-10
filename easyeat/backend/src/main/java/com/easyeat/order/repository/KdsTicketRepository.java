package com.easyeat.order.repository;

import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KdsTicketRepository extends JpaRepository<KdsTicket, UUID> {

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
}
