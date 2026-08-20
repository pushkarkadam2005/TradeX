package com.tradex.aml.model;

import com.tradex.aml.enums.AmlDecision;
import com.tradex.aml.enums.AmlRiskLevel;

import java.time.Instant;
import java.util.UUID;

public record AmlScreeningResult(
    UUID userId,
    UUID referenceId,
    AmlRiskLevel riskLevel,
    AmlDecision decision,
    String ruleCode,
    String reason,
    String provider,
    Instant screenedAt
) {
    public static AmlScreeningResult allow(UUID userId, UUID referenceId) {
        return new AmlScreeningResult(
            userId, referenceId, AmlRiskLevel.LOW, AmlDecision.ALLOW,
            "AML_PASS", "No sanctions or watchlists matches found", "MOCK_AML", Instant.now()
        );
    }

    public static AmlScreeningResult review(UUID userId, UUID referenceId, String reason) {
        return new AmlScreeningResult(
            userId, referenceId, AmlRiskLevel.HIGH, AmlDecision.REVIEW,
            "AML_REVIEW_REQUIRED", reason, "MOCK_AML", Instant.now()
        );
    }

    public static AmlScreeningResult block(UUID userId, UUID referenceId, String reason) {
        return new AmlScreeningResult(
            userId, referenceId, AmlRiskLevel.CRITICAL, AmlDecision.BLOCK,
            "AML_BLOCKED", reason, "MOCK_AML", Instant.now()
        );
    }
}
