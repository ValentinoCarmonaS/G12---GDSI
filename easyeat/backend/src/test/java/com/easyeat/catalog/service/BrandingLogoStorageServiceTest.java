package com.easyeat.catalog.service;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.domain.RestaurantBranding;
import com.easyeat.catalog.domain.RestaurantLogo;
import com.easyeat.catalog.repository.RestaurantBrandingRepository;
import com.easyeat.catalog.repository.RestaurantLogoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BrandingLogoStorageService Database Binary Storage Tests (AS-9, INV-009)")
class BrandingLogoStorageServiceTest {

    @Mock
    private RestaurantBrandingRepository brandingRepository;

    @Mock
    private RestaurantLogoRepository logoRepository;

    private BrandingLogoStorageService service;
    private UUID establishmentId;
    private User adminUser;

    @BeforeEach
    void setUp() {
        service = new BrandingLogoStorageService(brandingRepository, logoRepository);
        establishmentId = UUID.randomUUID();

        adminUser = new User();
        ReflectionTestUtils.setField(adminUser, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(adminUser, "tenantId", establishmentId);
        ReflectionTestUtils.setField(adminUser, "role", UserRole.ADMINISTRADOR);
    }

    @Test
    @DisplayName("storeLogo persists image bytes in RestaurantLogoRepository and updates logoUrl")
    void shouldStoreLogoInDatabaseAndReturnUrl() {
        byte[] content = new byte[]{10, 20, 30, 40};
        MockMultipartFile file = new MockMultipartFile("file", "brand.png", "image/png", content);

        when(logoRepository.findById(establishmentId)).thenReturn(Optional.empty());
        when(brandingRepository.findById(establishmentId)).thenReturn(Optional.empty());
        when(brandingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String resultUrl = service.storeLogo(establishmentId, file, adminUser);

        assertThat(resultUrl).isEqualTo("/api/v1/establishments/" + establishmentId + "/logo");

        ArgumentCaptor<RestaurantLogo> logoCaptor = ArgumentCaptor.forClass(RestaurantLogo.class);
        verify(logoRepository).save(logoCaptor.capture());

        RestaurantLogo savedLogo = logoCaptor.getValue();
        assertThat(savedLogo.getEstablishmentId()).isEqualTo(establishmentId);
        assertThat(savedLogo.getContentType()).isEqualTo("image/png");
        assertThat(savedLogo.getData()).isEqualTo(content);
        assertThat(savedLogo.getFileSize()).isEqualTo(4L);

        ArgumentCaptor<RestaurantBranding> brandingCaptor = ArgumentCaptor.forClass(RestaurantBranding.class);
        verify(brandingRepository).save(brandingCaptor.capture());
        assertThat(brandingCaptor.getValue().getLogoUrl()).isEqualTo(resultUrl);
    }

    @Test
    @DisplayName("getLogo delegates to RestaurantLogoRepository")
    void shouldRetrieveLogoFromRepository() {
        RestaurantLogo logo = new RestaurantLogo(establishmentId, "logo.png", "image/png", 4L, new byte[]{1, 2});
        when(logoRepository.findById(establishmentId)).thenReturn(Optional.of(logo));

        Optional<RestaurantLogo> retrieved = service.getLogo(establishmentId);

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getData()).containsExactly(1, 2);
    }
}
