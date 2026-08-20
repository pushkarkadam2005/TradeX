package com.tradex.order;

import com.tradex.account.dto.DepositRequest;
import com.tradex.account.service.AccountService;
import com.tradex.order.dto.CreateOrderRequest;
import com.tradex.order.dto.OrderResponse;
import com.tradex.order.engine.OrderBookRegistry;
import com.tradex.order.enums.OrderSide;
import com.tradex.order.enums.OrderStatus;
import com.tradex.order.enums.OrderType;
import com.tradex.order.service.OrderService;
import com.tradex.order.service.OrderService.CreateOrderResult;
import com.tradex.portfolio.service.PortfolioService;
import com.tradex.stock.entity.Stock;
import com.tradex.stock.repository.StockRepository;
import com.tradex.user.entity.Role;
import com.tradex.user.entity.User;
import com.tradex.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MarketOrderLiquidityExhaustionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderBookRegistry registry;

    private UUID userId;
    private Stock stock;

    @BeforeEach
    void setUp() {
        registry.markInitialized();
        registry.reinitializeBook("AAPL");

        stock = stockRepository.findBySymbol("AAPL").orElseGet(() ->
            stockRepository.save(new Stock("AAPL", "Apple Inc.", new BigDecimal("185.5000"), new BigDecimal("184.2500"), "Technology"))
        );

        User user = new User("markettrader_" + UUID.randomUUID() + "@tradex.com", "passhash", "Market Trader", Role.ROLE_USER);
        userRepository.save(user);
        userId = user.getId();

        // Seed trader cash ($50,000) and shares (500 AAPL)
        accountService.depositAdmin(new DepositRequest(userId, new BigDecimal("50000.0000"), "Seed test cash"));
        portfolioService.addSharesOnBuy(userId, stock.getId(), "AAPL", 500, new BigDecimal("185.0000"));
    }

    @Test
    @DisplayName("Market Order with Zero Available Liquidity — Entire order is CANCELLED and never rests in OrderBook")
    void marketOrderWithNoLiquidityIsCancelled() {
        CreateOrderRequest marketReq = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 100, null, "client-mkt-01");
        CreateOrderResult result = orderService.createOrder(userId, marketReq);

        OrderResponse resp = result.orderResponse();
        assertThat(resp.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(resp.remainingQuantity()).isEqualTo(100);
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(0);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Market Order Partially Filled — Executed quantity filled, remaining unexecuted quantity CANCELLED")
    void marketOrderPartiallyFilledThenCancelled() {
        // Resting SELL limit order for 40 shares @ $185.00
        CreateOrderRequest sellLimit = new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 40, new BigDecimal("185.0000"), "client-sell-01");
        orderService.createOrder(userId, sellLimit);

        // Incoming MARKET BUY for 100 shares
        CreateOrderRequest marketBuy = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 100, null, "client-mkt-02");
        CreateOrderResult result = orderService.createOrder(userId, marketBuy);

        OrderResponse resp = result.orderResponse();
        assertThat(resp.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(resp.remainingQuantity()).isEqualTo(60); // 40 executed, 60 cancelled
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(0);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Market Order Fully Filled — Returns FILLED status")
    void marketOrderFullyFilledRemainsFilled() {
        // Resting SELL limit order for 100 shares @ $185.00
        CreateOrderRequest sellLimit = new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.LIMIT, 100, new BigDecimal("185.0000"), "client-sell-02");
        orderService.createOrder(userId, sellLimit);

        // Incoming MARKET BUY for 40 shares
        CreateOrderRequest marketBuy = new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, 40, null, "client-mkt-03");
        CreateOrderResult result = orderService.createOrder(userId, marketBuy);

        OrderResponse resp = result.orderResponse();
        assertThat(resp.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(resp.remainingQuantity()).isEqualTo(0);
        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(0);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(1); // 60 shares remaining resting SELL
    }

    @Test
    @DisplayName("Verify Market Order Never Rests in OrderBook — OrderBook counts stay zero after MARKET orders")
    void verifyMarketOrderNeverRestsInOrderBook() {
        CreateOrderRequest mktSell = new CreateOrderRequest("AAPL", OrderSide.SELL, OrderType.MARKET, 50, null, "client-mkt-04");
        orderService.createOrder(userId, mktSell);

        assertThat(registry.getOrderBook("AAPL").getBuyOrderCount()).isEqualTo(0);
        assertThat(registry.getOrderBook("AAPL").getSellOrderCount()).isEqualTo(0);
    }
}
