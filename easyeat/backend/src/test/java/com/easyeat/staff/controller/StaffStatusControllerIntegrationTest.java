package com.easyeat.staff.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtService;
import com.easyeat.staff.dto.StaffAccountStatus;
import com.easyeat.staff.dto.StaffStatusUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StaffStatusControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private UUID tenantId;
    private User admin1;
    private User chef;
    private String admin1Token;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        tenantId = UUID.randomUUID();

        admin1 = new User();
        admin1.setTenantId(tenantId);
        admin1.setFullName("Admin One");
        admin1.setEmail("admin1@easyeat.com");
        admin1.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin1.setRole(UserRole.ADMINISTRADOR);
        admin1.setStatus(UserStatus.ACTIVE);
        admin1 = userRepository.save(admin1);
        admin1Token = jwtService.generateToken(admin1);

        chef = new User();
        chef.setTenantId(tenantId);
        chef.setFullName("Chef One");
        chef.setEmail("chef1@easyeat.com");
        chef.setPasswordHash(passwordEncoder.encode("Password123!"));
        chef.setRole(UserRole.CHEF);
        chef.setStatus(UserStatus.ACTIVE);
        chef = userRepository.save(chef);
    }

    @Test
    void updateStatus_returns200_whenSuspendingChef() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/staff/{id}/status", chef.getId())
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void updateStatus_returns404_whenStaffNotFound() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/staff/{id}/status", UUID.randomUUID())
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_returns404_or403_whenStaffBelongsToAnotherTenant() throws Exception {
        User otherTenantChef = new User();
        otherTenantChef.setTenantId(UUID.randomUUID());
        otherTenantChef.setFullName("Other Chef");
        otherTenantChef.setEmail("other@easyeat.com");
        otherTenantChef.setPasswordHash(passwordEncoder.encode("Password123!"));
        otherTenantChef.setRole(UserRole.CHEF);
        otherTenantChef.setStatus(UserStatus.ACTIVE);
        otherTenantChef = userRepository.save(otherTenantChef);

        mockMvc.perform(patch("/api/v1/admin/staff/{id}/status", otherTenantChef.getId())
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_returns409_whenSuspendingLastActiveAdmin() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/staff/{id}/status", admin1.getId())
                        .header("Authorization", "Bearer " + admin1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED))))
                .andExpect(status().isConflict());
    }

    @Test
    void updateStatus_preventsMutualSuspensionRaceCondition() throws Exception {
        User admin2 = new User();
        admin2.setTenantId(tenantId);
        admin2.setFullName("Admin Two");
        admin2.setEmail("admin2@easyeat.com");
        admin2.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin2.setRole(UserRole.ADMINISTRADOR);
        admin2.setStatus(UserStatus.ACTIVE);
        admin2 = userRepository.save(admin2);
        String admin2Token = jwtService.generateToken(admin2);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        UUID admin2Id = admin2.getId();
        UUID admin1Id = admin1.getId();

        Future<Integer> res1 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(patch("/api/v1/admin/staff/{id}/status", admin2Id)
                            .header("Authorization", "Bearer " + admin1Token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED))))
                    .andReturn().getResponse().getStatus();
        });

        Future<Integer> res2 = executor.submit(() -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(patch("/api/v1/admin/staff/{id}/status", admin1Id)
                            .header("Authorization", "Bearer " + admin2Token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new StaffStatusUpdateRequest(StaffAccountStatus.SUSPENDED))))
                    .andReturn().getResponse().getStatus();
        });

        readyLatch.await();
        startLatch.countDown();

        int status1 = res1.get();
        int status2 = res2.get();
        executor.shutdown();

        // Exactly one must succeed (200) and the other must fail with 409 Conflict
        assertEquals(200 + 409, status1 + status2);
    }
}
