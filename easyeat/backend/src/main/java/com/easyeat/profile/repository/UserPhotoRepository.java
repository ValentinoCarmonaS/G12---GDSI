package com.easyeat.profile.repository;

import com.easyeat.common.http.BinaryMediaMetadataProjection;
import com.easyeat.profile.domain.UserPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPhotoRepository extends JpaRepository<UserPhoto, UUID> {

    Optional<UserPhoto> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    @Query("SELECT up.userId FROM UserPhoto up WHERE up.userId IN :userIds")
    List<UUID> findUserIdsWithPhoto(@Param("userIds") Collection<UUID> userIds);

    @Query("SELECT up.fileName AS fileName, up.contentType AS contentType, up.fileSize AS fileSize, up.updatedAt AS updatedAt " +
            "FROM UserPhoto up WHERE up.userId = :userId")
    Optional<BinaryMediaMetadataProjection> findMetadataByUserId(@Param("userId") UUID userId);

    void deleteByUserId(UUID userId);
}

