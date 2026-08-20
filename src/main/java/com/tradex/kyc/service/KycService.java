package com.tradex.kyc.service;

import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.kyc.dto.KycResponse;
import com.tradex.kyc.dto.SubmitKycRequest;
import com.tradex.kyc.entity.KycVerification;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.enums.KycStatus;
import com.tradex.kyc.provider.KycProvider;
import com.tradex.kyc.repository.KycRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class KycService {

    private static final Logger log = LoggerFactory.getLogger(KycService.class);

    private final KycRepository kycRepository;
    private final KycProvider kycProvider;
    private final DomainEventPublisher eventPublisher;

    public KycService(KycRepository kycRepository, KycProvider kycProvider, DomainEventPublisher eventPublisher) {
        this.kycRepository = kycRepository;
        this.kycProvider = kycProvider;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public KycResponse submitKyc(UUID userId, SubmitKycRequest request) {
        KycVerification kyc = kycRepository.findByUserId(userId)
            .orElseGet(() -> new KycVerification(userId, request.level(), "MOCK_KYC"));

        if (kyc.getStatus() == KycStatus.VERIFIED) {
            throw new BusinessRuleViolationException("KYC_ALREADY_VERIFIED", "KYC verification is already completed and active");
        }

        KycProvider.KycSubmissionResult result = kycProvider.submitVerification(userId, request.level(), request.documentType());
        kyc.setLevel(request.level());
        kyc.submit(result.providerReference());

        if (result.autoVerified()) {
            kyc.approve(request.level());
        }

        KycVerification saved = kycRepository.save(kyc);

        EventType eventType = (saved.getStatus() == KycStatus.VERIFIED) ? EventType.KYC_VERIFIED : EventType.KYC_SUBMITTED;
        eventPublisher.publish(DomainEvent.of(eventType, "KYC", saved.getId(),
            Map.of("userId", userId, "status", saved.getStatus().name(), "level", saved.getLevel().name())));

        return KycResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public KycResponse getKycStatus(UUID userId) {
        KycVerification kyc = kycRepository.findByUserId(userId)
            .orElseGet(() -> new KycVerification(userId, KycLevel.BASIC, "MOCK_KYC"));
        return KycResponse.fromEntity(kyc);
    }

    @Transactional
    public KycResponse retryKyc(UUID userId, SubmitKycRequest request) {
        KycVerification kyc = kycRepository.findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("KycVerification", "userId", userId));

        if (kyc.getStatus() != KycStatus.REJECTED && kyc.getStatus() != KycStatus.EXPIRED) {
            throw new BusinessRuleViolationException("INVALID_KYC_RETRY", "Can only retry KYC if current status is REJECTED or EXPIRED");
        }

        return submitKyc(userId, request);
    }

    @Transactional(readOnly = true)
    public Page<KycResponse> getPendingKycs(Pageable pageable) {
        return kycRepository.findByStatus(KycStatus.PENDING, pageable)
            .map(KycResponse::fromEntity);
    }

    @Transactional
    public KycResponse approveKycAdmin(UUID kycId, KycLevel level) {
        KycVerification kyc = kycRepository.findById(kycId)
            .orElseThrow(() -> new ResourceNotFoundException("KycVerification", "id", kycId));

        kyc.approve(level);
        KycVerification saved = kycRepository.save(kyc);

        eventPublisher.publish(DomainEvent.of(EventType.KYC_VERIFIED, "KYC", saved.getId(),
            Map.of("userId", saved.getUserId(), "status", saved.getStatus().name(), "level", saved.getLevel().name())));

        return KycResponse.fromEntity(saved);
    }

    @Transactional
    public KycResponse rejectKycAdmin(UUID kycId, String reason) {
        KycVerification kyc = kycRepository.findById(kycId)
            .orElseThrow(() -> new ResourceNotFoundException("KycVerification", "id", kycId));

        kyc.reject(reason != null ? reason : "Administrative rejection");
        KycVerification saved = kycRepository.save(kyc);

        eventPublisher.publish(DomainEvent.of(EventType.KYC_REJECTED, "KYC", saved.getId(),
            Map.of("userId", saved.getUserId(), "status", saved.getStatus().name(), "reason", saved.getRejectionReason())));

        return KycResponse.fromEntity(saved);
    }
}
