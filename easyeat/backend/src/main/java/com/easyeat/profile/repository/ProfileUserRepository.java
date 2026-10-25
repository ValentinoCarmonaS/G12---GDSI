package com.easyeat.profile.repository;

import com.easyeat.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Isolated repository interface for diner profile queries, email verification token lookups,
 * and email uniqueness checks across active and pending addresses.
 */
@Repository
public interface ProfileUserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by the SHA-256 hash of their active email verification token.
     *
     * @param tokenHash SHA-256 hash string
     * @return Optional containing the User if token hash matches
     */
    Optional<User> findByEmailVerificationTokenHash(String tokenHash);

    /**
     * Checks if a pending email address is already requested by any user, ignoring case.
     *
     * @param pendingEmail email address to check
     * @return true if pending email is already assigned
     */
    boolean existsByPendingEmailIgnoreCase(String pendingEmail);

    /**
     * Checks if an email address is registered as primary email by any user, ignoring case.
     *
     * @param email email address to check
     * @return true if email exists
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Finds an active user by primary email address, ignoring case.
     *
     * @param email primary email
     * @return Optional containing User if found
     */
    Optional<User> findByEmailIgnoreCase(String email);
}
