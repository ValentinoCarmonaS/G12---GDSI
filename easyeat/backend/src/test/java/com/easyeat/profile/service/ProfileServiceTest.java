package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.auth.domain.UserStatus;
import com.easyeat.profile.dto.PersonalDataRequest;
import com.easyeat.profile.dto.ProfileResponse;
import com.easyeat.profile.repository.ProfileUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService Unit Tests (T010, COM-1.4.1)")
class ProfileServiceTest {

    @Mock
    private ProfileUserRepository profileUserRepository;

    @Mock
    private DietaryRestrictionService dietaryRestrictionService;

    @InjectMocks
    private ProfileService profileService;

    private User sampleDiner;

    @BeforeEach
    void setUp() {
        sampleDiner = new User("diner@example.com", "hashed_pwd", UserRole.COMENSAL);
        sampleDiner.setId(UUID.randomUUID());
        sampleDiner.setFirstName("Valentino");
        sampleDiner.setLastName("Carmona");
        sampleDiner.setFullName("Valentino Carmona");
        sampleDiner.setPhone("+54 9 11 2345-6789");
        sampleDiner.setStatus(UserStatus.ACTIVE);
    }

    @Nested
    @DisplayName("getProfile tests")
    class GetProfileTests {

        @Test
        @DisplayName("Returns populated ProfileResponse for authenticated diner")
        void getProfile_returnsPopulatedProfileResponse() {
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));

            ProfileResponse response = profileService.getProfile(sampleDiner);

            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo(sampleDiner.getId());
            assertThat(response.email()).isEqualTo("diner@example.com");
            assertThat(response.firstName()).isEqualTo("Valentino");
            assertThat(response.lastName()).isEqualTo("Carmona");
            assertThat(response.fullName()).isEqualTo("Valentino Carmona");
            assertThat(response.phone()).isEqualTo("+54 9 11 2345-6789");
            assertThat(response.pendingEmail()).isNull();
            assertThat(response.emailVerificationPending()).isFalse();
            assertThat(response.dietaryRestrictions()).isNotNull();
        }

        @Test
        @DisplayName("Returns pending email flag when user has pending verification")
        void getProfile_returnsPendingEmailVerificationFlag() {
            sampleDiner.setPendingEmail("new.email@example.com");
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));

            ProfileResponse response = profileService.getProfile(sampleDiner);

            assertThat(response.pendingEmail()).isEqualTo("new.email@example.com");
            assertThat(response.emailVerificationPending()).isTrue();
        }
    }

    @Nested
    @DisplayName("updatePersonalData tests")
    class UpdatePersonalDataTests {

        @Test
        @DisplayName("Updates names and phone with automatic full_name synchronization")
        void updatePersonalData_updatesFieldsAndSynchronizesFullName() {
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PersonalDataRequest request = new PersonalDataRequest("Agustin", "Fraga", "+5491198765432");
            ProfileResponse response = profileService.updatePersonalData(sampleDiner, request);

            assertThat(response.firstName()).isEqualTo("Agustin");
            assertThat(response.lastName()).isEqualTo("Fraga");
            assertThat(response.fullName()).isEqualTo("Agustin Fraga");
            assertThat(response.phone()).isEqualTo("+5491198765432");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(profileUserRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getFirstName()).isEqualTo("Agustin");
            assertThat(savedUser.getLastName()).isEqualTo("Fraga");
            assertThat(savedUser.getFullName()).isEqualTo("Agustin Fraga");
            assertThat(savedUser.getPhone()).isEqualTo("+5491198765432");
        }

        @Test
        @DisplayName("Trims whitespace from input fields and handles optional phone clearing")
        void updatePersonalData_trimsWhitespaceAndClearsOptionalPhone() {
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            PersonalDataRequest request = new PersonalDataRequest("  Valen  ", "  Carmona  ", "   ");
            ProfileResponse response = profileService.updatePersonalData(sampleDiner, request);

            assertThat(response.firstName()).isEqualTo("Valen");
            assertThat(response.lastName()).isEqualTo("Carmona");
            assertThat(response.fullName()).isEqualTo("Valen Carmona");
            assertThat(response.phone()).isNull();
        }

        @Test
        @DisplayName("Rejects non-alphabetic names with numbers or symbols")
        void updatePersonalData_rejectsNonAlphabeticNames() {
            PersonalDataRequest invalidFirstName = new PersonalDataRequest("Valen123", "Carmona", null);
            assertThatThrownBy(() -> profileService.updatePersonalData(sampleDiner, invalidFirstName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nombre");

            PersonalDataRequest invalidLastName = new PersonalDataRequest("Valen", "Carmona#$", null);
            assertThatThrownBy(() -> profileService.updatePersonalData(sampleDiner, invalidLastName))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apellido");
        }

        @Test
        @DisplayName("Rejects blank or whitespace-only names")
        void updatePersonalData_rejectsBlankNames() {
            PersonalDataRequest blankFirst = new PersonalDataRequest("   ", "Carmona", null);
            assertThatThrownBy(() -> profileService.updatePersonalData(sampleDiner, blankFirst))
                    .isInstanceOf(IllegalArgumentException.class);

            PersonalDataRequest blankLast = new PersonalDataRequest("Valen", "   ", null);
            assertThatThrownBy(() -> profileService.updatePersonalData(sampleDiner, blankLast))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Rejects malformed phone formats")
        void updatePersonalData_rejectsInvalidPhone() {
            PersonalDataRequest invalidPhone = new PersonalDataRequest("Valen", "Carmona", "123");
            assertThatThrownBy(() -> profileService.updatePersonalData(sampleDiner, invalidPhone))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("teléfono");

            PersonalDataRequest alphaPhone = new PersonalDataRequest("Valen", "Carmona", "not-a-phone");
            assertThatThrownBy(() -> profileService.updatePersonalData(sampleDiner, alphaPhone))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Preserves immutable security and tenant credentials")
        void updatePersonalData_preservesSecurityAndTenantFields() {
            when(profileUserRepository.findById(sampleDiner.getId())).thenReturn(Optional.of(sampleDiner));
            when(profileUserRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            String originalEmail = sampleDiner.getEmail();
            String originalHash = sampleDiner.getPasswordHash();
            UserRole originalRole = sampleDiner.getRole();
            Integer originalTokenVersion = sampleDiner.getTokenVersion();

            PersonalDataRequest request = new PersonalDataRequest("Nuevo", "Nombre", "+541144556677");
            profileService.updatePersonalData(sampleDiner, request);

            assertThat(sampleDiner.getEmail()).isEqualTo(originalEmail);
            assertThat(sampleDiner.getPasswordHash()).isEqualTo(originalHash);
            assertThat(sampleDiner.getRole()).isEqualTo(originalRole);
            assertThat(sampleDiner.getTokenVersion()).isEqualTo(originalTokenVersion);
        }
    }
}
