package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.profile.domain.DietaryCategory;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.dto.DietaryPreferencesRequest;
import com.easyeat.profile.dto.DietaryRestrictionItemDto;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DietaryRestrictionService Unit Tests (T028, COM-1.4.3)")
class DietaryRestrictionServiceTest {

    @Mock
    private DietaryRestrictionRepository dietaryRestrictionRepository;

    @Mock
    private UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    @InjectMocks
    private DietaryRestrictionService dietaryRestrictionService;

    private User sampleDiner;
    private DietaryRestriction celiac;
    private DietaryRestriction vegan;
    private DietaryRestriction peanuts;

    @BeforeEach
    void setUp() {
        sampleDiner = new User("diner@example.com", "pwdHash", UserRole.COMENSAL);
        sampleDiner.setId(UUID.randomUUID());

        celiac = new DietaryRestriction("CELIAC", "Sin TACC / Celíaco", DietaryCategory.DIETARY_LIFESTYLE, "Excluye gluten");
        vegan = new DietaryRestriction("VEGAN", "Vegano", DietaryCategory.DIETARY_LIFESTYLE, "Excluye origen animal");
        peanuts = new DietaryRestriction("PEANUTS", "Maní", DietaryCategory.COMMON_ALLERGEN, "Contiene maní");
    }

    @Nested
    @DisplayName("getDietaryTaxonomy tests")
    class GetDietaryTaxonomyTests {

        @Test
        @DisplayName("Returns full standardized platform taxonomy mapped to DTOs")
        void getDietaryTaxonomy_returnsOrderedDtoList() {
            when(dietaryRestrictionRepository.findAllByOrderByCategoryAscNameAsc())
                    .thenReturn(List.of(celiac, vegan, peanuts));

            List<DietaryRestrictionItemDto> result = dietaryRestrictionService.getDietaryTaxonomy();

            assertThat(result).hasSize(3);
            assertThat(result.get(0).code()).isEqualTo("CELIAC");
            assertThat(result.get(0).category()).isEqualTo("DIETARY_LIFESTYLE");
            assertThat(result.get(1).code()).isEqualTo("VEGAN");
            assertThat(result.get(2).code()).isEqualTo("PEANUTS");
            assertThat(result.get(2).category()).isEqualTo("COMMON_ALLERGEN");
        }

        @Test
        @DisplayName("Returns empty list when taxonomy table is empty")
        void getDietaryTaxonomy_emptyListWhenNoRestrictions() {
            when(dietaryRestrictionRepository.findAllByOrderByCategoryAscNameAsc()).thenReturn(List.of());

            List<DietaryRestrictionItemDto> result = dietaryRestrictionService.getDietaryTaxonomy();

            assertThat(result).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("getUserPreferences tests")
    class GetUserPreferencesTests {

        @Test
        @DisplayName("Returns populated restrictions for user with configured preferences")
        void getUserPreferences_returnsConfiguredPreferences() {
            UserDietaryRestriction udr1 = new UserDietaryRestriction(sampleDiner.getId(), "CELIAC");
            UserDietaryRestriction udr2 = new UserDietaryRestriction(sampleDiner.getId(), "PEANUTS");

            when(userDietaryRestrictionRepository.findByIdUserId(sampleDiner.getId()))
                    .thenReturn(List.of(udr1, udr2));
            when(dietaryRestrictionRepository.findAllById(any()))
                    .thenReturn(List.of(celiac, peanuts));

            List<DietaryRestrictionItemDto> result = dietaryRestrictionService.getUserPreferences(sampleDiner.getId());

            assertThat(result).hasSize(2);
            assertThat(result).extracting(DietaryRestrictionItemDto::code)
                    .containsExactlyInAnyOrder("CELIAC", "PEANUTS");
        }

        @Test
        @DisplayName("Returns empty list when user has zero restrictions configured")
        void getUserPreferences_returnsEmptyListForZeroRestrictions() {
            when(userDietaryRestrictionRepository.findByIdUserId(sampleDiner.getId()))
                    .thenReturn(List.of());

            List<DietaryRestrictionItemDto> result = dietaryRestrictionService.getUserPreferences(sampleDiner.getId());

            assertThat(result).isNotNull().isEmpty();
            verify(dietaryRestrictionRepository, never()).findAllById(any());
        }
    }

    @Nested
    @DisplayName("updateUserPreferences tests")
    class UpdateUserPreferencesTests {

        @Test
        @DisplayName("Atomically replaces existing restrictions with new valid selection")
        void updateUserPreferences_successAtomicReplacement() {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of("CELIAC", "PEANUTS"));

            when(dietaryRestrictionRepository.findAllById(any()))
                    .thenReturn(List.of(celiac, peanuts));

            List<DietaryRestrictionItemDto> result = dietaryRestrictionService.updateUserPreferences(sampleDiner, request);

            verify(userDietaryRestrictionRepository).deleteByUserId(sampleDiner.getId());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserDietaryRestriction>> captor = ArgumentCaptor.forClass(List.class);
            verify(userDietaryRestrictionRepository).saveAll(captor.capture());

            List<UserDietaryRestriction> saved = captor.getValue();
            assertThat(saved).hasSize(2);
            assertThat(saved).extracting(udr -> udr.getId().getRestrictionCode())
                    .containsExactlyInAnyOrder("CELIAC", "PEANUTS");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(DietaryRestrictionItemDto::code)
                    .containsExactlyInAnyOrder("CELIAC", "PEANUTS");
        }

        @Test
        @DisplayName("Clears all user restrictions when request contains empty list")
        void updateUserPreferences_clearsPreferencesWhenEmptyList() {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of());

            List<DietaryRestrictionItemDto> result = dietaryRestrictionService.updateUserPreferences(sampleDiner, request);

            verify(userDietaryRestrictionRepository).deleteByUserId(sampleDiner.getId());
            verify(userDietaryRestrictionRepository, never()).saveAll(any());
            assertThat(result).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("Throws DietaryTaxonomyException and does not alter persistence on invalid restriction code")
        void updateUserPreferences_throwsExceptionOnInvalidCode() {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of("CELIAC", "INVALID_CODE"));

            // Repository only finds CELIAC, not INVALID_CODE
            when(dietaryRestrictionRepository.findAllById(any()))
                    .thenReturn(List.of(celiac));

            assertThatThrownBy(() -> dietaryRestrictionService.updateUserPreferences(sampleDiner, request))
                    .isInstanceOf(DietaryTaxonomyException.class)
                    .hasMessageContaining("INVALID_CODE");

            verify(userDietaryRestrictionRepository, never()).deleteByUserId(any());
            verify(userDietaryRestrictionRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Deduplicates identical restriction codes in request before persistence")
        void updateUserPreferences_deduplicatesCodes() {
            DietaryPreferencesRequest request = new DietaryPreferencesRequest(List.of("CELIAC", "CELIAC"));

            when(dietaryRestrictionRepository.findAllById(any()))
                    .thenReturn(List.of(celiac));

            List<DietaryRestrictionItemDto> result = dietaryRestrictionService.updateUserPreferences(sampleDiner, request);

            verify(userDietaryRestrictionRepository).deleteByUserId(sampleDiner.getId());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<UserDietaryRestriction>> captor = ArgumentCaptor.forClass(List.class);
            verify(userDietaryRestrictionRepository).saveAll(captor.capture());

            List<UserDietaryRestriction> saved = captor.getValue();
            assertThat(saved).hasSize(1);
            assertThat(saved.get(0).getId().getRestrictionCode()).isEqualTo("CELIAC");
            assertThat(result).hasSize(1);
        }
    }
}
