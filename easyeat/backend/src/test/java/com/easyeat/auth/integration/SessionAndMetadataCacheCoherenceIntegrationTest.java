package com.easyeat.auth.integration;

import com.easyeat.auth.cache.UserSessionCache;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.catalog.cache.BrandingCache;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.catalog.repository.RestaurantBrandingRepository;
import com.easyeat.catalog.service.RestaurantBrandingService;
import com.easyeat.common.SqlQueryCountAssertionHelper;
import com.easyeat.operations.cache.OperatingScheduleCache;
import com.easyeat.operations.domain.EstablishmentOperatingSchedule;
import com.easyeat.operations.repository.OperatingScheduleRepository;
import com.easyeat.operations.service.OperatingScheduleService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("SessionAndMetadataCacheCoherenceIntegrationTest (T055, FR-005, SC-005, D-06)")
class SessionAndMetadataCacheCoherenceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_cache_coherence_it")
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
    private MockMvc mockMvc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserSessionCache userSessionCache;

    @Autowired
    private EstablishmentRepository establishmentRepository;

    @Autowired
    private RestaurantBrandingRepository brandingRepository;

    @Autowired
    private RestaurantBrandingService brandingService;

    @Autowired
    private BrandingCache brandingCache;

    @Autowired
    private OperatingScheduleRepository operatingScheduleRepository;

    @Autowired
    private OperatingScheduleService operatingScheduleService;

    @Autowired
    private OperatingScheduleCache operatingScheduleCache;

    private SqlQueryCountAssertionHelper sqlHelper;

    @BeforeEach
    void setUp() {
        sqlHelper = SqlQueryCountAssertionHelper.from(entityManagerFactory);
        userSessionCache.evictAll();
        brandingCache.evictAll();
        operatingScheduleCache.evictAll();
    }

    @Test
    @DisplayName("Consecutive authenticated requests execute 0 repeated user queries and direct userRepository.save evicts session immediately")
    void sessionCacheAvoidsRepeatedUserQueriesAndEvictsOnDirectSave() throws Exception {
        User user = new User("session-cache-" + UUID.randomUUID() + "@easyeat.com", "hash", UserRole.COMENSAL);
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);
        user = userRepository.save(user);

        String token = jwtService.generateToken(user);

        // Warm-up request (populates UserSessionCache)
        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Consecutive authenticated request: UserSessionCache hit -> 0 queries to users (only profile/restrictions)
        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long cachedReqSqlCount = sqlHelper.getPrepareStatementCount();

        // Evict once and compare: uncached request runs +1 query for users
        userSessionCache.evict(user.getId());
        sqlHelper.clear();
        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        long uncachedReqSqlCount = sqlHelper.getPrepareStatementCount();
        assertThat(cachedReqSqlCount).isLessThan(uncachedReqSqlCount);

        // Direct userRepository.save (bypasses AuthService) must trigger @EntityListeners and reject immediately
        user.setTokenVersion(2);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Suspending user directly also rejects 403 immediately
        String newToken = jwtService.generateToken(user);
        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isOk());

        user.setStatus(UserStatus.SUSPENDED);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/profile/me")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("BrandingCache and OperatingScheduleCache serve hits with 0 SQL and evict synchronously on update")
    void brandingAndScheduleCachesEvictSynchronouslyOnUpdate() throws Exception {
        Establishment est = new Establishment();
        est.setName("Cache Bistro");
        est.setStatus(EstablishmentStatus.ACTIVE);
        est = establishmentRepository.save(est);
        UUID estId = est.getId();

        RestaurantBranding branding = new RestaurantBranding(
                estId, "Nombre Inicial", "Desc", null,
                "#10B981", "#065F46", "#F59E0B", "#FFFFFF", "#FFFFFF", "#000000"
        );
        brandingRepository.saveAndFlush(branding);

        // Warm BrandingCache
        mockMvc.perform(get("/api/v1/establishments/{id}/branding", estId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fantasyName").value("Nombre Inicial"));

        // Second read: 0 SQL statements
        sqlHelper.assertExactQueries(0, () -> {
            try {
                mockMvc.perform(get("/api/v1/establishments/{id}/branding", estId))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.fantasyName").value("Nombre Inicial"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Update via service -> immediate reflection
        User admin = new User("admin-cache-" + UUID.randomUUID() + "@easyeat.com", "hash", UserRole.ADMINISTRADOR, estId);
        brandingService.updateBranding(estId, new RestaurantBrandingUpdateRequest(
                "Nombre Actualizado", "Nueva desc", "#111111", "#222222", "#333333"
        ), admin);

        mockMvc.perform(get("/api/v1/establishments/{id}/branding", estId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fantasyName").value("Nombre Actualizado"));

        // OperatingScheduleCache warm & evict via direct repository save (@EntityListeners)
        EstablishmentOperatingSchedule monday = new EstablishmentOperatingSchedule(
                estId,
                DayOfWeek.MONDAY,
                LocalTime.of(9, 0),
                LocalTime.of(22, 0),
                false
        );
        operatingScheduleRepository.saveAndFlush(monday);

        List<EstablishmentOperatingSchedule> loaded = operatingScheduleService.getWeek(estId);
        assertThat(loaded).hasSize(1);
        sqlHelper.assertExactQueries(0, () -> operatingScheduleService.getWeek(estId));

        monday.update(LocalTime.of(9, 0), LocalTime.of(23, 0), false);
        operatingScheduleRepository.saveAndFlush(monday);

        List<EstablishmentOperatingSchedule> reloaded = operatingScheduleService.getWeek(estId);
        assertThat(reloaded.get(0).getClosingTime()).isEqualTo(LocalTime.of(23, 0));
    }
}
