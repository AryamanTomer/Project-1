package com.bank.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

import com.bank.domain.Account;
import com.bank.domain.TransactionType;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.DataAccessException;
import com.bank.exception.InsufficientFundsException;
import com.bank.util.Money;



// These are the actual SQL Prepared statements only
// We will Deposit, Withdraw, Transfer here
public class AccountRepositoryImpl implements AccountRepository{
    private static final String CREATE_ACCOUNTS_SQL = """
            CREATE TABLE IF NOT EXISTS accounts (
                account_id VARCHAR(16) PRIMARY KEY,
                pin_hash VARCHAR(60) NOT NULL,
                balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;
    private static final String CREATE_TRANSACTIONS_SQL = """
            CREATE TABLE IF NOT EXISTS transactions (
                id BIGSERIAL PRIMARY KEY,
                account_id VARCHAR(16) NOT NULL REFERENCES accounts(account_id),
                type VARCHAR(20) NOT NULL,
                amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
                related_account_id VARCHAR(16),
                description VARCHAR(255),
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """;
    private static final String CREATE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_transactions_account_created
            ON transactions (account_id, created_at DESC)
            """;
    private static final String INSERT_SQL = """
            INSERT INTO accounts (account_id, pin_hash, balance, created_at)
            VALUES (?, ?, ?, ?)
            """;
    private static final String EXISTS_SQL = "SELECT 1 FROM accounts WHERE account_id = ?";
    private static final String FIND_BY_ID_SQL = """
            SELECT account_id, pin_hash, balance, created_at
            FROM accounts
            WHERE account_id = ?
            """;
    private static final String LOCK_BALANCE_SQL = "SELECT balance FROM accounts WHERE account_id = ? FOR UPDATE";
    private static final String UPDATE_BALANCE_SQL = "UPDATE accounts SET balance = ? WHERE account_id = ?";
    private static final String INSERT_TRANSACTION_SQL = """
            INSERT INTO transactions (account_id, type, amount, related_account_id, description)
            VALUES (?, ?, ?, ?, ?)
            """;

    public AccountRepositoryImpl() {
        // Make the tables if this is a brand new database
        initializeSchema();
    }

    @Override 
    public void create(Account account) {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, account.getAccountId());
            statement.setString(2, account.getPinHash());
            statement.setBigDecimal(3, Money.scale(account.getBalance()));
            statement.setTimestamp(4, Timestamp.from(account.getCreatedAt()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw databaseError("Could not create account", e);
        }
    }

    @Override
    public boolean existsById(String accountId) {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_SQL)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            throw databaseError("Could not check account existence", e);
        }
    }

     @Override
    public Optional<Account> findById(String accountId) {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_SQL)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapAccount(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw databaseError("Could not find account", e);
        }
    }

    @Override
    public void deposit(String accountId, BigDecimal amount) {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection()) {
            connection.setAutoCommit(false); // either the balance and the history row both stick, or neither does
            try {
                BigDecimal current = lockBalance(connection, accountId);
                updateBalance(connection, accountId, current.add(amount));
                insertTransaction(connection, accountId, TransactionType.DEPOSIT, amount, null, "Deposit");
                connection.commit();
            } catch (AccountNotFoundException e) {
                connection.rollback();
                throw e;
            } catch (SQLException e) {
                connection.rollback();
                throw databaseError("Could not deposit", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw databaseError("Could not deposit", e);
        }
    }

    @Override
    public void withdraw(String accountId, BigDecimal amount) {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal current = lockBalance(connection, accountId);
                if (current.compareTo(amount) < 0) {
                    throw new InsufficientFundsException();
                }
                updateBalance(connection, accountId, current.subtract(amount));
                insertTransaction(connection, accountId, TransactionType.WITHDRAWL, amount, null, "Withdrawal");
                connection.commit();
            } catch (AccountNotFoundException | InsufficientFundsException e) {
                connection.rollback();
                throw e;
            } catch (SQLException e) {
                connection.rollback();
                throw databaseError("Could not withdraw", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw databaseError("Could not withdraw", e);
        }
    }

    @Override
    public void transfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Always lock in the same order (smaller ID first) or two transfers can deadlock.
                String first = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
                String second = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;
                BigDecimal firstBalance = lockBalance(connection, first);
                BigDecimal secondBalance = lockBalance(connection, second);
                BigDecimal fromBalance = fromAccountId.equals(first) ? firstBalance : secondBalance;
                if (fromBalance.compareTo(amount) < 0) {
                    throw new InsufficientFundsException();
                }
                BigDecimal toBalance = toAccountId.equals(first) ? firstBalance : secondBalance;

                updateBalance(connection, fromAccountId, fromBalance.subtract(amount));
                updateBalance(connection, toAccountId, toBalance.add(amount));
                insertTransaction(
                        connection,
                        fromAccountId,
                        TransactionType.TRANSFER_OUT,
                        amount,
                        toAccountId,
                        "Transfer to " + toAccountId
                );
                insertTransaction(
                        connection,
                        toAccountId,
                        TransactionType.TRANSFER_IN,
                        amount,
                        fromAccountId,
                        "Transfer from " + fromAccountId
                );
                connection.commit();
            } catch (AccountNotFoundException | InsufficientFundsException e) {
                connection.rollback();
                throw e;
            } catch (SQLException e) {
                connection.rollback();
                throw databaseError("Could not transfer", e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw databaseError("Could not transfer", e);
        }
    }

    // First launch against an empty DB still works.
    private void initializeSchema() {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             PreparedStatement accounts = connection.prepareStatement(CREATE_ACCOUNTS_SQL);
             PreparedStatement transactions = connection.prepareStatement(CREATE_TRANSACTIONS_SQL);
             PreparedStatement index = connection.prepareStatement(CREATE_INDEX_SQL)) {
            accounts.executeUpdate();
            transactions.executeUpdate();
            index.executeUpdate();
        } catch (SQLException e) {
            throw databaseError("Could not initialize database schema", e);
        }
    }

    // ResultSet -> Account.
    private Account mapAccount(ResultSet resultSet) throws SQLException {
        return new Account(
                resultSet.getString("account_id"),
                resultSet.getString("pin_hash"),
                resultSet.getBigDecimal("balance"),
                resultSet.getTimestamp("created_at").toInstant()
        );
    }

    // SELECT ... FOR UPDATE. Throws if that ID isn't in the table.
    private BigDecimal lockBalance(Connection connection, String accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LOCK_BALANCE_SQL)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new AccountNotFoundException(accountId);
                }
                return Money.scale(resultSet.getBigDecimal("balance"));
            }
        }
    }

    private void updateBalance(Connection connection, String accountId, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_BALANCE_SQL)) {
            statement.setBigDecimal(1, Money.scale(newBalance));
            statement.setString(2, accountId);
            statement.executeUpdate();
        }
    }

    private void insertTransaction(
            Connection connection,
            String accountId,
            TransactionType type,
            BigDecimal amount,
            String relatedAccountId,
            String description
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_TRANSACTION_SQL)) {
            statement.setString(1, accountId);
            statement.setString(2, type.name());
            statement.setBigDecimal(3, Money.scale(amount));
            statement.setString(4, relatedAccountId);
            statement.setString(5, description);
            statement.executeUpdate();
        }
    }

    private DataAccessException databaseError(String message, SQLException cause) {
        return new DataAccessException(message, cause);
    }

}
