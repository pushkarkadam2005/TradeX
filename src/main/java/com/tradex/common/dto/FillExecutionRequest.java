package com.tradex.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cross-package data carrier for recording trade fills.
 * Carries plain primitives and UUIDs to prevent package cyclic dependencies.
 */
public record FillExecutionRequest(
    String executionId,
    UUID buyOrderId,
    UUID sellOrderId,
    UUID stockId,
    UUID buyerId,
    UUID sellerId,
    String symbol,
    BigDecimal price,
    long quantity,
    Instant executedAt
) {}
