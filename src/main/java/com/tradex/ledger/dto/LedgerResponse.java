package com.tradex.ledger.dto;

import com.tradex.ledger.entity.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerResponse(
    UUID id,
    UUID transactionId,
    UUID accountId,
    String entryType,
    BigDecimal amount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    String referenceType,
    UUID referenceId,
    String description,
    Instant createdAt
) {
    public static LedgerResponse fromEntity(LedgerEntry entry) {
        return new LedgerResponse(
            entry.getId(),
            entry.getTransactionId(),
            entry.getAccountId(),
            entry.getEntryType(),
            entry.getAmount(),
            entry.getBalanceBefore(),
            entry.getBalanceAfter(),
            entry.getReferenceType(),
            entry.getReferenceId(),
            entry.getDescription(),
            entry.getCreatedAt()
        );
    }
}
