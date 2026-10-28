package com.easyeat.dish.controller;

import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.dto.PhotoUploadResponse;
import com.easyeat.dish.service.DishPhotoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dishes/{id}/photo")
public class DishPhotoController {

    private final DishPhotoService dishPhotoService;

    public DishPhotoController(DishPhotoService dishPhotoService) {
        this.dishPhotoService = dishPhotoService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<PhotoUploadResponse> uploadDishPhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) {
        PhotoUploadResponse response = dishPhotoService.uploadPhoto(id, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<byte[]> getDishPhoto(@PathVariable UUID id) {
        DishPhoto photo = dishPhotoService.getPhoto(id);
        String contentType = photo.getContentType() != null ? photo.getContentType() : "image/jpeg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(photo.getData());
    }

    @DeleteMapping
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<Void> deleteDishPhoto(@PathVariable UUID id) {
        dishPhotoService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }
}
