package com.easyeat.profile.controller;

import com.easyeat.auth.domain.User;
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
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/profile/avatar")
public class ProfileAvatarController {

    private final UserPhotoService userPhotoService;

    public ProfileAvatarController(UserPhotoService userPhotoService) {
        this.userPhotoService = userPhotoService;
    }

    @PostMapping
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

    @GetMapping
    public ResponseEntity<?> getAvatar(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<UserPhoto> photoOpt = userPhotoService.getPhoto(user.getId());
        if (photoOpt.isEmpty()) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.NOT_FOUND,
                    "No custom avatar configured for user"
            );
            problem.setType(URI.create("https://easyeat.com/errors/avatar-not-found"));
            problem.setTitle("Avatar Not Found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
        }

        UserPhoto photo = photoOpt.get();
        String contentType = photo.getContentType() != null ? photo.getContentType() : "image/jpeg";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .body(photo.getData());
    }

    @DeleteMapping
    @PreAuthorize("hasRole('COMENSAL')")
    public ResponseEntity<Void> removeAvatar(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        userPhotoService.deletePhoto(user.getId());
        return ResponseEntity.noContent().build();
    }
}

