package com.easyeat.profile.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.common.http.BinaryMediaETagSupport;
import com.easyeat.common.http.BinaryMediaMetadataProjection;
import com.easyeat.profile.domain.UserPhoto;
import com.easyeat.profile.dto.AvatarUploadResponse;
import com.easyeat.profile.service.UserPhotoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileAvatarController {

    private final UserPhotoService userPhotoService;

    public ProfileAvatarController(UserPhotoService userPhotoService) {
        this.userPhotoService = userPhotoService;
    }

    @PostMapping("/avatar")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<AvatarUploadResponse> uploadAvatar(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file
    ) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AvatarUploadResponse response = userPhotoService.uploadPhoto(user.getId(), file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/avatar")
    public ResponseEntity<?> getAvatar(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return buildAvatarResponse(user.getId(), "private, max-age=86400");
    }

    @GetMapping("/users/{userId}/avatar")
    @PreAuthorize("hasAnyRole('ENCARGADO', 'CHEF', 'ADMINISTRADOR')")
    public ResponseEntity<?> getUserAvatarById(@PathVariable UUID userId) {
        return buildAvatarResponse(userId, "private, max-age=3600");
    }

    @DeleteMapping("/avatar")
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<Void> removeAvatar(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userPhotoService.deletePhoto(user.getId());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<?> buildAvatarResponse(UUID userId, String cacheControl) {
        String ifNoneMatch = resolveIfNoneMatchHeader();
        if (ifNoneMatch != null && !ifNoneMatch.isBlank()) {
            Optional<BinaryMediaMetadataProjection> metaOpt = userPhotoService.getPhotoMetadata(userId);
            if (metaOpt.isEmpty()) {
                return buildAvatarNotFound();
            }
            String etag = BinaryMediaETagSupport.computeETag(userId, metaOpt.get());
            if (BinaryMediaETagSupport.matches(ifNoneMatch, etag)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .header(HttpHeaders.ETAG, etag)
                        .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                        .build();
            }
        }

        Optional<UserPhoto> photoOpt = userPhotoService.getPhoto(userId);
        if (photoOpt.isEmpty()) {
            return buildAvatarNotFound();
        }

        UserPhoto photo = photoOpt.get();
        String contentType = photo.getContentType() != null ? photo.getContentType() : "image/jpeg";
        String etag = BinaryMediaETagSupport.computeETag(userId, photo.getUpdatedAt(), photo.getFileSize(), photo.getFileName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                .header(HttpHeaders.ETAG, etag)
                .body(photo.getData());
    }

    private ResponseEntity<ProblemDetail> buildAvatarNotFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "No custom avatar configured for user"
        );
        problem.setType(URI.create("https://easyeat.com/errors/avatar-not-found"));
        problem.setTitle("Avatar Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    private String resolveIfNoneMatchHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getHeader(HttpHeaders.IF_NONE_MATCH);
        }
        return null;
    }
}
