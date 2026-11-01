package com.easyeat.profile.service;

import com.easyeat.dish.exception.PhotoStorageException;
import com.easyeat.profile.domain.UserPhoto;
import com.easyeat.profile.dto.AvatarUploadResponse;
import com.easyeat.profile.repository.UserPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class UserPhotoService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5.242.880 bytes
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final UserPhotoRepository userPhotoRepository;

    public UserPhotoService(UserPhotoRepository userPhotoRepository) {
        this.userPhotoRepository = userPhotoRepository;
    }

    @Transactional
    public AvatarUploadResponse uploadPhoto(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new PhotoStorageException("El archivo de imagen no puede estar vacío.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new PhotoStorageException("El archivo supera el tamaño máximo permitido de 5 MB.");
        }

        String contentType = file.getContentType();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new PhotoStorageException("Error al leer los datos de la imagen: " + e.getMessage());
        }

        if (!isValidImage(contentType, bytes)) {
            throw new PhotoStorageException("Formato no soportado. Debe ser JPEG, PNG o WebP.");
        }

        String normalizedContentType = normalizeContentType(contentType, bytes);

        UserPhoto photo = userPhotoRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserPhoto p = new UserPhoto();
                    p.setUserId(userId);
                    return p;
                });

        String originalFilename = file.getOriginalFilename();
        photo.setFileName(originalFilename != null && !originalFilename.isBlank() ? originalFilename : "avatar");
        photo.setContentType(normalizedContentType);
        photo.setFileSize(file.getSize());
        photo.setData(bytes);

        userPhotoRepository.save(photo);

        return new AvatarUploadResponse(
                userId,
                photo.getFileName(),
                photo.getContentType(),
                photo.getFileSize(),
                "/api/v1/profile/avatar"
        );
    }

    @Transactional(readOnly = true)
    public Optional<UserPhoto> getPhoto(UUID userId) {
        return userPhotoRepository.findByUserId(userId);
    }

    @Transactional
    public void deletePhoto(UUID userId) {
        userPhotoRepository.deleteByUserId(userId);
    }

    public static boolean isJpeg(byte[] data) {
        if (data == null || data.length < 4) return false;
        return (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
    }

    public static boolean isPng(byte[] data) {
        if (data == null || data.length < 8) return false;
        return (data[0] & 0xFF) == 0x89 &&
                data[1] == 'P' &&
                data[2] == 'N' &&
                data[3] == 'G' &&
                data[4] == 0x0D &&
                data[5] == 0x0A &&
                data[6] == 0x1A &&
                data[7] == 0x0A;
    }

    public static boolean isWebp(byte[] data) {
        if (data == null || data.length < 12) return false;
        return data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' &&
                data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
    }

    private boolean isValidImage(String contentType, byte[] data) {
        boolean validMime = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
        boolean validBytes = isJpeg(data) || isPng(data) || isWebp(data);
        return validMime && validBytes;
    }

    private String normalizeContentType(String contentType, byte[] data) {
        if (isJpeg(data)) return "image/jpeg";
        if (isPng(data)) return "image/png";
        if (isWebp(data)) return "image/webp";
        if (contentType != null) {
            String lower = contentType.toLowerCase();
            if (lower.equals("image/jpeg") || lower.equals("image/jpg")) return "image/jpeg";
            if (lower.equals("image/png")) return "image/png";
            if (lower.equals("image/webp")) return "image/webp";
        }
        return "image/jpeg";
    }
}
