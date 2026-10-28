package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.context.TenantContextHolder;
import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.dto.PhotoUploadResponse;
import com.easyeat.dish.exception.DishLockedException;
import com.easyeat.dish.exception.DishNotFoundException;
import com.easyeat.dish.exception.PhotoStorageException;
import com.easyeat.dish.repository.DishPhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
public class DishPhotoService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private final DishRepository dishRepository;
    private final DishPhotoRepository dishPhotoRepository;

    public DishPhotoService(DishRepository dishRepository, DishPhotoRepository dishPhotoRepository) {
        this.dishRepository = dishRepository;
        this.dishPhotoRepository = dishPhotoRepository;
    }

    @Transactional
    public PhotoUploadResponse uploadPhoto(UUID dishId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new PhotoStorageException("El archivo de imagen no puede estar vacío.");
        }

        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId)
                .orElseThrow(() -> new DishNotFoundException(dishId));

        if (dish.getStatus() == DishStatus.PENDING_PRICE) {
            throw new DishLockedException(dishId, "PENDING_PRICE");
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
            throw new PhotoStorageException("Formato no soportado. Debe ser JPEG, PNG, WebP.");
        }

        String normalizedContentType = normalizeContentType(contentType, bytes);

        DishPhoto photo = dishPhotoRepository.findByDishId(dishId)
                .orElseGet(() -> {
                    DishPhoto p = new DishPhoto();
                    p.setDishId(dishId);
                    return p;
                });

        String originalFilename = file.getOriginalFilename();
        photo.setFileName(originalFilename != null && !originalFilename.isBlank() ? originalFilename : "photo");
        photo.setContentType(normalizedContentType);
        photo.setFileSize(file.getSize());
        photo.setData(bytes);

        dishPhotoRepository.save(photo);

        return new PhotoUploadResponse(
                dishId,
                photo.getFileName(),
                photo.getContentType(),
                photo.getFileSize(),
                "/api/v1/dishes/" + dishId + "/photo"
        );
    }

    @Transactional(readOnly = true)
    public DishPhoto getPhoto(UUID dishId) {
        return dishPhotoRepository.findByDishId(dishId)
                .orElseThrow(() -> new DishNotFoundException("No se encontró la foto para el plato con ID: " + dishId));
    }

    @Transactional
    public void deletePhoto(UUID dishId) {
        UUID tenantId = TenantContextHolder.getTenantId();
        Dish dish = dishRepository.findByIdAndTenantId(dishId, tenantId)
                .orElseThrow(() -> new DishNotFoundException(dishId));

        if (dish.getStatus() == DishStatus.PENDING_PRICE) {
            throw new DishLockedException(dishId, "PENDING_PRICE");
        }

        dishPhotoRepository.deleteByDishId(dishId);
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
