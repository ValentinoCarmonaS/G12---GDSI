package com.easyeat.dish.service;

import com.easyeat.dish.dto.CreateDishRequest;
import com.easyeat.dish.dto.DishDetailResponse;
import com.easyeat.dish.dto.DishPageResponse;
import com.easyeat.dish.dto.UpdateDishRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DishService {

    public DishDetailResponse createDraft(CreateDishRequest request, UUID chefId) {
        throw new UnsupportedOperationException("T028 pending implementation");
    }

    public DishDetailResponse updateBasicInfo(UUID id, UpdateDishRequest request) {
        throw new UnsupportedOperationException("T028 pending implementation");
    }

    public DishDetailResponse getDishById(UUID id) {
        throw new UnsupportedOperationException("T028 pending implementation");
    }

    public DishPageResponse listDishes(String statusFilter, Pageable pageable) {
        throw new UnsupportedOperationException("T028 pending implementation");
    }

    public void deleteDraft(UUID id) {
        throw new UnsupportedOperationException("T028 pending implementation");
    }
}
