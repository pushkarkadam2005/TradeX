package com.tradex.transaction.service;

import com.tradex.transaction.dto.TransactionResponse;
import com.tradex.transaction.entity.TransactionRecord;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;
import com.tradex.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Optional<TransactionRecord> recordTransaction(UUID userId, TransactionType type, TransactionStatus status,
                                                        BigDecimal amount, String currency, String referenceType,
                                                        UUID referenceId, String idempotencyKey, String description) {
        Optional<TransactionRecord> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent transaction log skip for key {}", idempotencyKey);
            return existing;
        }

        try {
            return Optional.of(doSaveTransaction(userId, type, status, amount, currency, referenceType, referenceId, idempotencyKey, description));
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent duplicate transaction idempotency key caught by DB constraint: {}", idempotencyKey);
            for (int i = 0; i < 5; i++) {
                Optional<TransactionRecord> saved = transactionRepository.findByIdempotencyKey(idempotencyKey);
                if (saved.isPresent()) {
                    return saved;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
            return transactionRepository.findByIdempotencyKey(idempotencyKey);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionRecord doSaveTransaction(UUID userId, TransactionType type, TransactionStatus status,
                                               BigDecimal amount, String currency, String referenceType,
                                               UUID referenceId, String idempotencyKey, String description) {
        TransactionRecord record = new TransactionRecord(
            userId, type, status, amount, currency, referenceType, referenceId, idempotencyKey, description
        );
        return transactionRepository.saveAndFlush(record);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getUserTransactions(UUID userId, TransactionType type, Pageable pageable) {
        if (type != null) {
            return transactionRepository.findByUserIdAndTransactionType(userId, type, pageable)
                .map(TransactionResponse::fromEntity);
        }
        return transactionRepository.findByUserId(userId, pageable)
            .map(TransactionResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDailyTradingValueForUser(UUID userId) {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal val = transactionRepository.calculateDailyTradingValueForUser(userId, startOfDay);
        return (val != null && val.compareTo(BigDecimal.ZERO) > 0) ? val : BigDecimal.ZERO;
    }
}
