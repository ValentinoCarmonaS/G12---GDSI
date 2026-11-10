package com.easyeat.order.repository;

import com.easyeat.order.domain.EstablishmentKdsSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EstablishmentKdsSettingsRepository extends JpaRepository<EstablishmentKdsSettings, UUID> {
}
