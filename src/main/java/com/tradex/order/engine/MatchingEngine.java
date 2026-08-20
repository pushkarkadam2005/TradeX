package com.tradex.order.engine;

import com.tradex.order.model.BookOrder;
import com.tradex.order.model.MatchResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
public class MatchingEngine {

    private final OrderBookRegistry registry;

    public MatchingEngine(OrderBookRegistry registry) {
        this.registry = registry;
    }

    public MatchResult match(BookOrder incomingOrder) {
        String symbol = incomingOrder.getSymbol();
        ReentrantLock lock = registry.getLock(symbol);
        lock.lock();
        try {
            OrderBook orderBook = registry.getOrderBook(symbol);
            return orderBook.match(incomingOrder);
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelOrder(String symbol, java.util.UUID orderId) {
        ReentrantLock lock = registry.getLock(symbol);
        lock.lock();
        try {
            OrderBook orderBook = registry.getOrderBook(symbol);
            return orderBook.cancelOrder(orderId);
        } finally {
            lock.unlock();
        }
    }
}
