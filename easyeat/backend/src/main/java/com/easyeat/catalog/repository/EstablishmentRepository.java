package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EstablishmentRepository extends JpaRepository<Establishment, UUID> {

    @Query(value = "SELECT * FROM establishments e WHERE e.status = 'ACTIVE' AND unaccent(LOWER(e.name)) LIKE unaccent(LOWER(CONCAT('%', :query, '%'))) ORDER BY e.name ASC", nativeQuery = true)
    List<Establishment> searchActiveByName(@Param("query") String query);

    Optional<Establishment> findByIdAndStatus(UUID id, EstablishmentStatus status);

    boolean existsByIdAndStatus(UUID id, EstablishmentStatus status);

    List<Establishment> findAllByStatus(EstablishmentStatus status);
}
