package com.easyeat.operations.repository;

import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OperatingScheduleRepository extends JpaRepository<EstablishmentOperatingSchedule, UUID> {

    List<EstablishmentOperatingSchedule> findByEstablishmentId(UUID establishmentId);

    Optional<EstablishmentOperatingSchedule> findByEstablishmentIdAndDayOfWeek(UUID establishmentId, DayOfWeek dayOfWeek);
}
