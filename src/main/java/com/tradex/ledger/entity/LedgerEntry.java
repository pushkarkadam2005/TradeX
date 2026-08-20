package com.tradex.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable financial double-entry ledger record.
 * No UPDATE or DELETE operations are performed on ledger entries.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "entry_type", nullable = false, length = 30)
    private String entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "reference_type", nullable = false, length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public LedgerEntry() {}

    public LedgerEntry(UUID transactionId, UUID accountId, String entryType, BigDecimal amount,
                       BigDecimal balanceBefore, BigDecimal balanceAfter, String referenceType,
                       UUID referenceId, String description) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount != null ? amount.setScale(4) : BigDecimal.ZERO.setScale(4);
        this.balanceBefore = balanceBefore != null ? balanceBefore.setScale(4) : BigDecimal.ZERO.setScale(4);
        this.balanceAfter = balanceAfter != null ? balanceAfter.setScale(4) : BigDecimal.ZERO.setScale(4);
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public UUID getReferenceId() {
        return referenceId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
