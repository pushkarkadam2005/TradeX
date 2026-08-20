package com.tradex.account.dto;

import com.tradex.account.entity.Account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    UUID userId,
    String currency,
    BigDecimal availableBalance,
    BigDecimal lockedBalance
) {
    public static AccountResponse fromEntity(Account account) {
        return new AccountResponse(
            account.getId(),
            account.getUserId(),
            account.getCurrency(),
            account.getAvailableBalance(),
            account.getLockedBalance()
        );
    }
}
