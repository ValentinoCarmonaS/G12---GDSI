package com.easyeat.dish.service;

import com.easyeat.catalog.domain.Dish;
import com.easyeat.catalog.exception.DishNotFoundException;
import com.easyeat.catalog.repository.DishRepository;
import com.easyeat.common.exception.TenantViolationException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The lookup behind every spec 007 admin dish operation (FR-001, SC-003): 404 only when no dish
 * with this id exists at all; a dish that exists but belongs to another tenant is 403, never
 * 404. spec.md's own Edge Cases section states this literally — "HTTP 404 is not an admissible
 * alternative" — which a tenant-scoped {@code findByIdAndTenantId} cannot express on its own,
 * since a cross-tenant id and a nonexistent one both come back empty from it.
 *
 * <p>A plain {@code @Component} rather than a default method on {@link DishRepository}: a
 * default interface method invoked on a plain {@code Mockito.mock()} returns the mock's default
 * (null, here) rather than running the real body, unless the mock is built with {@code
 * CALLS_REAL_METHODS} — which would also make every other, unstubbed method on that mock attempt
 * to call through. A separate, ordinarily-mockable component avoids that trap entirely.
 */
@Component
public class DishAccessGuard {

    private final DishRepository dishRepository;

    public DishAccessGuard(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    /**
     * @throws DishNotFoundException no dish with this id exists.
     * @throws TenantViolationException the dish exists but belongs to a different tenant.
     */
    public Dish requireOwnedByTenant(UUID id, UUID tenantId) {
        Dish dish = dishRepository.findById(id)
                .orElseThrow(() -> new DishNotFoundException(id));
        if (!dish.getTenantId().equals(tenantId)) {
            throw new TenantViolationException(tenantId, dish.getTenantId());
        }
        return dish;
    }
}
