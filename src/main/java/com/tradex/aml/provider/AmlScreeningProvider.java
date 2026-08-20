package com.tradex.aml.provider;

import com.tradex.aml.model.AmlScreeningResult;

import java.math.BigDecimal;
import java.util.UUID;

public interface AmlScreeningProvider {
    AmlScreeningResult screenUser(UUID userId, UUID referenceId, BigDecimal amount);
}
