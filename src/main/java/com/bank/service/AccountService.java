package com.bank.service;

import com.bank.domain.Account;
import com.bank.domain.Transaction;

import java.math.BigDecimal;
import java.util.List;

// Banking rules live here. No SQL in this layer.
public interface AccountService {
    Account register(String pin);

    // Wrong ID and wrong PIN should look the same to the user.
    Account login(String accountId, String pin);

    BigDecimal getBalance(String accountId);

    void deposit(String accountId, BigDecimal amount);

    void withdraw(String accountId, BigDecimal amount);

    void transfer(String fromAccountId, String toAccountId, BigDecimal amount);

    List<Transaction> getHistory(String accountId);
}
