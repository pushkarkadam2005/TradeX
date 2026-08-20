package com.tradex.aml.provider;

import com.tradex.aml.model.AmlScreeningResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockAmlScreeningProvider implements AmlScreeningProvider {

    @Override
    public AmlScreeningResult screenUser(UUID userId, UUID referenceId, BigDecimal amount) {
        if (amount != null && amount.compareTo(new BigDecimal("1000000.0000")) > 0) {
            return AmlScreeningResult.block(userId, referenceId, "Transaction amount exceeds suspicious transaction threshold");
        }
        return AmlScreeningResult.allow(userId, referenceId);
    }
}
