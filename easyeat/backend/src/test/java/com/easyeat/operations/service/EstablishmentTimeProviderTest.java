package com.easyeat.operations.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EstablishmentTimeProvider: Establishment.timezone is the only time authority")
class EstablishmentTimeProviderTest {

    private static final UUID ID = UUID.randomUUID();
    /** 2026-11-10T19:00:00Z is 16:00 in Buenos Aires (UTC-3) and 20:00 in Madrid (UTC+1). */
    private static final Instant NOW = Instant.parse("2026-11-10T19:00:00Z");

    @Mock
    private EstablishmentRepository establishmentRepository;

    private EstablishmentTimeProvider providerWith(String timezone) {
        var establishment = new Establishment(
                ID, "Cafe", EstablishmentStatus.ACTIVE, LocalTime.of(11, 0), LocalTime.of(23, 0), timezone);
        when(establishmentRepository.findById(ID)).thenReturn(Optional.of(establishment));
        return new EstablishmentTimeProvider(Clock.fixed(NOW, ZoneOffset.UTC), establishmentRepository);
    }

    @Test
    @DisplayName("EC-014: resolves now in the establishment's own timezone")
    void resolvesNowInEstablishmentTimezone() {
        ZonedDateTime buenosAires = providerWith("America/Argentina/Buenos_Aires").nowAt(ID);

        assertThat(buenosAires.getZone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(buenosAires.toLocalTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(buenosAires.toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("EC-014: the same instant reads differently in another timezone")
    void sameInstantDiffersByTimezone() {
        ZonedDateTime madrid = providerWith("Europe/Madrid").nowAt(ID);

        assertThat(madrid.toLocalTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(madrid.toLocalDate().toString()).isEqualTo("2026-11-10");
    }

    @Test
    @DisplayName("zoneOf returns the establishment's ZoneId")
    void zoneOfReturnsZone() {
        assertThat(providerWith("America/Argentina/Buenos_Aires").zoneOf(ID))
                .isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    @DisplayName("an unknown establishment is reported, never defaulted")
    void unknownEstablishmentThrows() {
        when(establishmentRepository.findById(ID)).thenReturn(Optional.empty());
        var provider = new EstablishmentTimeProvider(Clock.fixed(NOW, ZoneOffset.UTC), establishmentRepository);

        assertThatThrownBy(() -> provider.nowAt(ID)).isInstanceOf(EstablishmentNotFoundException.class);
        assertThatThrownBy(() -> provider.zoneOf(ID)).isInstanceOf(EstablishmentNotFoundException.class);
    }

    @Test
    @DisplayName("EC-018: no public method accepts a client-supplied time or clock")
    void noPublicMethodAcceptsClientTime() {
        Set<Class<?>> timeTypes = Set.of(Instant.class, ZonedDateTime.class, java.time.LocalDateTime.class,
                LocalTime.class, java.time.OffsetDateTime.class, Clock.class, long.class, Long.class);

        for (Method method : EstablishmentTimeProvider.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertThat(Arrays.asList(method.getParameterTypes()))
                        .as(method.getName()).doesNotContainAnyElementsOf(timeTypes);
            }
        }
    }
}
