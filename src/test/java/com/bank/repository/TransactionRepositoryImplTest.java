package com.bank.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bank.domain.Account;
import com.bank.domain.Transaction;
import com.bank.domain.TransactionType;

// History queries against bank_cli_test.
class TransactionRepositoryImplTest {
    private final AccountRepository accountRepository = new AccountRepositoryImpl();
    private final TransactionRepository transactionRepository = new TransactionRepositoryImpl();

    @BeforeEach
    void cleanTestDatabase() throws Exception {
        // Don't let leftover rows from the last test mess this up.
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM transactions");
            statement.execute("DELETE FROM accounts");
        }
    }

    @Test
    void findRecentByAccountId_returnsNewestTransactions() {
        accountRepository.create(new Account("10000001", "hash", new BigDecimal("0.00"), Instant.now()));
        accountRepository.deposit("10000001", new BigDecimal("25.00"));

        List<Transaction> history = transactionRepository.findRecentByAccountId("10000001", 20);

        assertEquals(1, history.size());
        assertEquals(TransactionType.DEPOSIT, history.get(0).getType());
        assertEquals(new BigDecimal("25.00"), history.get(0).getAmount());
    }

    @Test
    void findRecentByAccountId_returnsEmptyWhenNoneExist() {
        accountRepository.create(new Account("10000001", "hash", new BigDecimal("0.00"), Instant.now()));

        List<Transaction> history = transactionRepository.findRecentByAccountId("10000001", 20);

        assertTrue(history.isEmpty());
    }
}
