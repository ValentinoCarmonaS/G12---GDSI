package com.easyeat.profile.repository;

import com.easyeat.profile.domain.DietaryRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DietaryRestrictionRepository extends JpaRepository<DietaryRestriction, String> {
    List<DietaryRestriction> findAllByOrderByCategoryAscNameAsc();
}
