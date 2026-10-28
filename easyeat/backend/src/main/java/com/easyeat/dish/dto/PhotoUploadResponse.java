package com.easyeat.dish.dto;

import java.util.UUID;

public record PhotoUploadResponse(
        UUID dishId,
        String fileName,
        String contentType,
        long fileSize,
        String photoUrl
) {
}
