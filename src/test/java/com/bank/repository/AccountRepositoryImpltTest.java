package com.bank.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bank.domain.Account;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.DataAccessException;
import com.bank.exception.InsufficientFundsException;
import com.bank.util.Money;

// Hits bank_cli_test, not the real bank_cli. Surefire sets bank.test.db=true.
class AccountRepositoryImplTest {
    private final AccountRepository accounts = new AccountRepositoryImpl();

    @BeforeEach
    void cleanTestDatabase() throws Exception {
        // Start each test with empty tables.
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM transactions");
            statement.execute("DELETE FROM accounts");
        }
    }

    @Test
    void create_insertsAccount() {
        accounts.create(account("10000001"));

        assertTrue(accounts.existsById("10000001"));
        assertEquals("10000001", accounts.findById("10000001").orElseThrow().getAccountId());
        assertEquals(new BigDecimal("0.00"), accounts.findById("10000001").orElseThrow().getBalance());
    }

    @Test
    void create_rejectsDuplicateAccountId() {
        accounts.create(account("10000001"));

        assertThrows(DataAccessException.class, () -> accounts.create(account("10000001")));
    }

    @Test
    void existsById_returnsTrueWhenAccountExists() {
        accounts.create(account("10000001"));

        assertTrue(accounts.existsById("10000001"));
    }

    @Test
    void existsById_returnsFalseWhenAccountIsMissing() {
        assertFalse(accounts.existsById("99999999"));
    }

    @Test
    void findById_returnsAccountWhenItExists() {
        accounts.create(account("10000001"));

        Account found = accounts.findById("10000001").orElseThrow();
        assertEquals("10000001", found.getAccountId());
    }

    @Test
    void findById_returnsEmptyWhenMissing() {
        assertTrue(accounts.findById("99999999").isEmpty());
    }

    @Test
    void deposit_addsFundsAndRecordsTransaction() throws Exception {
        accounts.create(account("10000001"));

        accounts.deposit("10000001", new BigDecimal("25.00"));

        assertEquals(new BigDecimal("25.00"), accounts.findById("10000001").orElseThrow().getBalance());
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                     "SELECT type, amount FROM transactions WHERE account_id = '10000001'"
             )) {
            assertTrue(resultSet.next());
            assertEquals("DEPOSIT", resultSet.getString("type"));
            assertEquals(new BigDecimal("25.00"), Money.scale(resultSet.getBigDecimal("amount")));
        }
    }

    @Test
    void deposit_rejectsUnknownAccount() {
        assertThrows(AccountNotFoundException.class,
                () -> accounts.deposit("10000001", new BigDecimal("25.00")));
    }

    @Test
    void withdraw_subtractsFundsAndRecordsTransaction() throws Exception {
        accounts.create(account("10000001", "40.00"));

        accounts.withdraw("10000001", new BigDecimal("15.00"));

        assertEquals(new BigDecimal("25.00"), accounts.findById("10000001").orElseThrow().getBalance());
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                     "SELECT type, amount FROM transactions WHERE account_id = '10000001'"
             )) {
            assertTrue(resultSet.next());
            assertEquals("WITHDRAWL", resultSet.getString("type"));
            assertEquals(new BigDecimal("15.00"), Money.scale(resultSet.getBigDecimal("amount")));
        }
    }

    @Test
    void withdraw_rejectsOverdraftAndLeavesBalanceUnchanged() {
        accounts.create(account("10000001", "10.00"));

        assertThrows(InsufficientFundsException.class,
                () -> accounts.withdraw("10000001", new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), accounts.findById("10000001").orElseThrow().getBalance());
    }

    @Test
    void transfer_movesFundsAtomically() {
        accounts.create(account("10000001", "100.00"));
        accounts.create(account("10000002", "20.00"));

        accounts.transfer("10000001", "10000002", new BigDecimal("30.00"));

        assertEquals(new BigDecimal("70.00"), accounts.findById("10000001").orElseThrow().getBalance());
        assertEquals(new BigDecimal("50.00"), accounts.findById("10000002").orElseThrow().getBalance());
    }

    @Test
    void transfer_rollsBackWhenSourceCannotCoverAmount() {
        accounts.create(account("10000001", "10.00"));
        accounts.create(account("10000002", "50.00"));

        assertThrows(InsufficientFundsException.class,
                () -> accounts.transfer("10000001", "10000002", new BigDecimal("25.00")));
        assertEquals(new BigDecimal("10.00"), accounts.findById("10000001").orElseThrow().getBalance());
        assertEquals(new BigDecimal("50.00"), accounts.findById("10000002").orElseThrow().getBalance());
    }

    private Account account(String id) {
        return account(id, "0.00");
    }

    private Account account(String id, String balance) {
        return new Account(id, "hash", new BigDecimal(balance), Instant.now());
    }
}
