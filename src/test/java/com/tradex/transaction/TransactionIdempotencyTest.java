package com.tradex.transaction;

import com.tradex.transaction.entity.TransactionRecord;
import com.tradex.transaction.enums.TransactionStatus;
import com.tradex.transaction.enums.TransactionType;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TransactionIdempotencyTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("idempuser_" + UUID.randomUUID() + "@tradex.com", "passhash", "Idemp User", Role.ROLE_USER));
    }

    @Test
    @DisplayName("Transaction Activity Idempotency — Duplicate recording attempts with same idempotencyKey return existing record without duplicate insertion")
    void duplicateIdempotencyKeyReturnsExistingRecord() {
        String idempotencyKey = "tx-key-idempotent-100";
        UUID refId = UUID.randomUUID();

        // Initial transaction recording
        Optional<TransactionRecord> firstOpt = transactionService.recordTransaction(
            user.getId(), TransactionType.BUY, TransactionStatus.COMPLETED, new BigDecimal("100.0000"), "USD",
            "ORDER", refId, idempotencyKey, "First placement"
        );
        assertThat(firstOpt).isPresent();

        // Duplicate recording attempt with identical idempotencyKey
        Optional<TransactionRecord> secondOpt = transactionService.recordTransaction(
            user.getId(), TransactionType.BUY, TransactionStatus.COMPLETED, new BigDecimal("100.0000"), "USD",
            "ORDER", refId, idempotencyKey, "Duplicate placement retry"
        );
        assertThat(secondOpt).isPresent();

        assertThat(firstOpt.get().getId()).isEqualTo(secondOpt.get().getId());
        assertThat(firstOpt.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
}
