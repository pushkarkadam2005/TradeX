package com.tradex.order.engine;

import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderType;
import com.tradex.order.model.BookOrder;
import com.tradex.order.model.Fill;
import com.tradex.order.model.MatchResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * In-memory price-time priority OrderBook for a single stock symbol.
 * Holds separate BUY and SELL books with price-time ordering.
 * Thread-safety is enforced externally by the per-symbol ReentrantLock in OrderBookRegistry.
 */
public class OrderBook {

    private final String symbol;

    // BUY book: Highest price first (descending order)
    private final TreeMap<BigDecimal, ArrayDeque<BookOrder>> buyBook = new TreeMap<>(Collections.reverseOrder());

    // SELL book: Lowest price first (ascending natural order)
    private final TreeMap<BigDecimal, ArrayDeque<BookOrder>> sellBook = new TreeMap<>(Comparator.naturalOrder());

    public OrderBook(String symbol) {
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
    }

    public String getSymbol() {
        return symbol;
    }

    /**
     * Executes synchronous matching for an incoming order against resting orders.
     */
    public MatchResult match(BookOrder incoming) {
        List<Fill> fills = new ArrayList<>();

        if (incoming.getSide() == OrderSide.BUY) {
            matchBuyOrder(incoming, fills);
        } else {
            matchSellOrder(incoming, fills);
        }

        // If a LIMIT order still has remaining quantity, place it in the resting book
        if (incoming.getRemainingQuantity() > 0 && incoming.getOrderType() == OrderType.LIMIT) {
            addRestingOrder(incoming);
        }

        boolean fullyMatched = (incoming.getRemainingQuantity() == 0);
        return new MatchResult(incoming, fills, fullyMatched);
    }

    private void matchBuyOrder(BookOrder incomingBuy, List<Fill> fills) {
        Iterator<Map.Entry<BigDecimal, ArrayDeque<BookOrder>>> priceLevelIterator = sellBook.entrySet().iterator();

        while (priceLevelIterator.hasNext() && incomingBuy.getRemainingQuantity() > 0) {
            Map.Entry<BigDecimal, ArrayDeque<BookOrder>> entry = priceLevelIterator.next();
            BigDecimal restingSellPrice = entry.getKey();

            // Price priority check for LIMIT BUY: incoming price must be >= resting SELL price
            if (incomingBuy.getOrderType() == OrderType.LIMIT && incomingBuy.getPrice().compareTo(restingSellPrice) < 0) {
                break; // No further price levels can match
            }

            ArrayDeque<BookOrder> queue = entry.getValue();
            Iterator<BookOrder> orderIterator = queue.iterator();

            while (orderIterator.hasNext() && incomingBuy.getRemainingQuantity() > 0) {
                BookOrder restingSell = orderIterator.next();
                long fillQty = Math.min(incomingBuy.getRemainingQuantity(), restingSell.getRemainingQuantity());
                BigDecimal executionPrice = restingSell.getPrice(); // Executed at resting price

                // Generate deterministic executionId = UUID.nameUUIDFromBytes(incomingId:restingId:price:qty)
                String executionIdString = incomingBuy.getOrderId() + ":" + restingSell.getOrderId() + ":"
                    + executionPrice.toPlainString() + ":" + fillQty;
                String deterministicExecutionId = UUID.nameUUIDFromBytes(executionIdString.getBytes(StandardCharsets.UTF_8)).toString();

                Fill fill = new Fill(
                    deterministicExecutionId,
                    incomingBuy.getOrderId(),
                    restingSell.getOrderId(),
                    incomingBuy.getStockId(),
                    incomingBuy.getUserId(),
                    restingSell.getUserId(),
                    symbol,
                    executionPrice,
                    fillQty,
                    Instant.now()
                );
                fills.add(fill);

                // Mutate remaining quantities
                incomingBuy.decrementRemaining(fillQty);
                restingSell.decrementRemaining(fillQty);

                if (restingSell.getRemainingQuantity() == 0) {
                    orderIterator.remove();
                }
            }

            if (queue.isEmpty()) {
                priceLevelIterator.remove();
            }
        }
    }

