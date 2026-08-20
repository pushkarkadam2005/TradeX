package com.tradex.kyc.controller;

import com.tradex.common.dto.ApiResponse;
import com.tradex.kyc.dto.KycResponse;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.service.KycService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/kyc")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKycController {

    private final KycService kycService;

    public AdminKycController(KycService kycService) {
        this.kycService = kycService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<KycResponse>>> getPendingKycs(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<KycResponse> pending = kycService.getPendingKycs(pageable);
        return ResponseEntity.ok(ApiResponse.success("Pending KYC list retrieved", pending));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<KycResponse>> approveKyc(
        @PathVariable UUID id,
        @RequestParam(required = false, defaultValue = "STANDARD") KycLevel level
    ) {
        KycResponse response = kycService.approveKycAdmin(id, level);
        return ResponseEntity.ok(ApiResponse.success("KYC approved successfully", response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<KycResponse>> rejectKyc(
        @PathVariable UUID id,
        @RequestParam(required = false, defaultValue = "Compliance rejection") String reason
    ) {
        KycResponse response = kycService.rejectKycAdmin(id, reason);
        return ResponseEntity.ok(ApiResponse.success("KYC rejected successfully", response));
    }
}
