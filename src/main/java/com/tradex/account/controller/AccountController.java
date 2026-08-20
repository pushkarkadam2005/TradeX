package com.tradex.account.controller;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.auth.security.UserPrincipal;
import com.tradex.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AccountResponse>> getUserAccount(
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        AccountResponse account = accountService.getAccountByUserId(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Account details retrieved successfully", account));
    }

    @PostMapping("/deposit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> depositAdmin(
        @Valid @RequestBody DepositRequest request
    ) {
        AccountResponse updatedAccount = accountService.depositAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Deposit processed successfully", updatedAccount));
    }
}
