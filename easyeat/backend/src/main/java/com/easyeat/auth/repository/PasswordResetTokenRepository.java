package com.easyeat.auth.repository;

import com.easyeat.auth.domain.PasswordResetToken;
import com.easyeat.auth.domain.TokenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.status = :revokedStatus WHERE t.user.id = :userId AND t.status = :pendingStatus")
    int revokeAllPendingForUser(
            @Param("userId") UUID userId,
            @Param("pendingStatus") TokenStatus pendingStatus,
            @Param("revokedStatus") TokenStatus revokedStatus);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.status = 'CONSUMED', t.consumedAt = :now WHERE t.tokenHash = :tokenHash AND t.status = 'PENDING' AND t.expiresAt > :now")
    int consumeToken(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
