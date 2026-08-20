package com.tradex.withdrawal;

import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.kyc.dto.SubmitKycRequest;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.service.KycService;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import com.tradex.withdrawal.dto.CreateWithdrawalRequest;
import com.tradex.withdrawal.repository.WithdrawalRepository;
import com.tradex.withdrawal.service.WithdrawalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WithdrawalRollbackAtomicityTest {

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private WithdrawalRepository withdrawalRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private KycService kycService;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("wdrrb_" + UUID.randomUUID() + "@tradex.com", "passhash", "Wdr Rollback User", Role.ROLE_USER));
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("1000.0000"), "Initial balance"));
        kycService.submitKyc(user.getId(), new SubmitKycRequest(KycLevel.STANDARD, "PASSPORT"));
    }

    @Test
    @DisplayName("Withdrawal Atomicity — Invalid withdrawal amount causes complete transaction rollback with zero balance deduction or withdrawal creation")
    void failedWithdrawalRollsBackCompletely() {
        String idempKey = "wdr-rollback-key-1";
        // Attempt withdrawal of $5,000 when available balance is $1,000 -> Exceeds balance
        CreateWithdrawalRequest request = new CreateWithdrawalRequest(new BigDecimal("5000.0000"), "BANK-ACC-111", idempKey);

        assertThatThrownBy(() -> withdrawalService.requestWithdrawal(user.getId(), request))
            .isInstanceOf(BusinessRuleViolationException.class);

        // Verify withdrawal was NOT persisted
        assertThat(withdrawalRepository.existsByUserIdAndIdempotencyKey(user.getId(), idempKey)).isFalse();

        // Verify balance remained 100% intact ($1,000 available, $0 locked)
        var account = accountService.getAccountByUserId(user.getId());
        assertThat(account.availableBalance()).isEqualByComparingTo("1000.0000");
        assertThat(account.lockedBalance()).isEqualByComparingTo("0.0000");
    }
}
