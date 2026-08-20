package com.tradex.risk.model;

public record RiskDecision(
    boolean isApproved,
    String ruleCode,
    String reason
) {
    public static RiskDecision approved() {
        return new RiskDecision(true, "APPROVED", "Pre-trade risk checks passed");
    }

    public static RiskDecision rejected(String ruleCode, String reason) {
        return new RiskDecision(false, ruleCode, reason);
    }
}
