package com.easyeat.dish.controller;

import com.easyeat.dish.dto.PhotoUploadResponse;
import com.easyeat.dish.service.DishPhotoService;
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
        throw new UnsupportedOperationException("T029 pending implementation");
    }

    @GetMapping
    public ResponseEntity<byte[]> getDishPhoto(@PathVariable UUID id) {
        throw new UnsupportedOperationException("T029 pending implementation");
    }

    @DeleteMapping
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<Void> deleteDishPhoto(@PathVariable UUID id) {
        throw new UnsupportedOperationException("T029 pending implementation");
    }
}
