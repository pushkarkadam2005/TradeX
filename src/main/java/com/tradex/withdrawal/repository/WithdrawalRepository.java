package com.tradex.withdrawal.repository;

import com.tradex.withdrawal.entity.Withdrawal;
import com.tradex.withdrawal.enums.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WithdrawalRepository extends JpaRepository<Withdrawal, UUID> {

    Page<Withdrawal> findByUserId(UUID userId, Pageable pageable);

    Optional<Withdrawal> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    boolean existsByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Page<Withdrawal> findByStatus(WithdrawalStatus status, Pageable pageable);

    @Query("SELECT SUM(w.amount) FROM Withdrawal w WHERE w.userId = :userId AND w.createdAt >= :startInstant AND w.status NOT IN ('CANCELLED', 'REJECTED', 'FAILED')")
    BigDecimal calculateWithdrawalVolumeSince(@Param("userId") UUID userId, @Param("startInstant") Instant startInstant);
}
