package com.easyeat.auth.integration;

import com.easyeat.auth.domain.PasswordResetToken;
import com.easyeat.auth.domain.TokenStatus;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.dto.PasswordResetConfirmRequest;
import com.easyeat.auth.repository.PasswordResetTokenRepository;
import com.easyeat.auth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ConcurrentResetConsumptionTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyeat_test")
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
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Concurrent reset confirm requests: exactly 1 request succeeds (200 OK) and all others fail (400 Bad Request)")
    void testConcurrentResetConfirmAtomicSingleUse() throws Exception {
        int concurrency = 10;
        User user = new User("concurrent@easyeat.com", passwordEncoder.encode("InitialPass123"), UserRole.COMENSAL);
        user = userRepository.save(user);

        String rawToken = "concurrent-race-condition-token-123456";
        String tokenHash = sha256(rawToken);

        PasswordResetToken token = new PasswordResetToken(user, tokenHash, Instant.now().plus(15, ChronoUnit.MINUTES));
        tokenRepository.save(token);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch readyLatch = new CountDownLatch(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    PasswordResetConfirmRequest request = new PasswordResetConfirmRequest(rawToken, "NewPass" + index + "Aa1");
                    MvcResult result = mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    statusCodes.add(status);
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 400) {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire all concurrent threads simultaneously

        executor.shutdown();
        boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
        assertThat(finished).isTrue();

        assertThat(successCount.get()).as("Exactly one concurrent request must succeed").isEqualTo(1);
        assertThat(failureCount.get()).as("All other concurrent requests must fail with 400").isEqualTo(concurrency - 1);

        // Verify token in DB is CONSUMED
        PasswordResetToken updatedToken = tokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(updatedToken.getStatus()).isEqualTo(TokenStatus.CONSUMED);
        assertThat(updatedToken.getConsumedAt()).isNotNull();
    }
}
