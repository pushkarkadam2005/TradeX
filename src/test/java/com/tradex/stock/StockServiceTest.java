package com.tradex.stock;

import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.stock.dto.StockPriceResponse;
import com.tradex.stock.dto.StockResponse;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.stock.service.StockPriceCacheService;
import com.tradex.stock.service.StockService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockPriceCacheService cacheService;

    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(stockRepository, cacheService);
    }

    @Test
    @DisplayName("getCurrentPrice Cache Hit — returns price from Redis without DB query")
    void getCurrentPriceCacheHit() {
        BigDecimal cachedPrice = new BigDecimal("185.5000");
        given(cacheService.getPrice("AAPL")).willReturn(Optional.of(cachedPrice));

        StockPriceResponse response = stockService.getCurrentPrice("aapl");

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.currentPrice()).isEqualByComparingTo(cachedPrice);
        assertThat(response.source()).isEqualTo("REDIS_CACHE");
        verify(stockRepository, never()).findBySymbol(any());
    }

    @Test
    @DisplayName("getCurrentPrice Cache Miss — loads price from DB and populates Redis")
    void getCurrentPriceCacheMiss() {
        BigDecimal dbPrice = new BigDecimal("185.5000");
        Stock stock = new Stock("AAPL", "Apple Inc.", dbPrice, new BigDecimal("184.2500"), "Technology");
        stock.setId(UUID.randomUUID());

        given(cacheService.getPrice("AAPL")).willReturn(Optional.empty());
        given(stockRepository.findBySymbol("AAPL")).willReturn(Optional.of(stock));

        StockPriceResponse response = stockService.getCurrentPrice("aapl");

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.currentPrice()).isEqualByComparingTo(dbPrice);
        assertThat(response.source()).isEqualTo("POSTGRESQL_DB");

        verify(stockRepository).findBySymbol("AAPL");
        verify(cacheService).putPrice("AAPL", dbPrice);
    }

    @Test
    @DisplayName("Unknown stock symbol throws ResourceNotFoundException")
    void unknownSymbolThrowsNotFound() {
        given(cacheService.getPrice("UNKNOWN")).willReturn(Optional.empty());
        given(stockRepository.findBySymbol("UNKNOWN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.getCurrentPrice("UNKNOWN"))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Stock not found with symbol: 'UNKNOWN'");
    }

    @Test
    @DisplayName("updateStockPrice invalid price (<= 0) throws BusinessRuleViolationException")
    void updatePriceNegativeThrowsException() {
        assertThatThrownBy(() -> stockService.updateStockPrice("AAPL", new BigDecimal("-10.00")))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("strictly positive");

        assertThatThrownBy(() -> stockService.updateStockPrice("AAPL", BigDecimal.ZERO))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("updateStockPrice valid price updates DB and evicts Redis cache")
    void updatePriceValid() {
        BigDecimal newPrice = new BigDecimal("190.0000");
        Stock stock = new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology");
        stock.setId(UUID.randomUUID());

        given(stockRepository.findBySymbol("AAPL")).willReturn(Optional.of(stock));
        given(stockRepository.save(any(Stock.class))).willReturn(stock);

        StockResponse response = stockService.updateStockPrice("aapl", newPrice);

        assertThat(response.currentPrice()).isEqualByComparingTo(newPrice);
        verify(stockRepository).save(stock);
        verify(cacheService).evictPrice("AAPL");
    }
}
