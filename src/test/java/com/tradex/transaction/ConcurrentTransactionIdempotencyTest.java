package com.tradex.transaction;

import com.tradex.transaction.entity.TransactionRecord;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;
import com.tradex.transaction.repository.TransactionRepository;
import com.tradex.transaction.service.TransactionService;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTransactionIdempotencyTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("conc_tx_" + UUID.randomUUID() + "@tradex.com", "passhash", "Conc Tx User", Role.ROLE_USER));
    }

    @Test
    @DisplayName("Concurrent Transaction Recording — 20 concurrent threads with identical idempotencyKey result in exactly 1 DB record")
    void concurrentDuplicateRecordings() throws Exception {
        int threads = 20;
        String idempotencyKey = "conc-tx-key-" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Optional<TransactionRecord> recordOpt = transactionService.recordTransaction(
                        user.getId(), TransactionType.BUY, TransactionStatus.COMPLETED, new BigDecimal("100.0000"), "USD",
                        "ORDER", UUID.randomUUID(), idempotencyKey, "Concurrent idempotency test"
                    );
                    if (recordOpt.isPresent()) {
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

        // Verify only 1 record exists in database
        assertThat(transactionRepository.existsByIdempotencyKey(idempotencyKey)).isTrue();
        assertThat(transactionRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
    }
}
