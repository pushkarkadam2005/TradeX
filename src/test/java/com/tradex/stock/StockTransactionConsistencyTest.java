package com.tradex.stock;

import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.stock.service.StockPriceCacheService;
import com.tradex.stock.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class StockTransactionConsistencyTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockBean
    private StockPriceCacheService cacheService;

    @BeforeEach
    void setUp() {
        if (stockRepository.count() == 0) {
            stockRepository.saveAll(List.of(
                new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"),
                new Stock("MSFT", "Microsoft Corporation", new BigDecimal("420.7500"), new BigDecimal("418.9000"), "Technology"),
                new Stock("TSLA", "Tesla, Inc.", new BigDecimal("178.3000"), new BigDecimal("176.5000"), "Consumer Cyclical")
            ));
        }
    }

    @Test
    @DisplayName("1 & 2. Transaction Consistency — Redis is evicted ONLY after DB transaction commits, NOT on rollback")
    void transactionCommitTriggersCacheEviction() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        // 1. Successful transaction commit -> triggers cacheService.evictPrice("MSFT")
        txTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                stockService.updateStockPrice("MSFT", new BigDecimal("425.0000"));
                // Before commit, evictPrice should NOT have been called yet
                verify(cacheService, never()).evictPrice("MSFT");
            }
        });

        // After transaction commit, evictPrice IS called!
        verify(cacheService).evictPrice("MSFT");

        // 2. Rolled-back transaction -> does NOT call evictPrice for TSLA
        try {
            txTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    stockService.updateStockPrice("TSLA", new BigDecimal("200.0000"));
                    status.setRollbackOnly(); // force rollback
                }
            });
        } catch (Exception ignored) {
        }

        // Cache eviction must NOT have been called for TSLA due to rollback
        verify(cacheService, never()).evictPrice("TSLA");
    }

    @Test
    @DisplayName("6. Optimistic Locking — Concurrent update on stale entity version throws OptimisticLockingFailureException")
    void optimisticLockingPreventsLostUpdate() {
        Stock stock = stockRepository.findBySymbol("AAPL").orElseThrow();

        // Simulate concurrent modification in a separate transaction
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            Stock concurrentCopy = stockRepository.findById(stock.getId()).orElseThrow();
            concurrentCopy.setCurrentPrice(new BigDecimal("190.0000"));
            stockRepository.save(concurrentCopy);
        });

        // Current in-memory stock object now has a stale version
        stock.setCurrentPrice(new BigDecimal("195.0000"));

        assertThatThrownBy(() -> {
            txTemplate.executeWithoutResult(status -> {
                stockRepository.save(stock);
            });
        }).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
