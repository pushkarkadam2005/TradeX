package com.tradex.transaction.dto;

import com.tradex.transaction.entity.TransactionRecord;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    UUID userId,
    TransactionType transactionType,
    TransactionStatus transactionStatus,
    BigDecimal amount,
    String currency,
    String referenceType,
    UUID referenceId,
    String idempotencyKey,
    String description,
    Instant createdAt
) {
    public static TransactionResponse fromEntity(TransactionRecord entity) {
        return new TransactionResponse(
            entity.getId(),
            entity.getUserId(),
            entity.getTransactionType(),
            entity.getTransactionStatus(),
            entity.getAmount(),
            entity.getCurrency(),
            entity.getReferenceType(),
            entity.getReferenceId(),
            entity.getIdempotencyKey(),
            entity.getDescription(),
            entity.getCreatedAt()
        );
    }
}
