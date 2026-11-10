package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.dto.RestaurantBrandingResponse;
import com.easyeat.catalog.dto.RestaurantBrandingUpdateRequest;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.catalog.repository.RestaurantBrandingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantBrandingService Business Logic and Establishment Sync Tests")
class RestaurantBrandingServiceTest {

    @Mock
    private RestaurantBrandingRepository brandingRepository;

    @Mock
    private EstablishmentRepository establishmentRepository;

    private BrandingColorContrastCalculator contrastCalculator;
    private RestaurantBrandingService service;

    private UUID establishmentId;
    private User adminUser;
    private Establishment establishment;

    @BeforeEach
    void setUp() {
        contrastCalculator = new BrandingColorContrastCalculator();
        service = new RestaurantBrandingService(brandingRepository, contrastCalculator, establishmentRepository);

        establishmentId = UUID.randomUUID();
        adminUser = new User();
        ReflectionTestUtils.setField(adminUser, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(adminUser, "tenantId", establishmentId);
        ReflectionTestUtils.setField(adminUser, "role", UserRole.ADMINISTRADOR);

        establishment = new Establishment();
        establishment.setId(establishmentId);
        establishment.setName("Nombre Original");
        establishment.setStatus(EstablishmentStatus.ACTIVE);
    }

    @Test
    @DisplayName("updateBranding updates fantasyName in branding and synchronizes Establishment.name")
    void shouldUpdateBrandingAndSyncEstablishmentName() {
        when(brandingRepository.findById(establishmentId)).thenReturn(Optional.empty());
        when(brandingRepository.save(any(RestaurantBranding.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

        RestaurantBrandingUpdateRequest request = new RestaurantBrandingUpdateRequest(
                "Café Moderno Palermo",
                "Cafetería de especialidad",
                "#2563EB",
                "#1E3A8A",
                "#F59E0B"
        );

        RestaurantBrandingResponse response = service.updateBranding(establishmentId, request, adminUser);

        assertThat(response.fantasyName()).isEqualTo("Café Moderno Palermo");

        ArgumentCaptor<Establishment> estCaptor = ArgumentCaptor.forClass(Establishment.class);
        verify(establishmentRepository).save(estCaptor.capture());
        assertThat(estCaptor.getValue().getName()).isEqualTo("Café Moderno Palermo");
    }

    @Test
    @DisplayName("getAdminBranding defaults fantasyName to existing establishment name when unconfigured")
    void shouldDefaultFantasyNameToEstablishmentName() {
        when(brandingRepository.findById(establishmentId)).thenReturn(Optional.empty());
        when(establishmentRepository.findById(establishmentId)).thenReturn(Optional.of(establishment));

        RestaurantBrandingResponse response = service.getAdminBranding(establishmentId, adminUser);

        assertThat(response.fantasyName()).isEqualTo("Nombre Original");
    }
}
