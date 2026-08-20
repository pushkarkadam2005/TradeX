package com.tradex.ledger.service;

import com.tradex.ledger.dto.LedgerResponse;
import com.tradex.ledger.entity.LedgerEntry;
import com.tradex.ledger.repository.LedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public LedgerEntry recordEntry(UUID transactionId, UUID accountId, String entryType,
                                   BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                                   String referenceType, UUID referenceId, String description) {
        LedgerEntry entry = new LedgerEntry(
            transactionId, accountId, entryType, amount,
            balanceBefore, balanceAfter, referenceType, referenceId, description
        );
        return ledgerRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<LedgerResponse> getAccountLedger(UUID accountId) {
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
            .map(LedgerResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<LedgerResponse> getAccountLedgerPaginated(UUID accountId, Pageable pageable) {
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
            .map(LedgerResponse::fromEntity);
    }
}
