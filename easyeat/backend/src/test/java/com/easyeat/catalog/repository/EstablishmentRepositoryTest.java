package com.easyeat.catalog.repository;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("EstablishmentRepository Database Integration Tests (T009)")
class EstablishmentRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_repo_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Test
    @DisplayName("Should find active establishments ignoring case and diacritics")
    void shouldFindActiveByNameWithUnaccentAndCaseInsensitivity() {
        // Query without accent
        List<Establishment> resultWithoutAccent = establishmentRepository.searchActiveByName("cafe");
        assertThat(resultWithoutAccent).isNotEmpty();
        assertThat(resultWithoutAccent).extracting(Establishment::getName)
                .contains("Café Central Palermo");

        // Query with accent
        List<Establishment> resultWithAccent = establishmentRepository.searchActiveByName("café");
        assertThat(resultWithAccent).isNotEmpty();
        assertThat(resultWithAccent).extracting(Establishment::getName)
                .contains("Café Central Palermo");

        // Query uppercase
        List<Establishment> resultUppercase = establishmentRepository.searchActiveByName("BELLA");
        assertThat(resultUppercase).isNotEmpty();
        assertThat(resultUppercase).extracting(Establishment::getName)
                .contains("Trattoria Bella Vista");
    }

    @Test
    @DisplayName("Should exclude PAUSED or DISABLED establishments from searchActiveByName")
    void shouldExcludeInactiveEstablishmentsFromSearch() {
        // 'Bistró San Telmo' is seeded as PAUSED
        List<Establishment> results = establishmentRepository.searchActiveByName("Bistró");
        assertThat(results).isEmpty();

        List<Establishment> resultsWithoutAccent = establishmentRepository.searchActiveByName("bistro");
        assertThat(resultsWithoutAccent).isEmpty();
    }

    @Test
    @DisplayName("Should find establishment by id and status correctly")
    void shouldFindByIdAndStatus() {
        UUID cafeId = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
        UUID bistroId = UUID.fromString("c2eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");

        Optional<Establishment> activeCafe = establishmentRepository.findByIdAndStatus(cafeId, EstablishmentStatus.ACTIVE);
        assertThat(activeCafe).isPresent();
        assertThat(activeCafe.get().getName()).isEqualTo("Café Central Palermo");

        Optional<Establishment> pausedCafe = establishmentRepository.findByIdAndStatus(cafeId, EstablishmentStatus.PAUSED);
        assertThat(pausedCafe).isEmpty();

        Optional<Establishment> pausedBistro = establishmentRepository.findByIdAndStatus(bistroId, EstablishmentStatus.PAUSED);
        assertThat(pausedBistro).isPresent();
        assertThat(pausedBistro.get().getName()).isEqualTo("Bistró San Telmo");
    }
}
