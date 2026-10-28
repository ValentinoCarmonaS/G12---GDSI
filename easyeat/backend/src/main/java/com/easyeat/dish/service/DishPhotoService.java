package com.easyeat.dish.service;

import com.easyeat.dish.domain.DishPhoto;
import com.easyeat.dish.dto.PhotoUploadResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class DishPhotoService {

    public PhotoUploadResponse uploadPhoto(UUID dishId, MultipartFile file) {
        throw new UnsupportedOperationException("T027 pending implementation");
    }

    public DishPhoto getPhoto(UUID dishId) {
        throw new UnsupportedOperationException("T027 pending implementation");
    }

    public void deletePhoto(UUID dishId) {
        throw new UnsupportedOperationException("T027 pending implementation");
    }
}
