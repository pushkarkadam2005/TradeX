package com.tradex.order.engine;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class OrderBookRegistry {

    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final AtomicLong globalSequence = new AtomicLong(0);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public boolean isInitialized() {
        return initialized.get();
    }

    public void markInitialized() {
        initialized.set(true);
    }

    public OrderBook getOrderBook(String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        return books.computeIfAbsent(normalizedSymbol, OrderBook::new);
    }

    public void reinitializeBook(String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        ReentrantLock lock = getLock(normalizedSymbol);
        lock.lock();
        try {
            books.put(normalizedSymbol, new OrderBook(normalizedSymbol));
        } finally {
            lock.unlock();
        }
    }

    public ReentrantLock getLock(String symbol) {
        String normalizedSymbol = symbol.trim().toUpperCase();
        return locks.computeIfAbsent(normalizedSymbol, s -> new ReentrantLock());
    }

    public long nextSequence() {
        return globalSequence.incrementAndGet();
    }

    public void initializeMaxSequence(long maxSeq) {
        globalSequence.updateAndGet(current -> Math.max(current, maxSeq));
    }
}
