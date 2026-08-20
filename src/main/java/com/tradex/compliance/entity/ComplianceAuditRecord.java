package com.tradex.compliance.entity;

import com.tradex.compliance.enums.ComplianceOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_audit_records")
public class ComplianceAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ComplianceOutcome decision;

    @Column(name = "rule_code", nullable = false, length = 50)
    private String ruleCode;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ComplianceAuditRecord() {
    }

    public ComplianceAuditRecord(UUID userId, String action, ComplianceOutcome decision, String ruleCode, String referenceType, UUID referenceId) {
        this.userId = userId;
        this.action = action;
        this.decision = decision;
        this.ruleCode = ruleCode;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public ComplianceOutcome getDecision() {
        return decision;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
