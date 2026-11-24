package com.easyeat.catalog.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.domain.Establishment;
import com.easyeat.catalog.domain.EstablishmentStatus;
import com.easyeat.catalog.exception.EstablishmentInactiveException;
import com.easyeat.catalog.exception.EstablishmentNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.catalog.repository.EstablishmentRepository;
import com.easyeat.dish.repository.DishRecipeIngredientRepository;
import com.easyeat.dish.repository.RecipeLineWithIngredientProjection;
import com.easyeat.inventory.dto.IngredientStatus;
import com.easyeat.inventory.repository.IngredientRepository;
import com.easyeat.order.repository.DishReservedPortionsProjection;
import com.easyeat.order.repository.TemporaryReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DishAvailabilityEngine {

    public record DishAvailabilityResult(boolean isAvailable, int availableStock) {
    }

    public record AvailabilitySnapshot(Map<UUID, Boolean> availabilityMap, String etag) {
    }

    private final EstablishmentRepository establishmentRepository;
    private final DishRepository dishRepository;
    private final DishRecipeIngredientRepository dishRecipeIngredientRepository;
    private final IngredientRepository ingredientRepository;
    private final TemporaryReservationRepository temporaryReservationRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public DishAvailabilityEngine(
            EstablishmentRepository establishmentRepository,
            DishRepository dishRepository,
            DishRecipeIngredientRepository dishRecipeIngredientRepository,
            IngredientRepository ingredientRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            TemporaryReservationRepository temporaryReservationRepository) {
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
     */
    public boolean isDishAvailable(UUID dishId, UUID tenantId) {
        if (dishId == null || tenantId == null) {
            return false;
        }
        return computeSingleDishAvailability(dishId, tenantId).isAvailable();
    }

    /**
     * Calculates the maximum integer portions/stock available for a dish based on
     * the limiting ingredient in its recipe and live kitchen inventory (INV-3).
     */
    public int calculateAvailableStock(UUID dishId, UUID tenantId) {
        if (dishId == null || tenantId == null) {
            return 0;
        }
        return computeSingleDishAvailability(dishId, tenantId).availableStock();
    }

    private DishAvailabilityResult computeSingleDishAvailability(UUID dishId, UUID tenantId) {
        List<RecipeLineWithIngredientProjection> lines =
                dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(List.of(dishId), tenantId);
        if (lines == null || lines.isEmpty()) {
            return new DishAvailabilityResult(false, 0);
        }

        Map<UUID, Integer> reservedByDish = loadReservedPortions(List.of(dishId));
        return evaluateDishFromLines(dishId, lines, reservedByDish.getOrDefault(dishId, 0));
    }

    /**
     * Single-pass batch availability calculation for preloaded dishes without N+1 queries (FR-001, FR-002).
     */
    public Map<UUID, DishAvailabilityResult> computeBatchAvailability(
            Collection<Dish> dishes,
            UUID tenantId,
            List<RecipeLineWithIngredientProjection> preloadedLines) {
        if (dishes == null || dishes.isEmpty() || tenantId == null) {
            return Collections.emptyMap();
        }

        List<UUID> dishIds = dishes.stream().map(Dish::getId).filter(Objects::nonNull).toList();
        if (dishIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RecipeLineWithIngredientProjection> lines = preloadedLines != null
                ? preloadedLines
                : dishRecipeIngredientRepository.findRecipeLinesWithIngredientsByDishIdsAndTenantId(dishIds, tenantId);

        Map<UUID, List<RecipeLineWithIngredientProjection>> linesByDish = new HashMap<>();
        if (lines != null) {
            for (RecipeLineWithIngredientProjection line : lines) {
                if (line.getDishId() != null) {
                    linesByDish.computeIfAbsent(line.getDishId(), k -> new ArrayList<>()).add(line);
                }
            }
        }

        Map<UUID, Integer> reservedByDish = loadReservedPortions(dishIds);

        Map<UUID, DishAvailabilityResult> results = new LinkedHashMap<>();
        for (UUID dishId : dishIds) {
            List<RecipeLineWithIngredientProjection> dishLines = linesByDish.getOrDefault(dishId, Collections.emptyList());
            results.put(dishId, evaluateDishFromLines(dishId, dishLines, reservedByDish.getOrDefault(dishId, 0)));
        }
        return results;
    }

    private Map<UUID, Integer> loadReservedPortions(Collection<UUID> dishIds) {
        if (temporaryReservationRepository == null || dishIds == null || dishIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DishReservedPortionsProjection> projections =
                temporaryReservationRepository.sumActiveReservedPortionsByDishIds(dishIds, Instant.now());
        if (projections == null || projections.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Integer> map = new HashMap<>();
        for (DishReservedPortionsProjection proj : projections) {
            if (proj.getDishId() != null && proj.getReservedPortions() != null) {
                map.put(proj.getDishId(), proj.getReservedPortions().intValue());
            }
        }
        return map;
    }

    private DishAvailabilityResult evaluateDishFromLines(
            UUID dishId,
            List<RecipeLineWithIngredientProjection> lines,
            int reservedPortions) {
        if (lines == null || lines.isEmpty()) {
            return new DishAvailabilityResult(false, 0);
        }

        boolean rawAvailable = true;
        int maxPortions = Integer.MAX_VALUE;

        for (RecipeLineWithIngredientProjection line : lines) {
            BigDecimal quantity = line.getQuantity();
            if (line.getIngredientId() == null || quantity == null) {
                return new DishAvailabilityResult(false, 0);
            }

            IngredientStatus status = line.getIngredientStatus();
            BigDecimal currentStock = line.getCurrentStock();
            boolean activeWithNonNegativeStock =
                    status == IngredientStatus.ACTIVE && currentStock != null && currentStock.compareTo(BigDecimal.ZERO) >= 0;

            if (!activeWithNonNegativeStock || currentStock.compareTo(quantity) < 0) {
                rawAvailable = false;
            }

            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                if (status != IngredientStatus.ACTIVE || currentStock == null || currentStock.compareTo(BigDecimal.ZERO) <= 0) {
                    maxPortions = 0;
                    rawAvailable = false;
                } else {
                    int portionCount = currentStock.divide(quantity, 0, RoundingMode.FLOOR).intValue();
                    if (portionCount < maxPortions) {
                        maxPortions = portionCount;
                    }
                }
            }
        }

        int rawStock = maxPortions == Integer.MAX_VALUE ? 0 : maxPortions;
        int netStock = Math.max(0, rawStock - reservedPortions);
        boolean isAvailable = rawAvailable && (temporaryReservationRepository == null || netStock > 0);
        return new DishAvailabilityResult(isAvailable, netStock);
    }

    /**
     * Computes the availability map { [dishId]: boolean } for all published active dishes of an establishment.
     */
    public Map<UUID, Boolean> computeAvailabilityMap(UUID tenantId) {
        return computeAvailabilitySnapshotInternal(tenantId).availabilityMap();
    }

    /**
     * Computes both the availability map and its deterministic SHA-256 ETag in at most 4 SQL queries (SC-001).
     */
    public AvailabilitySnapshot computeAvailabilitySnapshot(UUID tenantId) {
        return computeAvailabilitySnapshotInternal(tenantId);
    }

    private AvailabilitySnapshot computeAvailabilitySnapshotInternal(UUID tenantId) {
        Establishment establishment = establishmentRepository.findById(tenantId)
                .orElseThrow(() -> new EstablishmentNotFoundException(tenantId));

        if (establishment.getStatus() != EstablishmentStatus.ACTIVE) {
            throw new EstablishmentInactiveException(
                    tenantId,
                    establishment.getStatus() != null ? establishment.getStatus().name() : "INACTIVE"
            );
        }

        List<Dish> publishedDishes = dishRepository.findPublishedActiveDishesWithRestrictions(tenantId);
        if (publishedDishes.isEmpty()) {
            return new AvailabilitySnapshot(Collections.emptyMap(), hashString("empty"));
        }

        Map<UUID, DishAvailabilityResult> batchResults = computeBatchAvailability(publishedDishes, tenantId, null);

        Map<UUID, Boolean> map = new LinkedHashMap<>();
        Map<UUID, Dish> dishById = new HashMap<>();
        for (Dish dish : publishedDishes) {
            DishAvailabilityResult res = batchResults.getOrDefault(dish.getId(), new DishAvailabilityResult(false, 0));
            boolean available = !Boolean.TRUE.equals(dish.getPausedByChef()) && res.isAvailable();
            map.put(dish.getId(), available);
            dishById.put(dish.getId(), dish);
        }

        return new AvailabilitySnapshot(map, computeETag(map, dishById));
    }

    /**
     * Computes a deterministic SHA-256 ETag from the serialized availability map using batch dish lookup.
     */
    public String computeETag(Map<UUID, Boolean> availabilityMap) {
        if (availabilityMap == null || availabilityMap.isEmpty()) {
            return hashString("empty");
        }

        List<UUID> sortedKeys = new ArrayList<>(availabilityMap.keySet());
        List<Dish> dishes = dishRepository.findByIdIn(sortedKeys);
        Map<UUID, Dish> dishById = dishes == null
                ? Collections.emptyMap()
                : dishes.stream().collect(Collectors.toMap(Dish::getId, Function.identity(), (a, b) -> a));

        return computeETag(availabilityMap, dishById);
    }

    public String computeETag(Map<UUID, Boolean> availabilityMap, Map<UUID, Dish> dishById) {
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
            Dish dish = dishById != null ? dishById.get(key) : null;
            if (dish != null) {
                sb.append(":paused=").append(Boolean.TRUE.equals(dish.getPausedByChef()));
            }
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
