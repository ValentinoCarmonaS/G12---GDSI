package com.easyeat.profile.service;

import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.exception.PhotoStorageException;
import com.easyeat.profile.domain.UserPhoto;
import com.easyeat.profile.dto.AvatarUploadResponse;
import com.easyeat.profile.repository.UserPhotoRepository;
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
@DisplayName("UserPhotoService Unit Test (T008, INV-4)")
class UserPhotoServiceTest {

    @Mock
    private UserPhotoRepository userPhotoRepository;

    @InjectMocks
    private UserPhotoService userPhotoService;

    private final UUID userId = UUID.randomUUID();

    @Nested
    @DisplayName("Upload Avatar Photo Tests")
    class UploadAvatarTests {

        @Test
        @DisplayName("Should successfully upload and persist valid JPEG avatar <= 5 MB")
        void uploadPhoto_shouldPersistJpeg_whenValid() {
            byte[] jpegBytes = DishTestFixtures.createValidJpegBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "avatar.jpg", "image/jpeg", jpegBytes
            );

            when(userPhotoRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(userPhotoRepository.save(any(UserPhoto.class))).thenAnswer(i -> i.getArgument(0));

            AvatarUploadResponse response = userPhotoService.uploadPhoto(userId, file);

            assertThat(response).isNotNull();
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.contentType()).isEqualTo("image/jpeg");
            assertThat(response.fileName()).isEqualTo("avatar.jpg");
            assertThat(response.avatarUrl()).isEqualTo("/api/v1/profile/avatar");

            ArgumentCaptor<UserPhoto> captor = ArgumentCaptor.forClass(UserPhoto.class);
            verify(userPhotoRepository).save(captor.capture());
            UserPhoto saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(userId);
            assertThat(saved.getContentType()).isEqualTo("image/jpeg");
            assertThat(saved.getData()).isEqualTo(jpegBytes);
        }

        @Test
        @DisplayName("Should successfully upload and persist valid PNG avatar <= 5 MB")
        void uploadPhoto_shouldPersistPng_whenValid() {
            byte[] pngBytes = DishTestFixtures.createValidPngBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "avatar.png", "image/png", pngBytes
            );

            when(userPhotoRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(userPhotoRepository.save(any(UserPhoto.class))).thenAnswer(i -> i.getArgument(0));

            AvatarUploadResponse response = userPhotoService.uploadPhoto(userId, file);

            assertThat(response).isNotNull();
            assertThat(response.contentType()).isEqualTo("image/png");
            assertThat(response.fileName()).isEqualTo("avatar.png");
        }

        @Test
        @DisplayName("Should successfully upload and persist valid WebP avatar <= 5 MB")
        void uploadPhoto_shouldPersistWebp_whenValid() {
            byte[] webpBytes = DishTestFixtures.createValidWebpBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "avatar.webp", "image/webp", webpBytes
            );

            when(userPhotoRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(userPhotoRepository.save(any(UserPhoto.class))).thenAnswer(i -> i.getArgument(0));

            AvatarUploadResponse response = userPhotoService.uploadPhoto(userId, file);

            assertThat(response).isNotNull();
            assertThat(response.contentType()).isEqualTo("image/webp");
            assertThat(response.fileName()).isEqualTo("avatar.webp");
        }

        @Test
        @DisplayName("Should accept avatar file at exact boundary of 5.242.880 bytes (5 MB)")
        void uploadPhoto_shouldAcceptFile_atExact5MbBoundary() {
            int exact5Mb = 5 * 1024 * 1024;
            byte[] boundaryBytes = new byte[exact5Mb];
            // Set valid JPEG magic bytes
            boundaryBytes[0] = (byte) 0xFF;
            boundaryBytes[1] = (byte) 0xD8;
            boundaryBytes[2] = (byte) 0xFF;
            boundaryBytes[3] = (byte) 0xE0;

            MockMultipartFile file = new MockMultipartFile(
                    "file", "boundary.jpg", "image/jpeg", boundaryBytes
            );

            when(userPhotoRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(userPhotoRepository.save(any(UserPhoto.class))).thenAnswer(i -> i.getArgument(0));

            AvatarUploadResponse response = userPhotoService.uploadPhoto(userId, file);

            assertThat(response).isNotNull();
            assertThat(response.fileSize()).isEqualTo((long) exact5Mb);
        }

        @Test
        @DisplayName("Should reject avatar file exceeding 5 MB limit (5.242.881 bytes)")
        void uploadPhoto_shouldRejectFile_whenExceeds5Mb() {
            byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1];
            MockMultipartFile file = new MockMultipartFile(
                    "file", "huge.jpg", "image/jpeg", oversizedBytes
            );

            assertThatThrownBy(() -> userPhotoService.uploadPhoto(userId, file))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("5 MB");

            verify(userPhotoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject empty or zero-byte avatar file")
        void uploadPhoto_shouldRejectFile_whenEmpty() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.jpg", "image/jpeg", new byte[0]
            );

            assertThatThrownBy(() -> userPhotoService.uploadPhoto(userId, emptyFile))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("vacío");

            assertThatThrownBy(() -> userPhotoService.uploadPhoto(userId, null))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("vacío");

            verify(userPhotoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject unsupported MIME type or invalid image magic bytes")
        void uploadPhoto_shouldRejectFile_whenInvalidFormat() {
            byte[] textBytes = DishTestFixtures.createInvalidFormatBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "document.pdf", "application/pdf", textBytes
            );

            assertThatThrownBy(() -> userPhotoService.uploadPhoto(userId, file))
                    .isInstanceOf(PhotoStorageException.class)
                    .hasMessageContaining("JPEG, PNG o WebP");

            verify(userPhotoRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should replace existing photo when user already has an avatar")
        void uploadPhoto_shouldReplaceExistingPhoto() {
            UserPhoto existingPhoto = new UserPhoto(
                    userId, "old.jpg", "image/jpeg", 100L, new byte[]{1, 2, 3, 4}
            );

            byte[] newBytes = DishTestFixtures.createValidPngBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "new.png", "image/png", newBytes
            );

            when(userPhotoRepository.findByUserId(userId)).thenReturn(Optional.of(existingPhoto));
            when(userPhotoRepository.save(any(UserPhoto.class))).thenAnswer(i -> i.getArgument(0));

            AvatarUploadResponse response = userPhotoService.uploadPhoto(userId, file);

            assertThat(response).isNotNull();
            assertThat(response.fileName()).isEqualTo("new.png");
            assertThat(existingPhoto.getFileName()).isEqualTo("new.png");
            assertThat(existingPhoto.getContentType()).isEqualTo("image/png");
            verify(userPhotoRepository).save(existingPhoto);
        }
    }

    @Nested
    @DisplayName("Delete and Get Avatar Tests")
    class DeleteAndGetAvatarTests {

        @Test
        @DisplayName("Should delete photo by userId")
        void deletePhoto_shouldInvokeRepositoryDelete() {
            userPhotoService.deletePhoto(userId);
            verify(userPhotoRepository).deleteByUserId(userId);
        }

        @Test
        @DisplayName("Should retrieve photo by userId when present")
        void getPhoto_shouldReturnPhoto_whenPresent() {
            UserPhoto photo = new UserPhoto(userId, "avatar.png", "image/png", 50L, new byte[]{1, 2});
            when(userPhotoRepository.findByUserId(userId)).thenReturn(Optional.of(photo));

            Optional<UserPhoto> result = userPhotoService.getPhoto(userId);

            assertThat(result).isPresent();
            assertThat(result.get().getFileName()).isEqualTo("avatar.png");
        }
    }
}
