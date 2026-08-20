package com.tradex.compliance.model;

import com.tradex.compliance.enums.ComplianceOutcome;

public record ComplianceDecision(
    ComplianceOutcome outcome,
    String ruleCode,
    String reason
) {
    public static ComplianceDecision approved() {
        return new ComplianceDecision(ComplianceOutcome.APPROVED, "COMPLIANCE_APPROVED", "Compliance checks passed");
    }

    public static ComplianceDecision review(String ruleCode, String reason) {
        return new ComplianceDecision(ComplianceOutcome.REVIEW, ruleCode, reason);
    }

    public static ComplianceDecision rejected(String ruleCode, String reason) {
        return new ComplianceDecision(ComplianceOutcome.REJECTED, ruleCode, reason);
    }

    public boolean isApproved() {
        return outcome == ComplianceOutcome.APPROVED;
    }
}
