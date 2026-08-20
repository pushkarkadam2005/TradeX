package com.tradex.kyc.provider;

import com.tradex.kyc.enums.KycLevel;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockKycProvider implements KycProvider {

    @Override
    public KycSubmissionResult submitVerification(UUID userId, KycLevel level, String documentType) {
        String ref = "MOCK-KYC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new KycSubmissionResult(ref, true);
    }
}
