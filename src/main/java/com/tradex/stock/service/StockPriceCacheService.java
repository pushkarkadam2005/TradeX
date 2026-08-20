package com.tradex.stock.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Service
public class StockPriceCacheService {

    private static final Logger log = LoggerFactory.getLogger(StockPriceCacheService.class);
    private static final String KEY_PREFIX = "tradex:stock:price:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public StockPriceCacheService(
        StringRedisTemplate redisTemplate,
        @Value("${tradex.cache.stock-price-ttl-seconds:60}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<BigDecimal> getPrice(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        String key = formatKey(symbol);
        try {
            String cachedValue = redisTemplate.opsForValue().get(key);
            if (cachedValue != null && !cachedValue.isBlank()) {
                return Optional.of(new BigDecimal(cachedValue));
            }
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for price cache read on key '{}': {}", key, e.getMessage());
        }
        return Optional.empty();
    }

    public void putPrice(String symbol, BigDecimal price) {
        if (symbol == null || price == null) {
            return;
        }
        String key = formatKey(symbol);
        try {
            redisTemplate.opsForValue().set(key, price.toPlainString(), ttl);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for price cache write on key '{}': {}", key, e.getMessage());
        }
    }

    public void evictPrice(String symbol) {
        if (symbol == null) {
            return;
        }
        String key = formatKey(symbol);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("Redis unavailable for price cache eviction on key '{}': {}", key, e.getMessage());
        }
    }

    public String formatKey(String symbol) {
        return KEY_PREFIX + symbol.trim().toUpperCase();
    }
}
