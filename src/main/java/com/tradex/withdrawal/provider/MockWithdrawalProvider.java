package com.tradex.withdrawal.provider;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockWithdrawalProvider implements WithdrawalProvider {

    @Override
    public WithdrawalProcessingResult processWithdrawal(UUID withdrawalId, UUID userId, BigDecimal amount, String destination) {
        String ref = "MOCK-PAYOUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new WithdrawalProcessingResult(ref, true);
    }
}
