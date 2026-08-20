package com.tradex.withdrawal.controller;

import com.tradex.common.dto.ApiResponse;
import com.tradex.withdrawal.dto.WithdrawalResponse;
import com.tradex.withdrawal.service.WithdrawalService;
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
@RequestMapping("/api/admin/withdrawals")
@PreAuthorize("hasRole('ADMIN')")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;

    public AdminWithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<Page<WithdrawalResponse>>> getPendingWithdrawals(
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<WithdrawalResponse> pending = withdrawalService.getPendingWithdrawalsAdmin(pageable);
        return ResponseEntity.ok(ApiResponse.success("Pending withdrawals list retrieved", pending));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> approveWithdrawal(@PathVariable UUID id) {
        WithdrawalResponse response = withdrawalService.approveWithdrawalAdmin(id);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal approved successfully", response));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> rejectWithdrawal(
        @PathVariable UUID id,
        @RequestParam(required = false, defaultValue = "Administrative rejection") String reason
    ) {
        WithdrawalResponse response = withdrawalService.rejectWithdrawalAdmin(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal rejected successfully", response));
    }
}
