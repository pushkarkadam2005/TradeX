package com.tradex.portfolio.entity;

import com.tradex.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(
    name = "portfolio_positions",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_stock", columnNames = {"user_id", "stock_id"})
)
public class PortfolioPosition extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "quantity", nullable = false)
    private long quantity = 0L;

    @Column(name = "locked_quantity", nullable = false)
    private long lockedQuantity = 0L;

    @Column(name = "average_buy_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageBuyPrice = BigDecimal.ZERO.setScale(4);

    public PortfolioPosition() {}

    public PortfolioPosition(UUID userId, UUID stockId, String symbol) {
        this.userId = userId;
        this.stockId = stockId;
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
        this.quantity = 0L;
        this.lockedQuantity = 0L;
        this.averageBuyPrice = BigDecimal.ZERO.setScale(4);
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getStockId() {
        return stockId;
    }

    public void setStockId(UUID stockId) {
        this.stockId = stockId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol != null ? symbol.toUpperCase().trim() : null;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getLockedQuantity() {
        return lockedQuantity;
    }

    public void setLockedQuantity(long lockedQuantity) {
        this.lockedQuantity = lockedQuantity;
    }

    public long getAvailableQuantity() {
        return Math.max(0, this.quantity - this.lockedQuantity);
    }

    public BigDecimal getAverageBuyPrice() {
        return averageBuyPrice;
    }

    public void setAverageBuyPrice(BigDecimal averageBuyPrice) {
        this.averageBuyPrice = averageBuyPrice != null ? averageBuyPrice.setScale(4, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(4);
    }

    public void reserveShares(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("Share reservation quantity must be strictly positive");
        }
        if (getAvailableQuantity() < qty) {
            throw new IllegalStateException("Insufficient available shares for reservation");
        }
        this.lockedQuantity += qty;
    }

    public void releaseShares(long qty) {
        if (qty <= 0) {
            return;
        }
        long releaseAmount = Math.min(this.lockedQuantity, qty);
        this.lockedQuantity -= releaseAmount;
    }

    public void deductLockedShares(long qty) {
        if (qty <= 0) {
            return;
        }
        long deductAmount = Math.min(this.lockedQuantity, qty);
        this.lockedQuantity -= deductAmount;
        this.quantity -= qty;
        if (this.quantity < 0) {
            this.quantity = 0L;
        }
    }

    public void addSharesOnBuy(long fillQty, BigDecimal fillPrice) {
        if (fillQty <= 0 || fillPrice == null) {
            return;
        }

        BigDecimal oldTotalCost = BigDecimal.valueOf(this.quantity).multiply(this.averageBuyPrice);
        BigDecimal fillCost = BigDecimal.valueOf(fillQty).multiply(fillPrice);
        long newTotalQty = this.quantity + fillQty;

        BigDecimal newAvgPrice = oldTotalCost.add(fillCost)
            .divide(BigDecimal.valueOf(newTotalQty), 4, RoundingMode.HALF_UP);

        this.quantity = newTotalQty;
        this.averageBuyPrice = newAvgPrice;
    }
}
