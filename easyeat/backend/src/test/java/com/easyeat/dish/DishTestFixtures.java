package com.easyeat.dish;

import com.easyeat.catalog.domain.Dish;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Base test fixtures and fluent builders for Dish domain entities, identifiers,
 * multimedia test payloads, and multi-tenant test boundaries.
 */
public final class DishTestFixtures {

    public static final UUID DEFAULT_TENANT_ID = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    public static final UUID ANOTHER_TENANT_ID = UUID.fromString("b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a22");

    public static final UUID DEFAULT_CHEF_ID = UUID.fromString("c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33");
    public static final UUID DEFAULT_ADMIN_ID = UUID.fromString("d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a44");

    public static final UUID DEFAULT_DISH_ID = UUID.fromString("e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55");
    public static final UUID DEFAULT_INGREDIENT_ID = UUID.fromString("f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a66");

    public static final String DEFAULT_DISH_NAME = "Risotto de Hongos Portobello";
    public static final String DEFAULT_DISH_CATEGORY = "Platos principales";
    public static final String DEFAULT_DISH_DESCRIPTION = "Arroz arborio cremoso cocinado lentamente con hongos portobello salteados al vino blanco.";
    public static final BigDecimal DEFAULT_DISH_PRICE = BigDecimal.valueOf(4500.00);

    public static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024; // 5 MB

    private DishTestFixtures() {
        // Utility class
    }

    public static Dish createStandardDish() {
        Dish dish = new Dish();
        dish.setId(DEFAULT_DISH_ID);
        dish.setTenantId(DEFAULT_TENANT_ID);
        dish.setName(DEFAULT_DISH_NAME);
        dish.setDescription(DEFAULT_DISH_DESCRIPTION);
        dish.setPrice(DEFAULT_DISH_PRICE);
        dish.setIsActive(true);
        dish.setCreatedAt(Instant.now());
        dish.setUpdatedAt(Instant.now());
        return dish;
    }

    public static DishBuilder dishBuilder() {
        return new DishBuilder();
    }

    public static byte[] createValidJpegBytes() {
        // JPEG magic bytes: FF D8 FF E0
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        bytes[3] = (byte) 0xE0;
        return bytes;
    }

    public static byte[] createValidPngBytes() {
        // PNG magic bytes: 89 50 4E 47 0D 0A 1A 0A
        byte[] bytes = new byte[512];
        bytes[0] = (byte) 0x89;
        bytes[1] = (byte) 0x50; // P
        bytes[2] = (byte) 0x4E; // N
        bytes[3] = (byte) 0x47; // G
        bytes[4] = (byte) 0x0D;
        bytes[5] = (byte) 0x0A;
        bytes[6] = (byte) 0x1A;
        bytes[7] = (byte) 0x0A;
        return bytes;
    }

    public static byte[] createValidWebpBytes() {
        // WebP RIFF....WEBP
        byte[] bytes = new byte[512];
        bytes[0] = 'R';
        bytes[1] = 'I';
        bytes[2] = 'F';
        bytes[3] = 'F';
        bytes[8] = 'W';
        bytes[9] = 'E';
        bytes[10] = 'B';
        bytes[11] = 'P';
        return bytes;
    }

    public static byte[] createOversizedPhotoBytes() {
        return new byte[MAX_PHOTO_BYTES + 1024]; // 5 MB + 1 KB
    }

    public static byte[] createInvalidFormatBytes() {
        return "This is plain text and definitely not a valid image file.".getBytes(StandardCharsets.UTF_8);
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

    public static class DishBuilder {
        private UUID id = UUID.randomUUID();
        private UUID tenantId = DEFAULT_TENANT_ID;
        private String name = DEFAULT_DISH_NAME;
        private String description = DEFAULT_DISH_DESCRIPTION;
        private BigDecimal price = DEFAULT_DISH_PRICE;
        private Boolean isActive = true;
        private Set<String> restrictionCodes = new HashSet<>();

        public DishBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public DishBuilder tenantId(UUID tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public DishBuilder name(String name) {
            this.name = name;
            return this;
        }

        public DishBuilder description(String description) {
            this.description = description;
            return this;
        }

        public DishBuilder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public DishBuilder isActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public DishBuilder restrictions(Set<String> restrictionCodes) {
            this.restrictionCodes = new HashSet<>(restrictionCodes);
            return this;
        }

        public Dish build() {
            Dish dish = new Dish();
            dish.setId(this.id);
            dish.setTenantId(this.tenantId);
            dish.setName(this.name);
            dish.setDescription(this.description);
            dish.setPrice(this.price);
            dish.setIsActive(this.isActive);
            dish.setRestrictionCodes(this.restrictionCodes);
            dish.setCreatedAt(Instant.now());
            dish.setUpdatedAt(Instant.now());
            return dish;
        }
    }
}
