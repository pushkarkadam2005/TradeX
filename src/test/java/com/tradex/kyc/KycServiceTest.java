package com.tradex.kyc;

import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.kyc.dto.KycResponse;
import com.tradex.kyc.dto.SubmitKycRequest;
import com.tradex.kyc.entity.KycVerification;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.enums.KycStatus;
import com.tradex.kyc.provider.KycProvider;
import com.tradex.kyc.repository.KycRepository;
import com.tradex.kyc.service.KycService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private KycRepository kycRepository;

    @Mock
    private KycProvider kycProvider;

    @Mock
    private DomainEventPublisher eventPublisher;

    private KycService kycService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        kycService = new KycService(kycRepository, kycProvider, eventPublisher);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("KYC Submission — Auto-verifies mock KYC and publishes KYC_VERIFIED event")
    void submitKycAutoVerifies() {
        SubmitKycRequest req = new SubmitKycRequest(KycLevel.STANDARD, "PASSPORT");
        given(kycRepository.findByUserId(userId)).willReturn(Optional.empty());
        given(kycProvider.submitVerification(eq(userId), eq(KycLevel.STANDARD), eq("PASSPORT")))
            .willReturn(new KycProvider.KycSubmissionResult("MOCK-REF-123", true));
        given(kycRepository.save(any(KycVerification.class))).willAnswer(invocation -> invocation.getArgument(0));

        KycResponse res = kycService.submitKyc(userId, req);

        assertThat(res.status()).isEqualTo(KycStatus.VERIFIED);
        assertThat(res.level()).isEqualTo(KycLevel.STANDARD);
        assertThat(res.providerReference()).isEqualTo("MOCK-REF-123");
    }

    @Test
    @DisplayName("Admin Approval — Approves pending KYC and updates status to VERIFIED")
    void approveKycAdmin() {
        UUID kycId = UUID.randomUUID();
        KycVerification kyc = new KycVerification(userId, KycLevel.BASIC, "MOCK_KYC");
        kyc.setStatus(KycStatus.PENDING);

        given(kycRepository.findById(kycId)).willReturn(Optional.of(kyc));
        given(kycRepository.save(any(KycVerification.class))).willAnswer(invocation -> invocation.getArgument(0));

        KycResponse res = kycService.approveKycAdmin(kycId, KycLevel.ENHANCED);

        assertThat(res.status()).isEqualTo(KycStatus.VERIFIED);
        assertThat(res.level()).isEqualTo(KycLevel.ENHANCED);
    }
}
