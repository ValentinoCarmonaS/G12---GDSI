package com.easyeat.profile.dto;

import java.util.UUID;

public record AvatarUploadResponse(
        UUID userId,
        String fileName,
        String contentType,
        Long fileSize,
        String avatarUrl
) {
}
