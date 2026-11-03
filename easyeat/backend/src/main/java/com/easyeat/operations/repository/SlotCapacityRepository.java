package com.easyeat.operations.repository;

import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlotCapacityRepository extends JpaRepository<EstablishmentSlotCapacity, UUID> {

    List<EstablishmentSlotCapacity> findByEstablishmentIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
            UUID establishmentId, LocalDate fromDate, LocalDate toDate);

    Optional<EstablishmentSlotCapacity> findByEstablishmentIdAndSlotDateAndStartTime(
            UUID establishmentId, LocalDate slotDate, LocalTime startTime);

    /**
     * Counts one approved order against the slot only while it is below the capacity (INV-2).
     * The check and the increment are a single statement, so concurrent approvals of the last
     * place cannot both succeed (EC-016). Returns the number of rows changed: 0 means sold out,
     * capacity 0 or no row at all.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EstablishmentSlotCapacity c SET c.approvedOrdersCount = c.approvedOrdersCount + 1 "
            + "WHERE c.establishmentId = :establishmentId AND c.slotDate = :slotDate "
            + "AND c.startTime = :startTime AND c.approvedOrdersCount < c.maxCapacity")
    int incrementIfBelowCapacity(@Param("establishmentId") UUID establishmentId,
                                 @Param("slotDate") LocalDate slotDate,
                                 @Param("startTime") LocalTime startTime);
}
