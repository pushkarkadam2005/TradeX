package com.tradex.withdrawal.provider;

import java.math.BigDecimal;
import java.util.UUID;

public interface WithdrawalProvider {

    record WithdrawalProcessingResult(String providerReference, boolean success) {}

    WithdrawalProcessingResult processWithdrawal(UUID withdrawalId, UUID userId, BigDecimal amount, String destination);
}
