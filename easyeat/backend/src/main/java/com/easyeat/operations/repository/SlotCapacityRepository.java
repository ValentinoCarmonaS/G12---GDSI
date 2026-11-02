package com.easyeat.operations.repository;

import com.easyeat.operations.domain.EstablishmentSlotCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