    private void matchSellOrder(BookOrder incomingSell, List<Fill> fills) {
        Iterator<Map.Entry<BigDecimal, ArrayDeque<BookOrder>>> priceLevelIterator = buyBook.entrySet().iterator();

        while (priceLevelIterator.hasNext() && incomingSell.getRemainingQuantity() > 0) {
            Map.Entry<BigDecimal, ArrayDeque<BookOrder>> entry = priceLevelIterator.next();
            BigDecimal restingBuyPrice = entry.getKey();

            // Price priority check for LIMIT SELL: incoming price must be <= resting BUY price
            if (incomingSell.getOrderType() == OrderType.LIMIT && incomingSell.getPrice().compareTo(restingBuyPrice) > 0) {
                break; // No further price levels can match
            }

            ArrayDeque<BookOrder> queue = entry.getValue();
            Iterator<BookOrder> orderIterator = queue.iterator();

            while (orderIterator.hasNext() && incomingSell.getRemainingQuantity() > 0) {
                BookOrder restingBuy = orderIterator.next();
                long fillQty = Math.min(incomingSell.getRemainingQuantity(), restingBuy.getRemainingQuantity());
                BigDecimal executionPrice = restingBuy.getPrice(); // Executed at resting price

                // Generate deterministic executionId
                String executionIdString = restingBuy.getOrderId() + ":" + incomingSell.getOrderId() + ":"
                    + executionPrice.toPlainString() + ":" + fillQty;
                String deterministicExecutionId = UUID.nameUUIDFromBytes(executionIdString.getBytes(StandardCharsets.UTF_8)).toString();

                Fill fill = new Fill(
                    deterministicExecutionId,
                    restingBuy.getOrderId(),
                    incomingSell.getOrderId(),
                    incomingSell.getStockId(),
                    restingBuy.getUserId(),
                    incomingSell.getUserId(),
                    symbol,
                    executionPrice,
                    fillQty,
                    Instant.now()
                );
                fills.add(fill);

                // Mutate remaining quantities
                incomingSell.decrementRemaining(fillQty);
                restingBuy.decrementRemaining(fillQty);

                if (restingBuy.getRemainingQuantity() == 0) {
                    orderIterator.remove();
                }
            }

            if (queue.isEmpty()) {
                priceLevelIterator.remove();
            }
        }
    }

    public void addRestingOrder(BookOrder order) {
        if (order.getSide() == OrderSide.BUY) {
            buyBook.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
        } else {
            sellBook.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
        }
    }

    public boolean cancelOrder(UUID orderId) {
        if (removeFromBook(buyBook, orderId)) {
            return true;
        }
        return removeFromBook(sellBook, orderId);
    }

    private boolean removeFromBook(TreeMap<BigDecimal, ArrayDeque<BookOrder>> book, UUID orderId) {
        Iterator<Map.Entry<BigDecimal, ArrayDeque<BookOrder>>> priceIterator = book.entrySet().iterator();
        while (priceIterator.hasNext()) {
            Map.Entry<BigDecimal, ArrayDeque<BookOrder>> entry = priceIterator.next();
            ArrayDeque<BookOrder> queue = entry.getValue();
            Iterator<BookOrder> orderIterator = queue.iterator();
            while (orderIterator.hasNext()) {
                BookOrder order = orderIterator.next();
                if (order.getOrderId().equals(orderId)) {
                    orderIterator.remove();
                    if (queue.isEmpty()) {
                        priceIterator.remove();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public int getBuyOrderCount() {
        return buyBook.values().stream().mapToInt(ArrayDeque::size).sum();
    }

    public int getSellOrderCount() {
        return sellBook.values().stream().mapToInt(ArrayDeque::size).sum();
    }
}
