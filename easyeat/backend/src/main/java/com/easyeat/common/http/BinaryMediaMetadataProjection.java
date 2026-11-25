package com.easyeat.common.http;

import java.time.Instant;

/**
 * Lightweight projection for binary media metadata excluding the BYTEA data column (FR-006, D-07).
 */
public interface BinaryMediaMetadataProjection {

    String getFileName();

    String getContentType();

    Long getFileSize();

    Instant getUpdatedAt();
}
