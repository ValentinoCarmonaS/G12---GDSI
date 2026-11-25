package com.easyeat.dish.controller;

import com.easyeat.common.http.BinaryMediaETagSupport;
import com.easyeat.common.http.BinaryMediaMetadataProjection;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.dto.PhotoUploadResponse;
import com.easyeat.dish.service.DishPhotoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<byte[]> getDishPhoto(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            BinaryMediaMetadataProjection metadata = dishPhotoService.getPhotoMetadata(id);
            String etag = BinaryMediaETagSupport.computeETag(id, metadata);
            if (BinaryMediaETagSupport.matches(ifNoneMatch, etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .header(HttpHeaders.ETAG, etag)
                        .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                        .build();
            }
        }

        DishPhoto photo = dishPhotoService.getPhoto(id);
        String contentType = photo.getContentType() != null ? photo.getContentType() : "image/jpeg";
        String etag = BinaryMediaETagSupport.computeETag(id, photo.getUpdatedAt(), photo.getFileSize(), photo.getFileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.ETAG, etag)
                .body(photo.getData());
    }

    @DeleteMapping
    @PreAuthorize("hasRole('CHEF')")
    public ResponseEntity<Void> deleteDishPhoto(@PathVariable UUID id) {
        dishPhotoService.deletePhoto(id);
        return ResponseEntity.noContent().build();
    }
}
