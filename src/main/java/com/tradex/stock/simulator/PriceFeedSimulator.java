package com.tradex.stock.simulator;

import com.tradex.stock.dto.StockResponse;
import com.tradex.stock.service.StockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

@Component
@Profile("demo")
public class PriceFeedSimulator {

    private static final Logger log = LoggerFactory.getLogger(PriceFeedSimulator.class);
    private static final BigDecimal MIN_PRICE = new BigDecimal("1.0000");
    private static final BigDecimal MAX_PERCENT_CHANGE = new BigDecimal("0.005"); // Max 0.5% movement per tick

    private final StockService stockService;
    private final Random random = new Random();

    public PriceFeedSimulator(StockService stockService) {
        this.stockService = stockService;
    }

    @Scheduled(fixedRate = 2000)
    public void simulatePriceTicks() {
        try {
            List<StockResponse> stocks = stockService.getAllTradableStocks();
            for (StockResponse stock : stocks) {
                BigDecimal currentPrice = stock.currentPrice();
                BigDecimal updatedPrice = calculateNextPrice(currentPrice);
                stockService.updateStockPrice(stock.symbol(), updatedPrice);
            }
        } catch (Exception e) {
            log.warn("Price feed simulation tick encountered error: {}", e.getMessage());
        }
    }

    public BigDecimal calculateNextPrice(BigDecimal currentPrice) {
        if (currentPrice == null || currentPrice.compareTo(MIN_PRICE) < 0) {
            return MIN_PRICE;
        }

        // Random factor between -1.0 and +1.0
        double factor = (random.nextDouble() * 2.0) - 1.0;
        BigDecimal deltaFactor = MAX_PERCENT_CHANGE.multiply(BigDecimal.valueOf(factor));
        BigDecimal change = currentPrice.multiply(deltaFactor);
        BigDecimal nextPrice = currentPrice.add(change).setScale(4, RoundingMode.HALF_UP);

        if (nextPrice.compareTo(MIN_PRICE) < 0) {
            return MIN_PRICE;
        }
        return nextPrice;
    }
}
