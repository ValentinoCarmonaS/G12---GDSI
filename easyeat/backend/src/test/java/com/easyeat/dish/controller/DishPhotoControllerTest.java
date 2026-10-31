package com.easyeat.dish.controller;

import com.easyeat.dish.DishTestFixtures;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.dto.PhotoUploadResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.PhotoStorageException;
import com.easyeat.dish.service.DishPhotoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DishPhotoController.class)
class DishPhotoControllerTest extends DishControllerTestBase {

    @MockBean
    private DishPhotoService dishPhotoService;

    private final UUID dishId = DishTestFixtures.DEFAULT_DISH_ID;

    @Nested
    @DisplayName("POST /api/v1/dishes/{id}/photo - Upload Photo (FR-004, CHF-2.2.1)")
    class UploadPhotoContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 200 OK and PhotoUploadResponse when upload is valid")
        void uploadPhoto_shouldReturn200_whenValid() throws Exception {
            byte[] jpegBytes = DishTestFixtures.createValidJpegBytes();
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.jpg", "image/jpeg", jpegBytes
            );

            PhotoUploadResponse response = new PhotoUploadResponse(
                    dishId,
                    "dish.jpg",
                    "image/jpeg",
                    jpegBytes.length,
                    "/api/v1/dishes/" + dishId + "/photo"
            );

            when(dishPhotoService.uploadPhoto(eq(dishId), any())).thenReturn(response);

            mockMvc.perform(multipart("/api/v1/dishes/{id}/photo", dishId)
                            .file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dishId").value(dishId.toString()))
                    .andExpect(jsonPath("$.fileName").value("dish.jpg"))
                    .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                    .andExpect(jsonPath("$.photoUrl").value("/api/v1/dishes/" + dishId + "/photo"));
        }

        @Test
        @WithMockUser(roles = "COMENSAL")
        @DisplayName("Should return 403 Forbidden when user is not a CHEF")
        void uploadPhoto_shouldReturn403_whenNotChef() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.jpg", "image/jpeg", DishTestFixtures.createValidJpegBytes()
            );

            mockMvc.perform(multipart("/api/v1/dishes/{id}/photo", dishId)
                            .file(file))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should return 401 Unauthorized when unauthenticated")
        void uploadPhoto_shouldReturn401_whenUnauthenticated() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.jpg", "image/jpeg", DishTestFixtures.createValidJpegBytes()
            );

            mockMvc.perform(multipart("/api/v1/dishes/{id}/photo", dishId)
                            .file(file))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 400 Bad Request with ProblemDetail when photo exceeds size or invalid format")
        void uploadPhoto_shouldReturn400_whenStorageError() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.txt", "text/plain", DishTestFixtures.createInvalidFormatBytes()
            );

            when(dishPhotoService.uploadPhoto(eq(dishId), any()))
                    .thenThrow(new PhotoStorageException("Formato no soportado. Debe ser JPEG, PNG o WebP."));

            mockMvc.perform(multipart("/api/v1/dishes/{id}/photo", dishId)
                            .file(file))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/photo-storage-error"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when dish is locked in PENDING_PRICE status")
        void uploadPhoto_shouldReturn409_whenDishLocked() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "dish.jpg", "image/jpeg", DishTestFixtures.createValidJpegBytes()
            );

            when(dishPhotoService.uploadPhoto(eq(dishId), any()))
                    .thenThrow(new DishLockedException(dishId, "PENDING_PRICE"));

            mockMvc.perform(multipart("/api/v1/dishes/{id}/photo", dishId)
                            .file(file))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-locked"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/dishes/{id}/photo - Retrieve Photo (FR-004, CHF-2.2.1)")
    class RetrievePhotoContractTests {

        @Test
        @DisplayName("Should return 200 OK with binary stream and headers when photo exists")
        void getPhoto_shouldReturn200_whenExists() throws Exception {
            byte[] jpegBytes = DishTestFixtures.createValidJpegBytes();
            DishPhoto photo = new DishPhoto(
                    dishId, "dish.jpg", "image/jpeg", (long) jpegBytes.length, jpegBytes
            );

            when(dishPhotoService.getPhoto(dishId)).thenReturn(photo);

            mockMvc.perform(get("/api/v1/dishes/{id}/photo", dishId))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=86400"))
                    .andExpect(content().bytes(jpegBytes));
        }

        @Test
        @DisplayName("Should return 404 Not Found when photo does not exist")
        void getPhoto_shouldReturn404_whenNotFound() throws Exception {
            when(dishPhotoService.getPhoto(dishId)).thenThrow(new DishNotFoundException(dishId));

            mockMvc.perform(get("/api/v1/dishes/{id}/photo", dishId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-not-found"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/dishes/{id}/photo - Delete Photo (FR-004, CHF-2.2.1)")
    class DeletePhotoContractTests {

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 204 No Content when photo is successfully deleted")
        void deletePhoto_shouldReturn204_whenSuccessful() throws Exception {
            doNothing().when(dishPhotoService).deletePhoto(dishId);

            mockMvc.perform(delete("/api/v1/dishes/{id}/photo", dishId))
                    .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(roles = "CHEF")
        @DisplayName("Should return 409 Conflict when dish is in PENDING_PRICE status")
        void deletePhoto_shouldReturn409_whenLocked() throws Exception {
            doThrow(new DishLockedException(dishId, "PENDING_PRICE"))
                    .when(dishPhotoService).deletePhoto(dishId);

            mockMvc.perform(delete("/api/v1/dishes/{id}/photo", dishId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.type").value("https://easyeat.com/errors/dish-locked"));
        }
    }
}
