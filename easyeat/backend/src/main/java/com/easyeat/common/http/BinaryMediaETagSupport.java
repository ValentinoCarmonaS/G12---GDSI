package com.easyeat.common.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Deterministic SHA-256 ETag computation and tolerant If-None-Match matching for binary media (FR-006, D-07).
 */
public final class BinaryMediaETagSupport {

    private BinaryMediaETagSupport() {
    }

    public static String computeETag(UUID resourceId, BinaryMediaMetadataProjection metadata) {
        if (metadata == null) {
            return null;
        }
        return computeETag(resourceId, metadata.getUpdatedAt(), metadata.getFileSize(), metadata.getFileName());
    }

    public static String computeETag(UUID resourceId, Instant updatedAt, Long fileSize, String fileName) {
        long millis = updatedAt != null ? updatedAt.toEpochMilli() : 0L;
        long size = fileSize != null ? fileSize : 0L;
        String safeName = fileName != null ? fileName : "";
        String payload = resourceId + ":" + millis + ":" + size + ":" + safeName;
        return "\"" + sha256Hex(payload) + "\"";
    }

    public static boolean matches(String ifNoneMatchHeader, String currentETag) {
        if (ifNoneMatchHeader == null || ifNoneMatchHeader.isBlank() || currentETag == null || currentETag.isBlank()) {
            return false;
        }
        String normalizedCurrent = normalizeTag(currentETag);
        String[] candidates = ifNoneMatchHeader.split(",");
        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed)) {
                return true;
            }
            if (normalizedCurrent.equals(normalizeTag(trimmed))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeTag(String tag) {
        String s = tag.trim();
        if (s.startsWith("W/") || s.startsWith("w/")) {
            s = s.substring(2).trim();
        }
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
