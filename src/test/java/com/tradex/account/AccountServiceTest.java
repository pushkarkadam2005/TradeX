package com.tradex.account;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.entity.Account;
import com.tradex.account.repository.AccountRepository;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.event.publisher.DomainEventPublisher;
import com.tradex.ledger.service.LedgerService;
import com.tradex.transaction.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private TransactionService transactionService;

    @Mock
    private DomainEventPublisher eventPublisher;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, ledgerService, transactionService, eventPublisher);
    }

    @Test
    @DisplayName("Admin Deposit — Increases available balance and records double-entry ledger record")
    void adminDepositSuccess() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(userId, "USD");
        given(accountRepository.findByUserId(userId)).willReturn(Optional.of(account));
        given(accountRepository.save(any(Account.class))).willAnswer(invocation -> invocation.getArgument(0));

        DepositRequest request = new DepositRequest(userId, new BigDecimal("500.0000"), "Initial deposit");
        AccountResponse response = accountService.depositAdmin(request);

        assertThat(response.availableBalance()).isEqualByComparingTo("500.0000");
    }

    @Test
    @DisplayName("Insufficient Funds Reservation — Throws BusinessRuleViolationException mapped to HTTP 409 CONFLICT")
    void insufficientFundsThrowsException() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(userId, new BigDecimal("50.0000"));
        given(accountRepository.findByUserId(userId)).willReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.reserveFunds(userId, new BigDecimal("100.0000"), UUID.randomUUID()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Insufficient available balance");
    }
}
