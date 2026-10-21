package com.easyeat.common.context;

import com.easyeat.common.exception.TenantViolationException;

import java.util.UUID;

/**
 * Thread-safe holder for the authenticated tenant identity (establishment UUID).
 * Uses {@link ThreadLocal} to isolate tenant scope across concurrent requests.
 */
public final class TenantContextHolder {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static UUID getRequiredTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantViolationException("No active tenant context found for the current request.");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
