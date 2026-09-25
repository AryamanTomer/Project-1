package com.bank.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.bank.util.Money;

// This is representative of 1 bank account and pinHash is a BCrypt String.
// A bcrypt string is a specially formatted text output that securely stores a hashed password
// It's commonly used for securely storing passwords in databases.
public class Account {
    private final String accountId;
    private final String pinHash;
    private final BigDecimal balance;
    // Instant is used to get the current timestamp in the UTC format.
    private final Instant createdAt;

    public Account(String accountId, String pinHash, BigDecimal balance, Instant createdAt) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.pinHash = Objects.requireNonNull(pinHash, "pinHash");
        this.balance = Money.scale(Objects.requireNonNull(balance, "balance"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getAccountId() {
        return accountId;
    }

    public String getPinHash() {
        return pinHash;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
