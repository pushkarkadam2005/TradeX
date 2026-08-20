package com.tradex.transaction.repository;

import com.tradex.transaction.entity.TransactionRecord;
import com.tradex.transaction.enums.TransactionType;
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
public interface TransactionRepository extends JpaRepository<TransactionRecord, UUID> {

    Optional<TransactionRecord> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<TransactionRecord> findByUserId(UUID userId, Pageable pageable);

    Page<TransactionRecord> findByUserIdAndTransactionType(UUID userId, TransactionType transactionType, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN t.transactionType = 'RESERVATION' THEN t.amount 
                WHEN t.transactionType = 'RELEASE' THEN -t.amount 
                ELSE 0 
            END
        ), 0) 
        FROM TransactionRecord t 
        WHERE t.userId = :userId 
          AND t.createdAt >= :startOfDay 
          AND t.transactionStatus = 'COMPLETED'
    """)
    BigDecimal calculateDailyTradingValueForUser(@Param("userId") UUID userId, @Param("startOfDay") Instant startOfDay);
}
