package com.tradex.withdrawal.controller;

import com.tradex.auth.security.UserPrincipal;
import com.tradex.common.dto.ApiResponse;
import com.tradex.withdrawal.dto.CreateWithdrawalRequest;
import com.tradex.withdrawal.dto.WithdrawalResponse;
import com.tradex.withdrawal.service.WithdrawalService;
import com.tradex.withdrawal.service.WithdrawalService.RequestWithdrawalResult;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WithdrawalResponse>> createWithdrawal(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody CreateWithdrawalRequest request
    ) {
        RequestWithdrawalResult result = withdrawalService.requestWithdrawal(principal.getId(), request);
        HttpStatus status = result.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        String message = result.isDuplicate() ? "Duplicate withdrawal request detected" : "Withdrawal request submitted successfully";
        return ResponseEntity.status(status).body(ApiResponse.success(message, result.response()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<WithdrawalResponse>>> getUserWithdrawals(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<WithdrawalResponse> withdrawals = withdrawalService.getUserWithdrawals(principal.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("User withdrawals retrieved successfully", withdrawals));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> getWithdrawalById(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        WithdrawalResponse withdrawal = withdrawalService.getWithdrawalById(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Withdrawal retrieved successfully", withdrawal));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<WithdrawalResponse>> cancelWithdrawal(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable UUID id
    ) {
        WithdrawalResponse response = withdrawalService.cancelWithdrawalUser(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Withdrawal cancelled successfully", response));
    }
}
