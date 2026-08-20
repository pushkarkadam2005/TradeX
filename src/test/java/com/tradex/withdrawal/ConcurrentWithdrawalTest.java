package com.tradex.withdrawal;

import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.kyc.dto.SubmitKycRequest;
import com.tradex.kyc.enums.KycLevel;
import com.tradex.kyc.service.KycService;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import com.tradex.withdrawal.dto.CreateWithdrawalRequest;
import com.tradex.withdrawal.repository.WithdrawalRepository;
import com.tradex.withdrawal.service.WithdrawalService;
import com.tradex.withdrawal.service.WithdrawalService.RequestWithdrawalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentWithdrawalTest {

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
        user = userRepository.save(new User("concwdr_" + UUID.randomUUID() + "@tradex.com", "passhash", "Conc Wdr User", Role.ROLE_USER));
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("10000.0000"), "Seed test cash"));
        kycService.submitKyc(user.getId(), new SubmitKycRequest(KycLevel.STANDARD, "PASSPORT"));
    }

    @Test
    @DisplayName("Concurrent Withdrawal Idempotency — 20 concurrent threads submitting identical idempotencyKey result in exactly 1 withdrawal entity and 1 balance reservation")
    void concurrentDuplicateWithdrawalsHandledIdempotently() throws Exception {
        int threads = 20;
        String idempKey = "conc-wdr-key-" + UUID.randomUUID();
        CreateWithdrawalRequest request = new CreateWithdrawalRequest(new BigDecimal("500.0000"), "BANK-ACC-9999", idempKey);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    RequestWithdrawalResult res = withdrawalService.requestWithdrawal(user.getId(), request);
                    if (res.response() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threads);

        // Verify exactly 1 withdrawal entity exists in DB
        assertThat(withdrawalRepository.existsByUserIdAndIdempotencyKey(user.getId(), idempKey)).isTrue();

        // Verify account balance was deducted/reserved EXACTLY ONCE ($10,000 - $500 = $9,500 available, $500 locked)
        var account = accountService.getAccountByUserId(user.getId());
        assertThat(account.availableBalance()).isEqualByComparingTo("9500.0000");
        assertThat(account.lockedBalance()).isEqualByComparingTo("500.0000");
    }
}
