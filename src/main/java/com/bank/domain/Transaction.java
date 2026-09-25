package com.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.bank.util.Money;

public class Transaction {
    private final Long id;
    private final String accountId;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String relatedAccountId;
    private final String description;
    private final Instant createdAt;

    //Transaction uses 2 optional fields due to only some transactions being transferrable to other IDs and the description also being an option of what you want to say.
    public Transaction(Long id, String accountId, TransactionType type, BigDecimal amount, String relatedAccountId, String description, Instant createdAt) {
        this.id = id;
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Money.scale(Objects.requireNonNull(amount, "amount"));
        this.relatedAccountId = relatedAccountId;
        this.description = description;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
    
    public Optional<Long> getId() {
        return Optional.ofNullable(id);
    }

    public String getAccountId() {
        return accountId;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }
    // Set on transfers and empty for a regular deposit or withdrawl
    public Optional<String> getRelatedAccountId() {
        return Optional.ofNullable(relatedAccountId);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
