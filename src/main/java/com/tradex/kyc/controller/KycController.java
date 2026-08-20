package com.tradex.kyc.controller;

import com.tradex.auth.security.UserPrincipal;
import com.tradex.common.dto.ApiResponse;
import com.tradex.kyc.dto.KycResponse;
import com.tradex.kyc.dto.SubmitKycRequest;
import com.tradex.kyc.service.KycService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kyc")
public class KycController {

    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<KycResponse>> submitKyc(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SubmitKycRequest request
    ) {
        KycResponse response = kycService.submitKyc(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("KYC submission processed", response));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KycResponse>> getKycStatus(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        KycResponse response = kycService.getKycStatus(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("KYC status retrieved successfully", response));
    }

    @PostMapping("/retry")
    public ResponseEntity<ApiResponse<KycResponse>> retryKyc(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SubmitKycRequest request
    ) {
        KycResponse response = kycService.retryKyc(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("KYC retry processed", response));
    }
}
