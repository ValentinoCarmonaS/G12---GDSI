package com.easyeat.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Establishment JPA Entity Tests (T008)")
class EstablishmentEntityTest {

    @Test
    @DisplayName("Should initialize with standard default values")
    void shouldInitializeWithDefaultValues() {
        Establishment establishment = new Establishment();

        assertThat(establishment.getStatus()).isEqualTo(EstablishmentStatus.ACTIVE);
        assertThat(establishment.getOpeningTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(establishment.getClosingTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(establishment.getTimezone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(establishment.getCreatedAt()).isNotNull();
        assertThat(establishment.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should correctly mutate and access all entity fields")
    void shouldMutateAndAccessAllFields() {
        Establishment establishment = new Establishment();
        UUID id = UUID.randomUUID();
        LocalTime opening = LocalTime.of(8, 30);
        LocalTime closing = LocalTime.of(23, 45);
        Instant now = Instant.now();

        establishment.setId(id);
        establishment.setName("Café Central Palermo");
        establishment.setStatus(EstablishmentStatus.PAUSED);
        establishment.setOpeningTime(opening);
        establishment.setClosingTime(closing);
        establishment.setTimezone("America/Argentina/Buenos_Aires");
        establishment.setCreatedAt(now);
        establishment.setUpdatedAt(now);

        assertThat(establishment.getId()).isEqualTo(id);
        assertThat(establishment.getName()).isEqualTo("Café Central Palermo");
        assertThat(establishment.getStatus()).isEqualTo(EstablishmentStatus.PAUSED);
        assertThat(establishment.getOpeningTime()).isEqualTo(opening);
        assertThat(establishment.getClosingTime()).isEqualTo(closing);
        assertThat(establishment.getTimezone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(establishment.getCreatedAt()).isEqualTo(now);
        assertThat(establishment.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should support parameterized constructor with explicit parameters")
    void shouldSupportParameterizedConstructor() {
        UUID id = UUID.randomUUID();
        LocalTime opening = LocalTime.of(12, 0);
        LocalTime closing = LocalTime.of(22, 0);

        Establishment establishment = new Establishment(
                id,
                "Bistró San Telmo",
                EstablishmentStatus.DISABLED,
                opening,
                closing,
                "America/Argentina/Buenos_Aires"
        );

        assertThat(establishment.getId()).isEqualTo(id);
        assertThat(establishment.getName()).isEqualTo("Bistró San Telmo");
        assertThat(establishment.getStatus()).isEqualTo(EstablishmentStatus.DISABLED);
        assertThat(establishment.getOpeningTime()).isEqualTo(opening);
        assertThat(establishment.getClosingTime()).isEqualTo(closing);
        assertThat(establishment.getTimezone()).isEqualTo("America/Argentina/Buenos_Aires");
    }

    @Test
    @DisplayName("Pre-persist and pre-update callbacks should set or refresh timestamps")
    void shouldUpdateTimestampsOnLifecycleCallbacks() throws InterruptedException {
        Establishment establishment = new Establishment();
        establishment.onCreate();
        Instant createdAt = establishment.getCreatedAt();
        Instant updatedAt = establishment.getUpdatedAt();

        assertThat(createdAt).isNotNull();
        assertThat(updatedAt).isNotNull();

        Thread.sleep(10);
        establishment.onUpdate();
        assertThat(establishment.getUpdatedAt()).isAfterOrEqualTo(updatedAt);
    }
}
