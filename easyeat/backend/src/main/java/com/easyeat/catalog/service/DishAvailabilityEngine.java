package com.easyeat.catalog.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.domain.DishRecipeIngredient;
import com.easyeat.dish.domain.DishStatus;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.entity.Ingredient;
import com.easyeat.inventory.repository.IngredientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class DishAvailabilityEngine {

    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final com.easyeat.order.repository.TemporaryReservationRepository temporaryReservationRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public DishAvailabilityEngine(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.easyeat.order.repository.TemporaryReservationRepository temporaryReservationRepository) {
        this.establishmentRepository = establishmentRepository;
        this.dishRepository = dishRepository;
        this.dishRecipeIngredientRepository = dishRecipeIngredientRepository;
        this.ingredientRepository = ingredientRepository;
        this.temporaryReservationRepository = temporaryReservationRepository;
    }

    public DishAvailabilityEngine(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository) {
        this(establishmentRepository, dishRepository, dishRecipeIngredientRepository, ingredientRepository, null);
    }

    /**
     * Dynamically calculates binary availability for a single dish based on live ingredient inventory.
     * Evaluates decimal precision, checks for inactive/discontinued ingredients, and handles negative stocks.
     */
    public boolean isDishAvailable(UUID dishId, UUID tenantId) {
        if (dishId == null || tenantId == null) {
            return false;
        }

        List<DishRecipeIngredient> recipeLines = dishRecipeIngredientRepository.findByIdDishId(dishId);
        if (recipeLines == null || recipeLines.isEmpty()) {
            // US3 CA 7: Dishes with no recipe (0 ingredients) are safely treated as unavailable
            return false;
        }

        for (DishRecipeIngredient line : recipeLines) {
            if (line.getId() == null || line.getId().getIngredientId() == null || line.getQuantity() == null) {
                return false;
            }

            Optional<Ingredient> ingredientOpt = ingredientRepository.findByIdAndTenantId(
                    line.getId().getIngredientId(), tenantId);

            if (ingredientOpt.isEmpty()) {
                return false;
            }

            Ingredient ingredient = ingredientOpt.get();

            // Check if ingredient is active
            if (ingredient.getStatus() != IngredientStatus.ACTIVE) {
                return false;
            }

            BigDecimal currentStock = ingredient.getCurrentStock();
            if (currentStock == null || currentStock.compareTo(BigDecimal.ZERO) < 0) {
                // US3 CA 10: Negative stock handled safely as out of stock without exception
                return false;
            }

            // US3 CA 8 & CA 9: Exact decimal comparison (currentStock >= recipeQuantity)
            if (currentStock.compareTo(line.getQuantity()) < 0) {
                return false;
            }
        }

        // T036: Si las porciones disponibles netas calculadas (contemplando reservas activas) son 0, el plato no está disponible
        if (temporaryReservationRepository != null && calculateAvailableStock(dishId, tenantId) <= 0) {
            return false;
        }

        return true;
    }

    /**
     * Calculates the maximum integer portions/stock available for a dish based on
     * the limiting ingredient in its recipe and live kitchen inventory (INV-3).
     * Returns 0 if dishId/tenantId is null, dish has no recipe, or any ingredient is inactive/insufficient.
     */
    public int calculateAvailableStock(UUID dishId, UUID tenantId) {
        if (dishId == null || tenantId == null) {
            return 0;
        }

        List<DishRecipeIngredient> recipeLines = dishRecipeIngredientRepository.findByIdDishId(dishId);
        if (recipeLines == null || recipeLines.isEmpty()) {
            return 0;
        }

        int maxPortions = Integer.MAX_VALUE;

        for (DishRecipeIngredient line : recipeLines) {
            if (line.getId() == null || line.getId().getIngredientId() == null || line.getQuantity() == null) {
                return 0;
            }

            BigDecimal lineQuantity = line.getQuantity();
            if (lineQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Optional<Ingredient> ingredientOpt = ingredientRepository.findByIdAndTenantId(
                    line.getId().getIngredientId(), tenantId);

            if (ingredientOpt.isEmpty()) {
                return 0;
            }

            Ingredient ingredient = ingredientOpt.get();

            if (ingredient.getStatus() != IngredientStatus.ACTIVE) {
                return 0;
            }

            BigDecimal currentStock = ingredient.getCurrentStock();
            if (currentStock == null || currentStock.compareTo(BigDecimal.ZERO) <= 0) {
                return 0;
            }

            BigDecimal portions = currentStock.divide(lineQuantity, 0, java.math.RoundingMode.FLOOR);
            int portionCount = portions.intValue();
            if (portionCount < maxPortions) {
                maxPortions = portionCount;
            }
        }

        int availableStock = maxPortions == Integer.MAX_VALUE ? 0 : maxPortions;

        // T026: Deducir porciones bloqueadas en reservas temporales activas no vencidas (status = 'ACTIVE' AND expires_at > NOW())
        if (temporaryReservationRepository != null && availableStock > 0) {
            int reservedPortions = temporaryReservationRepository.sumReservedPortionsByDishId(dishId, java.time.Instant.now());
            availableStock = Math.max(0, availableStock - reservedPortions);
        }

        return availableStock;
    }

    /**
     * Computes the availability map { [dishId]: boolean } for all published active dishes of an establishment.
     */
    public Map<UUID, Boolean> computeAvailabilityMap(UUID tenantId) {
        Establishment establishment = establishmentRepository.findById(tenantId)
                .orElseThrow(() -> new EstablishmentNotFoundException(tenantId));

        if (establishment.getStatus() != EstablishmentStatus.ACTIVE) {
            throw new EstablishmentInactiveException(
                    tenantId,
                    establishment.getStatus() != null ? establishment.getStatus().name() : "INACTIVE"
            );
        }

        List<Dish> publishedDishes = dishRepository.findByTenantIdAndStatusAndIsActiveTrue(tenantId, DishStatus.PUBLISHED);
        if (publishedDishes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<UUID, Boolean> map = new LinkedHashMap<>();
        for (Dish dish : publishedDishes) {
            boolean available = !Boolean.TRUE.equals(dish.getPausedByChef()) && isDishAvailable(dish.getId(), tenantId);
            map.put(dish.getId(), available);
        }

        return map;
    }

    /**
     * Computes a deterministic SHA-256 ETag from the serialized availability map.
     * Orders keys lexicographically to guarantee hash stability across requests.
     */
    public String computeETag(Map<UUID, Boolean> availabilityMap) {
        if (availabilityMap == null || availabilityMap.isEmpty()) {
            return hashString("empty");
        }

        List<UUID> sortedKeys = new ArrayList<>(availabilityMap.keySet());
        sortedKeys.sort(Comparator.comparing(UUID::toString));

        StringBuilder sb = new StringBuilder();
        for (UUID key : sortedKeys) {
            sb.append(key.toString())
                    .append("=")
                    .append(availabilityMap.get(key));
            dishRepository.findById(key).ifPresent(d -> sb.append(":paused=").append(Boolean.TRUE.equals(d.getPausedByChef())));
            sb.append(";");
        }

        return hashString(sb.toString());
    }

    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
