package com.tradex.stock.service;

import com.tradex.common.exception.BusinessRuleViolationException;
import com.tradex.common.exception.ResourceNotFoundException;
import com.tradex.stock.dto.StockPriceResponse;
import com.tradex.stock.dto.StockResponse;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockPriceCacheService cacheService;

    public StockService(StockRepository stockRepository, StockPriceCacheService cacheService) {
        this.stockRepository = stockRepository;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getAllTradableStocks() {
        return stockRepository.findByTradableTrue().stream()
            .map(StockResponse::fromEntity)
            .toList();
    }

    @Transactional(readOnly = true)
    public Stock getStockEntityBySymbol(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        return stockRepository.findBySymbol(normalizedSymbol)
            .orElseThrow(() -> new ResourceNotFoundException("Stock", "symbol", normalizedSymbol));
    }

    @Transactional(readOnly = true)
    public StockResponse getStockBySymbol(String symbol) {
        return StockResponse.fromEntity(getStockEntityBySymbol(symbol));
    }

    @Transactional(readOnly = true)
    public StockPriceResponse getCurrentPrice(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        // 1. Try Redis Cache read
        Optional<BigDecimal> cachedPrice = cacheService.getPrice(normalizedSymbol);
        if (cachedPrice.isPresent()) {
            return new StockPriceResponse(normalizedSymbol, cachedPrice.get(), "REDIS_CACHE");
        }

        // 2. Cache miss -> query PostgreSQL database (source of truth)
        Stock stock = getStockEntityBySymbol(normalizedSymbol);
        BigDecimal currentPrice = stock.getCurrentPrice();

        // 3. Populate Redis Cache safely
        cacheService.putPrice(normalizedSymbol, currentPrice);

        return new StockPriceResponse(normalizedSymbol, currentPrice, "POSTGRESQL_DB");
    }

    @Transactional
    public StockResponse updateStockPrice(String symbol, BigDecimal newPrice) {
        if (newPrice == null || newPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleViolationException("INVALID_STOCK_PRICE", "Stock price must be strictly positive");
        }

        Stock stock = getStockEntityBySymbol(symbol);
        stock.setCurrentPrice(newPrice);
        Stock updatedStock = stockRepository.save(stock);

        // Evict Redis cache ONLY after PostgreSQL transaction commits successfully
        String symbolToEvict = updatedStock.getSymbol();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheService.evictPrice(symbolToEvict);
                }
            });
        } else {
            cacheService.evictPrice(symbolToEvict);
        }

        return StockResponse.fromEntity(updatedStock);
    }

    public String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BusinessRuleViolationException("INVALID_STOCK_SYMBOL", "Stock symbol cannot be empty");
        }
        return symbol.trim().toUpperCase();
    }
}
