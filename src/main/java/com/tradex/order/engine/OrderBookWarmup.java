package com.tradex.order.engine;

import com.tradex.order.entity.Order;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import com.tradex.order.model.BookOrder;
import com.tradex.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderBookWarmup implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(OrderBookWarmup.class);

    private final OrderRepository orderRepository;
    private final OrderBookRegistry registry;

    public OrderBookWarmup(OrderRepository orderRepository, OrderBookRegistry registry) {
        this.orderRepository = orderRepository;
        this.registry = registry;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            long maxSeq = orderRepository.findMaxOrderSequence();
            registry.initializeMaxSequence(maxSeq);
            log.info("Initialized global order_sequence counter to {}", maxSeq);

            List<Order> activeOrders = orderRepository.findByStatusInOrderByOrderSequenceAsc(
                List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED)
            );

            int restoredCount = 0;
            for (Order order : activeOrders) {
                if (order.getOrderType() == OrderType.LIMIT) {
                    BookOrder bookOrder = BookOrder.fromEntity(order);
                    OrderBook book = registry.getOrderBook(order.getSymbol());
                    book.addRestingOrder(bookOrder);
                    restoredCount++;
                }
            }
            log.info("Restored {} active limit orders into OrderBookRegistry on startup", restoredCount);
            registry.markInitialized();
        } catch (Exception e) {
            log.error("Failed to warmup OrderBookRegistry: {}", e.getMessage(), e);
        }
    }
}
