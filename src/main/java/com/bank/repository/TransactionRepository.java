package com.bank.repository;

import com.bank.domain.Transaction;

import java.util.List;

// This will be reading the history of the transactions and will be using a list to write the rows in AccountRepository;
public interface TransactionRepository {
    List<Transaction> findRecentByAccountId(String accountId, int limit);
}
