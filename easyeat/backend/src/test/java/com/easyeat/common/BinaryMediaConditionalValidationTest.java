package com.easyeat.common;

import com.easyeat.auth.controller.GlobalExceptionHandler;
import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import com.easyeat.catalog.controller.DinerBrandingController;
import com.easyeat.catalog.service.BrandingLogoStorageService;
import com.easyeat.catalog.service.RestaurantBrandingService;
import com.easyeat.common.http.BinaryMediaETagSupport;
import com.easyeat.common.http.BinaryMediaMetadataProjection;
import com.easyeat.dish.controller.DishPhotoController;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.service.DishPhotoService;
import com.easyeat.profile.controller.ProfileAvatarController;
import com.easyeat.profile.domain.UserPhoto;
import com.easyeat.profile.service.UserPhotoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("BinaryMediaConditionalValidationTest (T056, FR-006, FR-010, SC-006, D-07, Q3)")
class BinaryMediaConditionalValidationTest {

    @Test
    @DisplayName("BinaryMediaETagSupport computes deterministic SHA-256 ETag and matches quoted/unquoted values")
    void shouldComputeDeterministicETagAndMatchTolerantly() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Instant updatedAt = Instant.ofEpochMilli(1764000000000L);

        String etag1 = BinaryMediaETagSupport.computeETag(id, updatedAt, 2048L, "dish.jpg");
        String etag2 = BinaryMediaETagSupport.computeETag(id, updatedAt, 2048L, "dish.jpg");
        String etagDifferent = BinaryMediaETagSupport.computeETag(id, updatedAt.plusMillis(1), 2048L, "dish.jpg");

        assertThat(etag1).matches("^\"[0-9a-f]{64}\"$");
        assertThat(etag1).isEqualTo(etag2);
        assertThat(etag1).isNotEqualTo(etagDifferent);

        assertThat(BinaryMediaETagSupport.matches(etag1, etag1)).isTrue();
        assertThat(BinaryMediaETagSupport.matches(etag1.replace("\"", ""), etag1)).isTrue();
        assertThat(BinaryMediaETagSupport.matches("W/" + etag1, etag1)).isTrue();
        assertThat(BinaryMediaETagSupport.matches("\"other\"", etag1)).isFalse();
        assertThat(BinaryMediaETagSupport.matches(null, etag1)).isFalse();
    }

    @Test
    @DisplayName("GET /api/v1/dishes/{id}/photo returns 304 without reading BYTEA when If-None-Match matches")
    void dishPhotoShouldReturn304WithoutByteaRead() throws Exception {
        DishPhotoService service = mock(DishPhotoService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DishPhotoController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UUID dishId = UUID.randomUUID();
        Instant updatedAt = Instant.ofEpochMilli(1764000100000L);
        BinaryMediaMetadataProjection meta = metadata("dish.jpg", "image/jpeg", 1024L, updatedAt);
        String etag = BinaryMediaETagSupport.computeETag(dishId, updatedAt, 1024L, "dish.jpg");

        when(service.getPhotoMetadata(dishId)).thenReturn(meta);

        mvc.perform(get("/api/v1/dishes/{id}/photo", dishId)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=86400"))
                .andExpect(content().bytes(new byte[0]));

        verify(service, never()).getPhoto(any());
    }

    @Test
    @DisplayName("GET /api/v1/establishments/{id}/logo returns 304 without reading BYTEA and preserves public Cache-Control")
    void logoShouldReturn304WithoutByteaRead() throws Exception {
        RestaurantBrandingService brandingService = mock(RestaurantBrandingService.class);
        BrandingLogoStorageService logoService = mock(BrandingLogoStorageService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DinerBrandingController(brandingService, logoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UUID estId = UUID.randomUUID();
        Instant updatedAt = Instant.ofEpochMilli(1764000200000L);
        BinaryMediaMetadataProjection meta = metadata("logo.png", "image/png", 512L, updatedAt);
        String etag = BinaryMediaETagSupport.computeETag(estId, updatedAt, 512L, "logo.png");

        when(logoService.getLogoMetadata(estId)).thenReturn(Optional.of(meta));

        mvc.perform(get("/api/v1/establishments/{id}/logo", estId)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=86400"))
                .andExpect(content().bytes(new byte[0]));

        verify(logoService, never()).getLogo(any());
    }

    @Test
    @DisplayName("GET /api/v1/profile/users/{userId}/avatar keeps 1-arg signature and returns 304 without BYTEA")
    void staffAvatarShouldKeepSignatureAndReturn304WithoutByteaRead() throws Exception {
        Method method = ProfileAvatarController.class.getMethod("getUserAvatarById", UUID.class);
        assertThat(method.getParameterCount()).isEqualTo(1);

        UserPhotoService photoService = mock(UserPhotoService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProfileAvatarController(photoService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UUID targetUserId = UUID.randomUUID();
        Instant updatedAt = Instant.ofEpochMilli(1764000300000L);
        BinaryMediaMetadataProjection meta = metadata("avatar.jpg", "image/jpeg", 768L, updatedAt);
        String etag = BinaryMediaETagSupport.computeETag(targetUserId, updatedAt, 768L, "avatar.jpg");

        when(photoService.getPhotoMetadata(targetUserId)).thenReturn(Optional.of(meta));

        User staff = new User("enc@easyeat.com", "hash", UserRole.ENCARGADO);
        staff.setId(UUID.randomUUID());
        var auth = new UsernamePasswordAuthenticationToken(
                staff, null, List.of(new SimpleGrantedAuthority("ROLE_ENCARGADO")));

        mvc.perform(get("/api/v1/profile/users/{userId}/avatar", targetUserId)
                        .principal(auth)
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, etag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=3600"))
                .andExpect(content().bytes(new byte[0]));

        verify(photoService, never()).getPhoto(any());
    }

    @Test
    @DisplayName("GET /api/v1/dishes/{id}/photo returns 200 with ETag and new ETag when updatedAt changes")
    void dishPhotoShouldEmitETagOn200AndChangeOnReupload() throws Exception {
        DishPhotoService service = mock(DishPhotoService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DishPhotoController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UUID dishId = UUID.randomUUID();
        byte[] bytes = new byte[]{1, 2, 3};
        DishPhoto photo = new DishPhoto(dishId, "dish.jpg", "image/jpeg", 3L, bytes);
        Instant t1 = Instant.ofEpochMilli(1764000400000L);
        photo.setUpdatedAt(t1);
        String expectedETag = BinaryMediaETagSupport.computeETag(dishId, t1, 3L, "dish.jpg");

        when(service.getPhoto(dishId)).thenReturn(photo);

        mvc.perform(get("/api/v1/dishes/{id}/photo", dishId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, expectedETag))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=86400"))
                .andExpect(content().bytes(bytes));

        String nextETag = BinaryMediaETagSupport.computeETag(dishId, t1.plusSeconds(10), 3L, "dish.jpg");
        assertThat(nextETag).isNotEqualTo(expectedETag);
    }

    private BinaryMediaMetadataProjection metadata(String fileName, String contentType, long fileSize, Instant updatedAt) {
        return new BinaryMediaMetadataProjection() {
            @Override
            public String getFileName() {
                return fileName;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public Long getFileSize() {
                return fileSize;
            }

            @Override
            public Instant getUpdatedAt() {
                return updatedAt;
            }
        };
    }
}
