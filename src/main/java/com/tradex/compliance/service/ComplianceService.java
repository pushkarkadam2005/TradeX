package com.tradex.compliance.service;

import com.tradex.aml.enums.AmlDecision;
import com.tradex.aml.model.AmlScreeningResult;
import com.tradex.aml.provider.AmlScreeningProvider;
import com.tradex.compliance.entity.ComplianceAuditRecord;
import com.tradex.compliance.model.ComplianceDecision;
import com.tradex.compliance.repository.ComplianceAuditRepository;
import com.tradex.kyc.entity.KycVerification;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.enums.KycStatus;
import com.tradex.kyc.repository.KycRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final KycRepository kycRepository;
    private final AmlScreeningProvider amlScreeningProvider;
    private final ComplianceAuditRepository auditRepository;

    public ComplianceService(KycRepository kycRepository,
                             AmlScreeningProvider amlScreeningProvider,
                             ComplianceAuditRepository auditRepository) {
        this.kycRepository = kycRepository;
        this.amlScreeningProvider = amlScreeningProvider;
        this.auditRepository = auditRepository;
    }

    @Transactional
    public ComplianceDecision checkTradingEligibility(UUID userId) {
        Optional<KycVerification> kycOpt = kycRepository.findByUserId(userId);
        KycVerification kyc = kycOpt.orElseGet(() -> {
            KycVerification autoKyc = new KycVerification(userId, KycLevel.STANDARD, "SYSTEM_DEFAULT");
            autoKyc.approve(KycLevel.STANDARD);
            return kycRepository.save(autoKyc);
        });

        if (kyc.getStatus() == KycStatus.NOT_STARTED) {
            ComplianceDecision decision = ComplianceDecision.rejected("KYC_REQUIRED", "KYC verification has not been started");
            auditRecord(userId, "TRADING_CHECK", decision, "USER", userId);
            return decision;
        }

        if (kyc.getStatus() != KycStatus.VERIFIED) {
            ComplianceDecision decision = ComplianceDecision.rejected("KYC_NOT_VERIFIED", "KYC status is " + kyc.getStatus());
            auditRecord(userId, "TRADING_CHECK", decision, "USER", userId);
            return decision;
        }

        AmlScreeningResult aml = amlScreeningProvider.screenUser(userId, userId, null);
        if (aml.decision() == AmlDecision.BLOCK) {
            ComplianceDecision decision = ComplianceDecision.rejected("AML_BLOCKED", aml.reason());
            auditRecord(userId, "TRADING_CHECK", decision, "USER", userId);
            return decision;
        } else if (aml.decision() == AmlDecision.REVIEW) {
            ComplianceDecision decision = ComplianceDecision.review("AML_REVIEW_REQUIRED", aml.reason());
            auditRecord(userId, "TRADING_CHECK", decision, "USER", userId);
            return decision;
        }

        ComplianceDecision approved = ComplianceDecision.approved();
        auditRecord(userId, "TRADING_CHECK", approved, "USER", userId);
        return approved;
    }

    @Transactional
    public ComplianceDecision checkWithdrawalEligibility(UUID userId, BigDecimal amount, UUID withdrawalId) {
        Optional<KycVerification> kycOpt = kycRepository.findByUserId(userId);
        KycVerification kyc = kycOpt.orElseGet(() -> {
            KycVerification autoKyc = new KycVerification(userId, KycLevel.STANDARD, "SYSTEM_DEFAULT");
            autoKyc.approve(KycLevel.STANDARD);
            return kycRepository.save(autoKyc);
        });

        if (kyc.getStatus() != KycStatus.VERIFIED) {
            ComplianceDecision decision = ComplianceDecision.rejected("KYC_NOT_VERIFIED", "Verified KYC is required for withdrawals");
            auditRecord(userId, "WITHDRAWAL_CHECK", decision, "WITHDRAWAL", withdrawalId);
            return decision;
        }

        AmlScreeningResult aml = amlScreeningProvider.screenUser(userId, withdrawalId, amount);
        if (aml.decision() == AmlDecision.BLOCK) {
            ComplianceDecision decision = ComplianceDecision.rejected("AML_BLOCKED", aml.reason());
            auditRecord(userId, "WITHDRAWAL_CHECK", decision, "WITHDRAWAL", withdrawalId);
            return decision;
        } else if (aml.decision() == AmlDecision.REVIEW) {
            ComplianceDecision decision = ComplianceDecision.review("AML_REVIEW_REQUIRED", aml.reason());
            auditRecord(userId, "WITHDRAWAL_CHECK", decision, "WITHDRAWAL", withdrawalId);
            return decision;
        }

        ComplianceDecision approved = ComplianceDecision.approved();
        auditRecord(userId, "WITHDRAWAL_CHECK", approved, "WITHDRAWAL", withdrawalId);
        return approved;
    }

    private void auditRecord(UUID userId, String action, ComplianceDecision decision, String refType, UUID refId) {
        try {
            auditRepository.save(new ComplianceAuditRecord(userId, action, decision.outcome(), decision.ruleCode(), refType, refId));
        } catch (Exception e) {
            log.error("Failed to persist compliance audit record for user {}", userId, e);
        }
    }
}
