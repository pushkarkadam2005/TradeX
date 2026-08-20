package com.tradex.withdrawal.dto;

import com.tradex.withdrawal.entity.Withdrawal;
import com.tradex.withdrawal.enums.WithdrawalStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WithdrawalResponse(
    UUID id,
    UUID userId,
    BigDecimal amount,
    String currency,
    WithdrawalStatus status,
    String idempotencyKey,
    String destinationReference,
    String complianceDecision,
    String rejectionReason,
    Instant createdAt
) {
    public static WithdrawalResponse fromEntity(Withdrawal entity) {
        return new WithdrawalResponse(
            entity.getId(),
            entity.getUserId(),
            entity.getAmount(),
            entity.getCurrency(),
            entity.getStatus(),
            entity.getIdempotencyKey(),
            entity.getDestinationReference(),
            entity.getComplianceDecision(),
            entity.getRejectionReason(),
            entity.getCreatedAt()
        );
    }
}
