package com.bank.repository;

import com.bank.domain.Account;

import java.math.BigDecimal;
import java.util.Optional;

// SQL for accounts. The service should never be writing a query
public interface AccountRepository {
    void create(Account account);

    boolean existsById(String accountId);

    Optional<Account> findById(String accountId);

    void deposit(String accountId, BigDecimal amount);

    void withdraw(String accountId, BigDecimal amount);

    //Both sides of a transfer happen together, or neither does.
    void transfer(String fromAccountId, String toAccountId, BigDecimal amount);
}
