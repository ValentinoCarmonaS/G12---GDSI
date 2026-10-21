package com.easyeat.staff.exception;

import java.util.UUID;

public class OrphanedAdminException extends RuntimeException {

    private final UUID tenantId;

    public OrphanedAdminException(String message) {
        super(message);
        this.tenantId = null;
    }

    public OrphanedAdminException(UUID tenantId) {
        super(String.format("Operation rejected: cannot remove or reassign the sole active Administrator for establishment %s", tenantId));
        this.tenantId = tenantId;
    }

    public OrphanedAdminException(UUID tenantId, String message) {
        super(message);
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
