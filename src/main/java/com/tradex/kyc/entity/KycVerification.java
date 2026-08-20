package com.tradex.kyc.entity;

import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.enums.KycStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyc_verifications")
public class KycVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private KycLevel level;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(name = "provider_reference", length = 100)
    private String providerReference;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected KycVerification() {
    }

    public KycVerification(UUID userId, KycLevel level, String provider) {
        this.userId = userId;
        this.status = KycStatus.NOT_STARTED;
        this.level = level != null ? level : KycLevel.BASIC;
        this.provider = provider != null ? provider : "MOCK_KYC";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public KycStatus getStatus() {
        return status;
    }

    public void setStatus(KycStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public KycLevel getLevel() {
        return level;
    }

    public void setLevel(KycLevel level) {
        this.level = level;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(Instant verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
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

    public void submit(String providerRef) {
        this.status = KycStatus.PENDING;
        this.providerReference = providerRef;
        this.submittedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void approve(KycLevel verifiedLevel) {
        this.status = KycStatus.VERIFIED;
        if (verifiedLevel != null) {
            this.level = verifiedLevel;
        }
        this.verifiedAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(365L * 24 * 3600); // 1 year validity
        this.rejectionReason = null;
        this.updatedAt = Instant.now();
    }

    public void reject(String reason) {
        this.status = KycStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = Instant.now();
    }
}
