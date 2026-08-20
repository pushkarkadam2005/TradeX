package com.tradex.kyc.dto;

import com.tradex.kyc.entity.KycVerification;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.enums.KycStatus;

import java.time.Instant;
import java.util.UUID;

public record KycResponse(
    UUID id,
    UUID userId,
    KycStatus status,
    KycLevel level,
    String provider,
    String providerReference,
    Instant submittedAt,
    Instant verifiedAt,
    Instant expiresAt,
    String rejectionReason,
    Instant createdAt
) {
    public static KycResponse fromEntity(KycVerification entity) {
        return new KycResponse(
            entity.getId(),
            entity.getUserId(),
            entity.getStatus(),
            entity.getLevel(),
            entity.getProvider(),
            entity.getProviderReference(),
            entity.getSubmittedAt(),
            entity.getVerifiedAt(),
            entity.getExpiresAt(),
            entity.getRejectionReason(),
            entity.getCreatedAt()
        );
    }
}
