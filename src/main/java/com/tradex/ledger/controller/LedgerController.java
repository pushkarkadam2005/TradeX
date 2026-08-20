package com.tradex.ledger.controller;

import com.tradex.account.entity.Account;
import com.tradex.account.service.AccountService;
import com.tradex.auth.security.UserPrincipal;
import com.tradex.common.dto.ApiResponse;
import com.tradex.ledger.dto.LedgerResponse;
import com.tradex.ledger.service.LedgerService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public LedgerController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<LedgerResponse>>> getUserLedger(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Account account = accountService.getOrCreateAccountEntity(principal.getId());
        Page<LedgerResponse> ledger = ledgerService.getAccountLedgerPaginated(account.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Ledger entries retrieved successfully", ledger));
    }
}
