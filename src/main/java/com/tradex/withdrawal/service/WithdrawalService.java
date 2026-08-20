package com.tradex.withdrawal.service;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.compliance.model.ComplianceDecision;
import com.tradex.compliance.service.ComplianceService;
import com.tradex.event.model.DomainEvent;
import com.tradex.event.model.EventType;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;
import com.tradex.transaction.service.TransactionService;
import com.tradex.withdrawal.config.WithdrawalProperties;
import com.tradex.withdrawal.dto.CreateWithdrawalRequest;
import com.tradex.withdrawal.dto.WithdrawalResponse;
import com.tradex.withdrawal.entity.Withdrawal;
import com.tradex.withdrawal.enums.WithdrawalStatus;
import com.tradex.withdrawal.provider.WithdrawalProvider;
import com.tradex.withdrawal.repository.WithdrawalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);

    private final WithdrawalRepository withdrawalRepository;
    private final AccountService accountService;
    private final ComplianceService complianceService;
    private final TransactionService transactionService;
    private final WithdrawalProperties withdrawalProperties;
    private final WithdrawalProvider withdrawalProvider;
    private final DomainEventPublisher eventPublisher;

    public WithdrawalService(WithdrawalRepository withdrawalRepository,
                             AccountService accountService,
                             ComplianceService complianceService,
                             TransactionService transactionService,
                             WithdrawalProperties withdrawalProperties,
                             WithdrawalProvider withdrawalProvider,
                             DomainEventPublisher eventPublisher) {
        this.withdrawalRepository = withdrawalRepository;
        this.accountService = accountService;
        this.complianceService = complianceService;
        this.transactionService = transactionService;
        this.withdrawalProperties = withdrawalProperties;
        this.withdrawalProvider = withdrawalProvider;
        this.eventPublisher = eventPublisher;
    }

    public record RequestWithdrawalResult(WithdrawalResponse response, boolean isDuplicate) {}

    public RequestWithdrawalResult requestWithdrawal(UUID userId, CreateWithdrawalRequest request) {
        String idempKey = request.idempotencyKey().trim();

        // 1. Idempotency Pre-check
        Optional<Withdrawal> existing = withdrawalRepository.findByUserIdAndIdempotencyKey(userId, idempKey);
        if (existing.isPresent()) {
            log.info("Duplicate withdrawal request detected for userId {} and key {}", userId, idempKey);
            return new RequestWithdrawalResult(WithdrawalResponse.fromEntity(existing.get()), true);
        }

        try {
            Withdrawal saved = doCreateWithdrawal(userId, request, idempKey);
            return new RequestWithdrawalResult(WithdrawalResponse.fromEntity(saved), false);
        } catch (BusinessRuleViolationException brve) {
            throw brve;
        } catch (Exception e) {
            log.warn("Concurrent duplicate withdrawal request exception caught: {}. Checking for existing idempotency key record: {}", e.getMessage(), idempKey);
            for (int i = 0; i < 10; i++) {
                Optional<Withdrawal> retry = withdrawalRepository.findByUserIdAndIdempotencyKey(userId, idempKey);
                if (retry.isPresent()) {
                    return new RequestWithdrawalResult(WithdrawalResponse.fromEntity(retry.get()), true);
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
            Optional<Withdrawal> fallback = withdrawalRepository.findByUserIdAndIdempotencyKey(userId, idempKey);
            if (fallback.isPresent()) {
                return new RequestWithdrawalResult(WithdrawalResponse.fromEntity(fallback.get()), true);
            }
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public Withdrawal doCreateWithdrawal(UUID userId, CreateWithdrawalRequest request, String idempKey) {
        BigDecimal amount = request.amount();

        // 2. Validate Amount Limits
        if (amount.compareTo(withdrawalProperties.getMinimumAmount()) < 0) {
            throw new BusinessRuleViolationException("INVALID_WITHDRAWAL_AMOUNT",
                "Withdrawal amount ($" + amount + ") is below minimum allowed ($" + withdrawalProperties.getMinimumAmount() + ")");
        }
        if (amount.compareTo(withdrawalProperties.getMaximumAmount()) > 0) {
            throw new BusinessRuleViolationException("WITHDRAWAL_LIMIT_EXCEEDED",
                "Withdrawal amount ($" + amount + ") exceeds maximum allowed single withdrawal ($" + withdrawalProperties.getMaximumAmount() + ")");
        }

        // 3. Compliance Eligibility Check (KYC & AML)
        ComplianceDecision compliance = complianceService.checkWithdrawalEligibility(userId, amount, null);
        if (!compliance.isApproved()) {
            throw new BusinessRuleViolationException(compliance.ruleCode(), compliance.reason());
        }

        // 4. Daily & Monthly Volume Limits
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal currentDaily = withdrawalRepository.calculateWithdrawalVolumeSince(userId, startOfDay);
        if (currentDaily == null) currentDaily = BigDecimal.ZERO;
        if (currentDaily.add(amount).compareTo(withdrawalProperties.getDailyLimit()) > 0) {
            throw new BusinessRuleViolationException("WITHDRAWAL_LIMIT_EXCEEDED",
                "Projected daily withdrawal limit ($" + currentDaily.add(amount) + ") exceeds daily limit ($" + withdrawalProperties.getDailyLimit() + ")");
        }

        Instant startOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal currentMonthly = withdrawalRepository.calculateWithdrawalVolumeSince(userId, startOfMonth);
        if (currentMonthly == null) currentMonthly = BigDecimal.ZERO;
        if (currentMonthly.add(amount).compareTo(withdrawalProperties.getMonthlyLimit()) > 0) {
            throw new BusinessRuleViolationException("WITHDRAWAL_LIMIT_EXCEEDED",
                "Projected monthly withdrawal limit ($" + currentMonthly.add(amount) + ") exceeds monthly limit ($" + withdrawalProperties.getMonthlyLimit() + ")");
        }

        // 5. Validate Available Balance in Account FIRST before persisting entity
        AccountResponse account = accountService.getAccountByUserId(userId);
        if (account.availableBalance().compareTo(amount) < 0) {
            throw new BusinessRuleViolationException("INSUFFICIENT_FUNDS",
                "Insufficient available balance. Required: $" + amount + ", Available: $" + account.availableBalance());
        }

        // 6. Persist Withdrawal Entity to enforce unique(user_id, idempotency_key) DB constraint atomically
        Withdrawal withdrawal = new Withdrawal(userId, amount, "USD", idempKey, request.destinationReference());
        withdrawal.setComplianceDecision(compliance.outcome().name());
        Withdrawal saved = withdrawalRepository.saveAndFlush(withdrawal);

        // 7. Reserve Funds & Record Double-Entry Ledger Entry via AccountService
        accountService.reserveFunds(userId, amount, saved.getId());

        // 8. Record Transaction Activity Log
        transactionService.recordTransaction(
            userId, TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED, amount, "USD",
            "WITHDRAWAL", saved.getId(), "tx-wdr-" + idempKey, "Withdrawal request submitted"
        );

        // 9. Publish Domain Event Post-Commit
        eventPublisher.publish(DomainEvent.of(EventType.WITHDRAWAL_REQUESTED, "WITHDRAWAL", saved.getId(),
            Map.of("userId", userId, "amount", amount, "destination", request.destinationReference())));

        return saved;
    }

    @Transactional(readOnly = true)
    public Page<WithdrawalResponse> getUserWithdrawals(UUID userId, Pageable pageable) {
        return withdrawalRepository.findByUserId(userId, pageable)
            .map(WithdrawalResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public WithdrawalResponse getWithdrawalById(UUID id, UUID userId) {
        Withdrawal w = withdrawalRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", "id", id));
        if (!w.getUserId().equals(userId)) {
            throw new BusinessRuleViolationException("ACCESS_DENIED", "You are not authorized to view this withdrawal");
        }
        return WithdrawalResponse.fromEntity(w);
    }

    @Transactional
    public WithdrawalResponse cancelWithdrawalUser(UUID id, UUID userId) {
        Withdrawal w = withdrawalRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", "id", id));
        if (!w.getUserId().equals(userId)) {
            throw new BusinessRuleViolationException("ACCESS_DENIED", "You are not authorized to cancel this withdrawal");
        }
        if (w.getStatus() != WithdrawalStatus.REQUESTED && w.getStatus() != WithdrawalStatus.COMPLIANCE_REVIEW) {
            throw new BusinessRuleViolationException("INVALID_WITHDRAWAL_STATE", "Cannot cancel withdrawal in status " + w.getStatus());
        }

        // Release locked funds back to available balance
        accountService.releaseFunds(userId, w.getAmount(), w.getId());
        w.setStatus(WithdrawalStatus.CANCELLED);
        Withdrawal saved = withdrawalRepository.save(w);

        eventPublisher.publish(DomainEvent.of(EventType.WITHDRAWAL_REJECTED, "WITHDRAWAL", saved.getId(),
            Map.of("userId", userId, "amount", w.getAmount(), "reason", "User cancelled withdrawal")));

        return WithdrawalResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public Page<WithdrawalResponse> getPendingWithdrawalsAdmin(Pageable pageable) {
        return withdrawalRepository.findByStatus(WithdrawalStatus.REQUESTED, pageable)
            .map(WithdrawalResponse::fromEntity);
    }

    @Transactional
    public WithdrawalResponse approveWithdrawalAdmin(UUID id) {
        Withdrawal w = withdrawalRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", "id", id));
        if (w.getStatus() != WithdrawalStatus.REQUESTED && w.getStatus() != WithdrawalStatus.COMPLIANCE_REVIEW) {
            throw new BusinessRuleViolationException("INVALID_WITHDRAWAL_STATE", "Cannot approve withdrawal in status " + w.getStatus());
        }

        w.setStatus(WithdrawalStatus.APPROVED);
        Withdrawal saved = withdrawalRepository.save(w);

        // Process through withdrawal provider
        withdrawalProvider.processWithdrawal(w.getId(), w.getUserId(), w.getAmount(), w.getDestinationReference());

        // Deduct locked funds permanently
        accountService.deductLockedFunds(w.getUserId(), w.getAmount(), w.getId(), "Withdrawal approved and processed");
        saved.setStatus(WithdrawalStatus.COMPLETED);
        saved = withdrawalRepository.save(saved);

        eventPublisher.publish(DomainEvent.of(EventType.WITHDRAWAL_COMPLETED, "WITHDRAWAL", saved.getId(),
            Map.of("userId", saved.getUserId(), "amount", saved.getAmount())));

        return WithdrawalResponse.fromEntity(saved);
    }

    @Transactional
    public WithdrawalResponse rejectWithdrawalAdmin(UUID id, String reason) {
        Withdrawal w = withdrawalRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Withdrawal", "id", id));
        if (w.getStatus() != WithdrawalStatus.REQUESTED && w.getStatus() != WithdrawalStatus.COMPLIANCE_REVIEW) {
            throw new BusinessRuleViolationException("INVALID_WITHDRAWAL_STATE", "Cannot reject withdrawal in status " + w.getStatus());
        }

        // Release locked funds back to user
        accountService.releaseFunds(w.getUserId(), w.getAmount(), w.getId());
        w.setStatus(WithdrawalStatus.REJECTED);
        w.setRejectionReason(reason != null ? reason : "Administrative rejection");
        Withdrawal saved = withdrawalRepository.save(w);

        eventPublisher.publish(DomainEvent.of(EventType.WITHDRAWAL_REJECTED, "WITHDRAWAL", saved.getId(),
            Map.of("userId", saved.getUserId(), "amount", saved.getAmount(), "reason", saved.getRejectionReason())));

        return WithdrawalResponse.fromEntity(saved);
    }
}
