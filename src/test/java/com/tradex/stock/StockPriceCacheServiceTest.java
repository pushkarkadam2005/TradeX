package com.tradex.stock;

import com.tradex.stock.service.StockPriceCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockPriceCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private StockPriceCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new StockPriceCacheService(redisTemplate, 60);
    }

    @Test
    @DisplayName("Key format normalizes symbol to uppercase inside tradex:stock:price:{SYMBOL} namespace")
    void formatKeyNormalizesSymbol() {
        assertThat(cacheService.formatKey("aapl")).isEqualTo("tradex:stock:price:AAPL");
        assertThat(cacheService.formatKey(" msft ")).isEqualTo("tradex:stock:price:MSFT");
    }

    @Test
    @DisplayName("Cache hit returns BigDecimal price with exact scale from Redis string")
    void cacheHitReturnsPrice() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("tradex:stock:price:AAPL")).willReturn("185.5000");

        Optional<BigDecimal> price = cacheService.getPrice("aapl");

        assertThat(price).isPresent();
        assertThat(price.get()).isEqualByComparingTo("185.5000");
    }

    @Test
    @DisplayName("Cache miss returns empty Optional")
    void cacheMissReturnsEmpty() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("tradex:stock:price:AAPL")).willReturn(null);

        Optional<BigDecimal> price = cacheService.getPrice("AAPL");

        assertThat(price).isEmpty();
    }

    @Test
    @DisplayName("Redis DataAccessException on read falls back safely returning empty Optional without exception")
    void redisReadDataAccessExceptionFallback() {
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("Redis connection refused"));

        Optional<BigDecimal> price = cacheService.getPrice("AAPL");

        assertThat(price).isEmpty(); // Graceful fallback
    }

    @Test
    @DisplayName("Cache put stores exact toPlainString representation with TTL")
    void putPriceStoresStringWithTTL() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        BigDecimal price = new BigDecimal("185.5000");

        cacheService.putPrice("aapl", price);

        verify(valueOperations).set(eq("tradex:stock:price:AAPL"), eq("185.5000"), any(Duration.class));
    }

    @Test
    @DisplayName("Redis DataAccessException on write logs warning without throwing exception")
    void putPriceDataAccessExceptionHandledGracefully() {
        given(redisTemplate.opsForValue()).willThrow(new RedisConnectionFailureException("Redis connection error"));

        // Should not throw exception
        cacheService.putPrice("AAPL", new BigDecimal("185.5000"));
    }

    @Test
    @DisplayName("Redis DataAccessException on eviction logs warning without throwing exception")
    void evictPriceDataAccessExceptionHandledGracefully() {
        given(redisTemplate.delete("tradex:stock:price:AAPL"))
            .willThrow(new RedisConnectionFailureException("Redis connection error"));

        // Should not throw exception
        cacheService.evictPrice("AAPL");
    }

    @Test
    @DisplayName("Unexpected RuntimeException (e.g. NullPointerException) is NOT swallowed as a Redis failure")
    void unexpectedRuntimeExceptionNotSwallowed() {
        given(redisTemplate.opsForValue()).willThrow(new NullPointerException("Unexpected bug"));

        assertThatThrownBy(() -> cacheService.getPrice("AAPL"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("Unexpected bug");
    }
}
