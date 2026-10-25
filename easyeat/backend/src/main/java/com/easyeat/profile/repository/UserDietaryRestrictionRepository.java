package com.easyeat.profile.repository;

import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.domain.UserDietaryRestrictionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserDietaryRestrictionRepository extends JpaRepository<UserDietaryRestriction, UserDietaryRestrictionId> {

    List<UserDietaryRestriction> findByIdUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM UserDietaryRestriction udr WHERE udr.id.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
