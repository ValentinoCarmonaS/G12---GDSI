package com.easyeat.profile.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Flyway V10 Migration and Demographics/Avatar Schema Integration Test (T002)")
class FlywayMigrationV10Test {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_v10_test")
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
    private JdbcTemplate jdbcTemplate;

    private UUID createTestUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, password_hash, role, status, full_name, must_change_password, token_version, created_at, updated_at) " +
                        "VALUES (?, ?, 'hash', 'COMENSAL', 'ACTIVE', 'Test User', false, 0, NOW(), NOW())",
                userId, email.toLowerCase()
        );
        return userId;
    }

    @Test
    @DisplayName("V10 migration should add demographics columns to users table and create user_photos table")
    void verifyV10SchemaEvolution() {
        // Verify users columns
        List<String> userColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'users'",
                String.class
        );
        assertThat(userColumns).contains("age", "gender", "address");

        // Verify user_photos columns
        List<String> photoColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'user_photos'",
                String.class
        );
        assertThat(photoColumns).contains(
                "user_id",
                "file_name",
                "content_type",
                "file_size",
                "data",
                "created_at",
                "updated_at"
        );
    }

    @Test
    @DisplayName("CHECK constraint chk_users_age_range should enforce age between 14 and 120 or NULL")
    void verifyUserAgeCheckConstraint() {
        UUID userValid = createTestUser("age_valid_" + UUID.randomUUID() + "@test.com");
        jdbcTemplate.update("UPDATE users SET age = 14 WHERE id = ?", userValid);
        jdbcTemplate.update("UPDATE users SET age = 120 WHERE id = ?", userValid);
        jdbcTemplate.update("UPDATE users SET age = NULL WHERE id = ?", userValid);

        UUID userYoung = createTestUser("age_young_" + UUID.randomUUID() + "@test.com");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE users SET age = 13 WHERE id = ?", userYoung))
                .isInstanceOf(DataIntegrityViolationException.class);

        UUID userOld = createTestUser("age_old_" + UUID.randomUUID() + "@test.com");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE users SET age = 121 WHERE id = ?", userOld))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK constraint chk_users_gender_valid should accept closed taxonomy values or NULL")
    void verifyUserGenderCheckConstraint() {
        UUID userValid = createTestUser("gender_valid_" + UUID.randomUUID() + "@test.com");
        for (String validGender : List.of("FEMALE", "MALE", "NON_BINARY", "PREFER_NOT_TO_SAY",
                "Femenino", "Masculino", "No binario", "Prefiero no responder")) {
            int rows = jdbcTemplate.update("UPDATE users SET gender = ? WHERE id = ?", validGender, userValid);
            assertThat(rows).isEqualTo(1);
        }

        jdbcTemplate.update("UPDATE users SET gender = NULL WHERE id = ?", userValid);

        UUID userInvalid = createTestUser("gender_invalid_" + UUID.randomUUID() + "@test.com");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE users SET gender = 'INVALID_GENDER' WHERE id = ?", userInvalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK constraint chk_users_address_length should enforce max 255 characters or NULL")
    void verifyUserAddressCheckConstraint() {
        UUID userValid = createTestUser("address_valid_" + UUID.randomUUID() + "@test.com");
        String maxAddress = "A".repeat(255);
        int rows = jdbcTemplate.update("UPDATE users SET address = ? WHERE id = ?", maxAddress, userValid);
        assertThat(rows).isEqualTo(1);

        jdbcTemplate.update("UPDATE users SET address = NULL WHERE id = ?", userValid);

        UUID userTooLong = createTestUser("address_toolong_" + UUID.randomUUID() + "@test.com");
        String tooLongAddress = "A".repeat(256);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE users SET address = ? WHERE id = ?", tooLongAddress, userTooLong))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK constraints and FK on user_photos table should enforce MIME types, size boundaries and cascade delete")
    void verifyUserPhotoConstraintsAndCascade() {
        UUID userId = createTestUser("photo_user_" + UUID.randomUUID() + "@test.com");
        byte[] sampleData = new byte[]{1, 2, 3, 4};

        // Valid insert
        int inserted = jdbcTemplate.update(
                "INSERT INTO user_photos (user_id, file_name, content_type, file_size, data) VALUES (?, ?, ?, ?, ?)",
                userId, "avatar.png", "image/png", 4L, sampleData
        );
        assertThat(inserted).isEqualTo(1);

        // Invalid MIME type
        UUID userInvalidMime = createTestUser("photo_mime_" + UUID.randomUUID() + "@test.com");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_photos (user_id, file_name, content_type, file_size, data) VALUES (?, ?, ?, ?, ?)",
                userInvalidMime, "doc.pdf", "application/pdf", 4L, sampleData
        )).isInstanceOf(DataIntegrityViolationException.class);

        // Invalid size: 0 bytes
        UUID userZeroSize = createTestUser("photo_zero_" + UUID.randomUUID() + "@test.com");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_photos (user_id, file_name, content_type, file_size, data) VALUES (?, ?, ?, ?, ?)",
                userZeroSize, "empty.png", "image/png", 0L, sampleData
        )).isInstanceOf(DataIntegrityViolationException.class);

        // Invalid size: > 5242880 bytes
        UUID userOversize = createTestUser("photo_oversize_" + UUID.randomUUID() + "@test.com");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_photos (user_id, file_name, content_type, file_size, data) VALUES (?, ?, ?, ?, ?)",
                userOversize, "huge.png", "image/png", 5242881L, sampleData
        )).isInstanceOf(DataIntegrityViolationException.class);

        // Cascade delete when user is deleted
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        Integer photoCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_photos WHERE user_id = ?",
                Integer.class,
                userId
        );
        assertThat(photoCount).isEqualTo(0);
    }
}

