package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.PhotoUploadResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.PhotoStorageException;
import com.easyeat.dish.repository.DishPhotoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DishPhotoServiceTest {

    @Mock
    private DishRepository dishRepository;

    @Mock
    private DishPhotoRepository dishPhotoRepository;

    @InjectMocks
    private DishPhotoService dishPhotoService;

    private final UUID tenantId = DishTestFixtures.DEFAULT_TENANT_ID;
    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Nested
    @DisplayName("Upload Dish Photo (FR-004, CHF-2.2.1)")
    class UploadPhotoTests {

        @Test
        @DisplayName("Should successfully upload and persist valid JPEG photo <= 5 MB")
        void uploadPhoto_shouldPersistJpeg_whenValid() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            byte[] jpegBytes = DishTestFixtures.createValidJpegBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.jpg", "image/jpeg", jpegBytes
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
            when(dishPhotoRepository.save(any(DishPhoto.class))).thenAnswer(i -> i.getArgument(0));

            PhotoUploadResponse response = dishPhotoService.uploadPhoto(dishId, file);

            assertThat(response).isNotNull();
            assertThat(response.dishId()).isEqualTo(dishId);
            assertThat(response.contentType()).isEqualTo("image/jpeg");
            assertThat(response.fileName()).isEqualTo("dish.jpg");
            assertThat(response.photoUrl()).isEqualTo("/api/v1/dishes/" + dishId + "/photo");

            ArgumentCaptor<DishPhoto> captor = ArgumentCaptor.forClass(DishPhoto.class);
            verify(dishPhotoRepository).save(captor.capture());
            DishPhoto saved = captor.getValue();
            assertThat(saved.getDishId()).isEqualTo(dishId);
            assertThat(saved.getContentType()).isEqualTo("image/jpeg");
            assertThat(saved.getData()).isEqualTo(jpegBytes);
        }

        @Test
        @DisplayName("Should successfully upload and persist valid PNG photo <= 5 MB")
        void uploadPhoto_shouldPersistPng_whenValid() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            byte[] pngBytes = DishTestFixtures.createValidPngBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.png", "image/png", pngBytes
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
            when(dishPhotoRepository.save(any(DishPhoto.class))).thenAnswer(i -> i.getArgument(0));

            PhotoUploadResponse response = dishPhotoService.uploadPhoto(dishId, file);

            assertThat(response).isNotNull();
            assertThat(response.contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("Should successfully upload and persist valid WebP photo <= 5 MB")
        void uploadPhoto_shouldPersistWebp_whenValid() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            byte[] webpBytes = DishTestFixtures.createValidWebpBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.webp", "image/webp", webpBytes
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));
            when(dishPhotoRepository.save(any(DishPhoto.class))).thenAnswer(i -> i.getArgument(0));

            PhotoUploadResponse response = dishPhotoService.uploadPhoto(dishId, file);

            assertThat(response).isNotNull();
            assertThat(response.contentType()).isEqualTo("image/webp");
        }

        @Test
        @DisplayName("Should reject file exceeding 5 MB limit")
        void uploadPhoto_shouldRejectFile_whenExceeds5Mb() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            byte[] oversizedBytes = DishTestFixtures.createOversizedPhotoBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "large.jpg", "image/jpeg", oversizedBytes
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            assertThatThrownBy(() -> dishPhotoService.uploadPhoto(dishId, file))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("5 MB");

            verify(dishPhotoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject unsupported MIME type or invalid image data")
        void uploadPhoto_shouldRejectFile_whenInvalidFormat() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            byte[] textBytes = DishTestFixtures.createInvalidFormatBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.txt", "text/plain", textBytes
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            assertThatThrownBy(() -> dishPhotoService.uploadPhoto(dishId, file))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("JPEG, PNG, WebP");

            verify(dishPhotoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject photo upload when dish is locked in PENDING_PRICE status")
        void uploadPhoto_shouldThrowDishLockedException_whenInPendingPrice() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.PENDING_PRICE);
            dish.setTenantId(tenantId);

            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.jpg", "image/jpeg", DishTestFixtures.createValidJpegBytes()
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            assertThatThrownBy(() -> dishPhotoService.uploadPhoto(dishId, file))
                    .isInstanceOf(DishLockedException.class);

            verify(dishPhotoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw DishNotFoundException when dish does not exist")
        void uploadPhoto_shouldThrowNotFound_whenDishDoesNotExist() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.jpg", "image/jpeg", DishTestFixtures.createValidJpegBytes()
            );

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dishPhotoService.uploadPhoto(dishId, file))
                    .isInstanceOf(DishNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Retrieve Dish Photo (FR-004, CHF-2.2.1)")
    class RetrievePhotoTests {

        @Test
        @DisplayName("Should return DishPhoto when photo exists")
        void getPhoto_shouldReturnDishPhoto_whenFound() {
            DishPhoto photo = new DishPhoto(
                    dishId, "photo.jpg", "image/jpeg", 512L, DishTestFixtures.createValidJpegBytes()
            );

            when(dishPhotoRepository.findByDishId(dishId)).thenReturn(Optional.of(photo));

            DishPhoto result = dishPhotoService.getPhoto(dishId);

            assertThat(result).isNotNull();
            assertThat(result.getDishId()).isEqualTo(dishId);
            assertThat(result.getContentType()).isEqualTo("image/jpeg");
        }

        @Test
        @DisplayName("Should throw DishNotFoundException when photo does not exist")
        void getPhoto_shouldThrowNotFound_whenNotFound() {
            when(dishPhotoRepository.findByDishId(dishId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dishPhotoService.getPhoto(dishId))
                    .isInstanceOf(DishNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Delete Dish Photo (FR-004, CHF-2.2.1)")
    class DeletePhotoTests {

        @Test
        @DisplayName("Should delete photo when dish is in DRAFT status")
        void deletePhoto_shouldDeletePhoto_whenInDraftStatus() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.DRAFT);
            dish.setTenantId(tenantId);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            dishPhotoService.deletePhoto(dishId);

            verify(dishPhotoRepository).deleteByDishId(dishId);
        }

        @Test
        @DisplayName("Should throw DishLockedException when dish is in PENDING_PRICE status")
        void deletePhoto_shouldThrowDishLocked_whenInPendingPrice() {
            Dish dish = DishTestFixtures.createStandardDish();
            dish.setStatus(DishStatus.PENDING_PRICE);
            dish.setTenantId(tenantId);

            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.of(dish));

            assertThatThrownBy(() -> dishPhotoService.deletePhoto(dishId))
                    .isInstanceOf(DishLockedException.class);

            verify(dishPhotoRepository, never()).deleteByDishId(any());
        }

        @Test
        @DisplayName("Should throw DishNotFoundException when dish does not exist")
        void deletePhoto_shouldThrowNotFound_whenDishDoesNotExist() {
            when(dishRepository.findByIdAndTenantId(dishId, tenantId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dishPhotoService.deletePhoto(dishId))
                    .isInstanceOf(DishNotFoundException.class);
        }
    }
}
