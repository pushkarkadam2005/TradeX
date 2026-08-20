package com.tradex.account.service;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.entity.Account;
import com.tradex.account.repository.AccountRepository;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.ledger.service.LedgerService;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;
import com.tradex.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final LedgerService ledgerService;
    private final TransactionService transactionService;
    private final DomainEventPublisher eventPublisher;

    public AccountService(AccountRepository accountRepository,
                          LedgerService ledgerService,
                          TransactionService transactionService,
                          DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
        this.transactionService = transactionService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Account getOrCreateAccountEntity(UUID userId) {
        return accountRepository.findByUserId(userId)
            .orElseGet(() -> {
                log.info("Creating default trading account for userId {}", userId);
                Account account = new Account(userId, "USD");
                return accountRepository.save(account);
            });
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByUserId(UUID userId) {
        Account account = getOrCreateAccountEntity(userId);
        return AccountResponse.fromEntity(account);
    }

    @Transactional
    public void reserveFunds(UUID userId, BigDecimal amount, UUID orderId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("INVALID_RESERVATION_AMOUNT", "Reservation amount must be strictly positive");
        }

        Account account = getOrCreateAccountEntity(userId);
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new BusinessRuleViolationException("INSUFFICIENT_FUNDS",
                "Insufficient available balance. Required: $" + amount + ", Available: $" + account.getAvailableBalance());
        }

        BigDecimal before = account.getAvailableBalance();
        account.reserveFunds(amount);
        Account savedAccount = accountRepository.save(account);

        ledgerService.recordEntry(
            UUID.randomUUID(),
            savedAccount.getId(),
            "BUY_RESERVATION",
            amount,
            before,
            savedAccount.getAvailableBalance(),
            "ORDER",
            orderId,
            "Funds reserved for BUY order placement"
        );
    }

    @Transactional
    public void releaseFunds(UUID userId, BigDecimal amount, UUID orderId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return; // Nothing to release
        }

        Account account = getOrCreateAccountEntity(userId);
        if (account.getLockedBalance().compareTo(amount) < 0) {
            // Cap release at locked balance if edge case occurs
            amount = account.getLockedBalance();
        }

        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal before = account.getAvailableBalance();
        account.releaseFunds(amount);
        Account savedAccount = accountRepository.save(account);

        ledgerService.recordEntry(
            UUID.randomUUID(),
            savedAccount.getId(),
            "BUY_RELEASE",
            amount,
            before,
            savedAccount.getAvailableBalance(),
            "ORDER",
            orderId,
            "Funds released from cancelled or excess BUY order"
        );
    }

    @Transactional
    public void deductLockedFunds(UUID userId, BigDecimal amount, UUID referenceId, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Account account = getOrCreateAccountEntity(userId);
        BigDecimal before = account.getAvailableBalance();
        account.deductLockedFunds(amount);
        Account savedAccount = accountRepository.save(account);

        ledgerService.recordEntry(
            UUID.randomUUID(),
            savedAccount.getId(),
            "BUY_SETTLEMENT_DEDUCTION",
            amount,
            before,
            savedAccount.getAvailableBalance(),
            "SETTLEMENT",
            referenceId,
            description
        );
    }

    @Transactional
    public void creditAvailableFunds(UUID userId, BigDecimal amount, UUID referenceId, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Account account = getOrCreateAccountEntity(userId);
        BigDecimal before = account.getAvailableBalance();
        account.creditAvailableFunds(amount);
        Account savedAccount = accountRepository.save(account);

        ledgerService.recordEntry(
            UUID.randomUUID(),
            savedAccount.getId(),
            "SELL_SETTLEMENT_CREDIT",
            amount,
            before,
            savedAccount.getAvailableBalance(),
            "SETTLEMENT",
            referenceId,
            description
        );
    }

    @Transactional
    public AccountResponse depositAdmin(DepositRequest request) {
        Account account = getOrCreateAccountEntity(request.userId());
        BigDecimal before = account.getAvailableBalance();

        account.creditAvailableFunds(request.amount());
        Account savedAccount = accountRepository.save(account);

        ledgerService.recordEntry(
            UUID.randomUUID(),
            savedAccount.getId(),
            "ADMIN_DEPOSIT",
            request.amount(),
            before,
            savedAccount.getAvailableBalance(),
            "ADMIN",
            null,
            request.description() != null ? request.description() : "Administrative deposit"
        );

        String idempKey = "tx-deposit-" + savedAccount.getId() + "-" + System.nanoTime();
        transactionService.recordTransaction(
            request.userId(),
            TransactionType.DEPOSIT,
            TransactionStatus.COMPLETED,
            request.amount(),
            "USD",
            "ADMIN",
            null,
            idempKey,
            request.description() != null ? request.description() : "Administrative deposit"
        );

        eventPublisher.publish(DomainEvent.of(EventType.DEPOSIT_COMPLETED, "ACCOUNT", savedAccount.getId(),
            Map.of("userId", request.userId(), "price", request.amount())));

        return AccountResponse.fromEntity(savedAccount);
    }
}
