package com.tradex.withdrawal.entity;

import com.tradex.withdrawal.enums.WithdrawalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "withdrawals",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_withdrawal_idemp", columnNames = {"user_id", "idempotency_key"})
)
public class Withdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WithdrawalStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "destination_reference", nullable = false, length = 255)
    private String destinationReference;

    @Column(name = "compliance_decision", length = 30)
    private String complianceDecision;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Withdrawal() {
    }

    public Withdrawal(UUID userId, BigDecimal amount, String currency, String idempotencyKey, String destinationReference) {
        this.userId = userId;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.status = WithdrawalStatus.REQUESTED;
        this.idempotencyKey = idempotencyKey;
        this.destinationReference = destinationReference;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public WithdrawalStatus getStatus() {
        return status;
    }

    public void setStatus(WithdrawalStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDestinationReference() {
        return destinationReference;
    }

    public String getComplianceDecision() {
        return complianceDecision;
    }

    public void setComplianceDecision(String complianceDecision) {
        this.complianceDecision = complianceDecision;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
