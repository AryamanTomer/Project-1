package com.bank.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bank.domain.Transaction;
import com.bank.domain.TransactionType;
import com.bank.exception.DataAccessException;

// Newest transactions first.
public class TransactionRepositoryImpl implements TransactionRepository {
    private static final String FIND_RECENT_SQL = """
            SELECT id, account_id, type, amount, related_account_id, description, created_at
            FROM transactions
            WHERE account_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """;

    @Override
    public List<Transaction> findRecentByAccountId(String accountId, int limit) {
        try (Connection connection = ConnectionFactory.getConnectionFactory().getConnection();
             PreparedStatement statement = connection.prepareStatement(FIND_RECENT_SQL)) {
            statement.setString(1, accountId);
            statement.setInt(2, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>();
                while (resultSet.next()) {
                    transactions.add(mapTransaction(resultSet));
                }
                return List.copyOf(transactions);
            }
        } catch (SQLException e) {
            throw databaseError("Could not list transactions", e);
        }
    }

    // ResultSet -> Transaction.
    private Transaction mapTransaction(ResultSet resultSet) throws SQLException {
        Instant createdAt = Optional.ofNullable(resultSet.getTimestamp("created_at"))
                .map(timestamp -> timestamp.toInstant())
                .orElse(Instant.EPOCH);
        return new Transaction(
                resultSet.getLong("id"),
                resultSet.getString("account_id"),
                TransactionType.valueOf(resultSet.getString("type")),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("related_account_id"),
                resultSet.getString("description"),
                createdAt
        );
    }

    private DataAccessException databaseError(String message, SQLException cause) {
        return new DataAccessException(message, cause);
    }
}
