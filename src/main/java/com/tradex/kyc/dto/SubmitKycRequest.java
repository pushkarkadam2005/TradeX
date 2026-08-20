package com.tradex.kyc.dto;

import com.tradex.kyc.enums.KycLevel;
import jakarta.validation.constraints.NotNull;

public record SubmitKycRequest(
    @NotNull(message = "KYC level is required")
    KycLevel level,
    String documentType
) {}
