package com.tradex.transaction.controller;

import com.tradex.auth.security.UserPrincipal;
import com.tradex.common.dto.ApiResponse;
import com.tradex.transaction.dto.TransactionResponse;
import com.tradex.transaction.enums.TransactionType;
import com.tradex.transaction.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getUserTransactions(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(required = false) TransactionType type,
        @PageableDefault(size = 20, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<TransactionResponse> transactions = transactionService.getUserTransactions(principal.getId(), type, pageable);
        return ResponseEntity.ok(ApiResponse.success("User transaction activity history retrieved successfully", transactions));
    }
}
