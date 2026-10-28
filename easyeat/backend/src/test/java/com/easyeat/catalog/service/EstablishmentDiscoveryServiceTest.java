package com.easyeat.catalog.service;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.dto.EstablishmentSummaryResponse;
import com.easyeat.catalog.exception.InvalidSearchQueryException;
import com.easyeat.catalog.repository.EstablishmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstablishmentDiscoveryServiceTest {

    @Mock
    private EstablishmentRepository establishmentRepository;

    private EstablishmentDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new EstablishmentDiscoveryService(establishmentRepository);
    }

    @Nested
    @DisplayName("Query Validation (>= 3 characters threshold)")
    class QueryValidationTests {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t", "\n"})
        @DisplayName("Should throw InvalidSearchQueryException when query is null, empty or only whitespace")
        void shouldThrowExceptionWhenQueryIsEmptyOrBlank(String query) {
            assertThatThrownBy(() -> service.searchEstablishments(query))
                    .isInstanceOf(InvalidSearchQueryException.class)
                    .hasMessageContaining("at least 3");
            verifyNoInteractions(establishmentRepository);
        }

        @ParameterizedTest
        @ValueSource(strings = {"a", "ab", "  a  ", " ca ", "12"})
        @DisplayName("Should throw InvalidSearchQueryException when trimmed query is shorter than 3 characters")
        void shouldThrowExceptionWhenQueryIsShorterThanThreeCharacters(String query) {
            assertThatThrownBy(() -> service.searchEstablishments(query))
                    .isInstanceOf(InvalidSearchQueryException.class)
                    .hasMessageContaining("at least 3");
            verifyNoInteractions(establishmentRepository);
        }
    }

    @Nested
    @DisplayName("Search Execution and Mapping")
    class SearchExecutionTests {

        @Test
        @DisplayName("Should trim query and invoke repository searchActiveByName")
        void shouldTrimQueryAndCallRepository() {
            String rawQuery = "  palermo  ";
            when(establishmentRepository.searchActiveByName("palermo")).thenReturn(Collections.emptyList());

            List<EstablishmentSummaryResponse> result = service.searchEstablishments(rawQuery);

            assertThat(result).isNotNull().isEmpty();
            verify(establishmentRepository, times(1)).searchActiveByName("palermo");
        }

        @Test
        @DisplayName("Should map matching active establishments to EstablishmentSummaryResponse DTOs")
        void shouldMapEntitiesToSummaryResponseDto() {
            UUID id1 = UUID.randomUUID();
            Establishment est1 = new Establishment(
                    id1,
                    "Café Central Palermo",
                    EstablishmentStatus.ACTIVE,
                    LocalTime.of(8, 0),
                    LocalTime.of(23, 30),
                    "America/Argentina/Buenos_Aires"
            );

            UUID id2 = UUID.randomUUID();
            Establishment est2 = new Establishment(
                    id2,
                    "La Farola de Palermo",
                    EstablishmentStatus.ACTIVE,
                    LocalTime.of(11, 0),
                    LocalTime.of(23, 0),
                    "America/Argentina/Buenos_Aires"
            );

            when(establishmentRepository.searchActiveByName("palermo")).thenReturn(List.of(est1, est2));

            List<EstablishmentSummaryResponse> results = service.searchEstablishments("palermo");

            assertThat(results).hasSize(2);

            EstablishmentSummaryResponse res1 = results.get(0);
            assertThat(res1.getId()).isEqualTo(id1);
            assertThat(res1.getName()).isEqualTo("Café Central Palermo");
            assertThat(res1.getStatus()).isEqualTo(EstablishmentStatus.ACTIVE);
            assertThat(res1.getOpeningTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(res1.getClosingTime()).isEqualTo(LocalTime.of(23, 30));
            assertThat(res1.getTimezone()).isEqualTo("America/Argentina/Buenos_Aires");

            EstablishmentSummaryResponse res2 = results.get(1);
            assertThat(res2.getId()).isEqualTo(id2);
            assertThat(res2.getName()).isEqualTo("La Farola de Palermo");
            assertThat(res2.getStatus()).isEqualTo(EstablishmentStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return empty list when no active establishment matches query")
        void shouldReturnEmptyListWhenNoMatches() {
            when(establishmentRepository.searchActiveByName(anyString())).thenReturn(Collections.emptyList());

            List<EstablishmentSummaryResponse> result = service.searchEstablishments("inexistente");

            assertThat(result).isNotNull().isEmpty();
            verify(establishmentRepository).searchActiveByName("inexistente");
        }

        @Test
        @DisplayName("Should filter out any non-active establishment defensively if returned by repository")
        void shouldExcludeInactiveEstablishmentsDefensively() {
            UUID idActive = UUID.randomUUID();
            Establishment active = new Establishment(idActive, "Activo Resto", EstablishmentStatus.ACTIVE, LocalTime.of(10, 0), LocalTime.of(22, 0), "America/Argentina/Buenos_Aires");

            UUID idPaused = UUID.randomUUID();
            Establishment paused = new Establishment(idPaused, "Pausado Resto", EstablishmentStatus.PAUSED, LocalTime.of(10, 0), LocalTime.of(22, 0), "America/Argentina/Buenos_Aires");

            UUID idDisabled = UUID.randomUUID();
            Establishment disabled = new Establishment(idDisabled, "Inhabilitado Resto", EstablishmentStatus.DISABLED, LocalTime.of(10, 0), LocalTime.of(22, 0), "America/Argentina/Buenos_Aires");

            when(establishmentRepository.searchActiveByName("resto")).thenReturn(List.of(active, paused, disabled));

            List<EstablishmentSummaryResponse> results = service.searchEstablishments("resto");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(idActive);
            assertThat(results.get(0).getStatus()).isEqualTo(EstablishmentStatus.ACTIVE);
        }
    }
}

