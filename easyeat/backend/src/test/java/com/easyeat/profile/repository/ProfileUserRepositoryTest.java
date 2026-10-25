package com.easyeat.profile.repository;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Transactional
class ProfileUserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_profile_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private ProfileUserRepository profileUserRepository;

    @Test
    @DisplayName("Should find user by emailVerificationTokenHash")
    void findByEmailVerificationTokenHash() {
        String email = "diner-" + System.currentTimeMillis() + "@test.com";
        String tokenHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";


        User user = new User(email, "hashed_pass", UserRole.COMENSAL);
        user.setFirstName("Martín");
        user.setLastName("Gómez");
        user.setPhone("+54 9 11 9876-5432");
        user.setPendingEmail("pending@test.com");
        user.setEmailVerificationTokenHash(tokenHash);
        user.setEmailTokenExpiresAt(Instant.now().plusSeconds(86400));
        user.setEmailVerificationLastSentAt(Instant.now());

        profileUserRepository.saveAndFlush(user);

        Optional<User> found = profileUserRepository.findByEmailVerificationTokenHash(tokenHash);
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(email);
        assertThat(found.get().getFirstName()).isEqualTo("Martín");
        assertThat(found.get().getLastName()).isEqualTo("Gómez");

        Optional<User> notFound = profileUserRepository.findByEmailVerificationTokenHash("non_existent_hash");
        assertThat(notFound).isEmpty();
    }

    @Test
    @DisplayName("Should verify case-insensitive pending email uniqueness")
    void existsByPendingEmailIgnoreCase() {
        String email = "diner-pending-" + System.currentTimeMillis() + "@test.com";
        User user = new User(email, "hashed_pass", UserRole.COMENSAL);
        user.setPendingEmail("unique.pending@test.com");

        profileUserRepository.saveAndFlush(user);

        assertThat(profileUserRepository.existsByPendingEmailIgnoreCase("unique.pending@test.com")).isTrue();
        assertThat(profileUserRepository.existsByPendingEmailIgnoreCase("UNIQUE.PENDING@TEST.COM")).isTrue();
        assertThat(profileUserRepository.existsByPendingEmailIgnoreCase("other.pending@test.com")).isFalse();
    }

    @Test
    @DisplayName("Should verify case-insensitive email existence")
    void existsByEmailIgnoreCase() {
        String email = "case-test-" + System.currentTimeMillis() + "@test.com";
        User user = new User(email, "hashed_pass", UserRole.COMENSAL);
        profileUserRepository.saveAndFlush(user);

        assertThat(profileUserRepository.existsByEmailIgnoreCase(email.toUpperCase())).isTrue();
        assertThat(profileUserRepository.existsByEmailIgnoreCase("unregistered@test.com")).isFalse();
    }
}
