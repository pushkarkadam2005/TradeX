package com.tradex.account.entity;

import com.tradex.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "USD";

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance = BigDecimal.ZERO.setScale(4);

    @Column(name = "locked_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal lockedBalance = BigDecimal.ZERO.setScale(4);

    public Account() {}

    public Account(UUID userId, String currency) {
        this.userId = userId;
        this.currency = currency != null ? currency.toUpperCase().trim() : "USD";
        this.availableBalance = BigDecimal.ZERO.setScale(4);
        this.lockedBalance = BigDecimal.ZERO.setScale(4);
    }

    public Account(UUID userId, BigDecimal availableBalance) {
        this.userId = userId;
        this.currency = "USD";
        this.availableBalance = availableBalance != null ? availableBalance.setScale(4) : BigDecimal.ZERO.setScale(4);
        this.lockedBalance = BigDecimal.ZERO.setScale(4);
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance != null ? availableBalance.setScale(4) : BigDecimal.ZERO.setScale(4);
    }

    public BigDecimal getLockedBalance() {
        return lockedBalance;
    }

    public void setLockedBalance(BigDecimal lockedBalance) {
        this.lockedBalance = lockedBalance != null ? lockedBalance.setScale(4) : BigDecimal.ZERO.setScale(4);
    }

    public void reserveFunds(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reservation amount must be strictly positive");
        }
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient available balance for reservation");
        }
        this.availableBalance = this.availableBalance.subtract(amount);
        this.lockedBalance = this.lockedBalance.add(amount);
    }

    public void releaseFunds(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Release amount must be strictly positive");
        }
        if (this.lockedBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient locked balance for release");
        }
        this.lockedBalance = this.lockedBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }

    public void deductLockedFunds(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deduction amount must be strictly positive");
        }
        if (this.lockedBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient locked balance for deduction");
        }
        this.lockedBalance = this.lockedBalance.subtract(amount);
    }

    public void creditAvailableFunds(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be strictly positive");
        }
        this.availableBalance = this.availableBalance.add(amount);
    }
}
