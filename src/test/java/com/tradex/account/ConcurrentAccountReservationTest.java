package com.tradex.account;

import com.tradex.account.dto.AccountResponse;
import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
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
class ConcurrentAccountReservationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("concurrent_" + UUID.randomUUID() + "@tradex.com", "passhash", "Concurrent User", Role.ROLE_USER));
        accountService.depositAdmin(new DepositRequest(user.getId(), new BigDecimal("10000.0000"), "Initial $10k"));
    }

    @Test
    @DisplayName("Concurrent Account Reservation Audit — 20 simultaneous $1,000 reservations on $10,000 balance: Optimistic locking protects cash integrity without negative available balance")
    void concurrentAccountReservationsNeverExceedBalance() throws Exception {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    accountService.reserveFunds(user.getId(), new BigDecimal("1000.0000"), null);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        latch.countDown();
        done.await();
        executor.shutdown();

        AccountResponse finalAccount = accountService.getAccountByUserId(user.getId());

        // Cash invariants must be strictly preserved:
        assertThat(finalAccount.availableBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(finalAccount.lockedBalance()).isLessThanOrEqualTo(new BigDecimal("10000.0000"));

        BigDecimal total = finalAccount.availableBalance().add(finalAccount.lockedBalance());
        assertThat(total).isEqualByComparingTo("10000.0000");

        assertThat(successCount.get()).isGreaterThan(0);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threads);

        BigDecimal expectedLocked = new BigDecimal(successCount.get()).multiply(new BigDecimal("1000.0000"));
        assertThat(finalAccount.lockedBalance()).isEqualByComparingTo(expectedLocked);
    }
}
