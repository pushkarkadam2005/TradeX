package com.tradex.kyc.provider;

import com.tradex.kyc.enums.KycLevel;

import java.util.UUID;

public interface KycProvider {
    
    record KycSubmissionResult(String providerReference, boolean autoVerified) {}

    KycSubmissionResult submitVerification(UUID userId, KycLevel level, String documentType);
}
