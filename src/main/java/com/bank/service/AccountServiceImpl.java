package com.bank.service;

import com.bank.domain.Account;
import com.bank.domain.Transaction;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.AuthenticationException;
import com.bank.exception.DataAccessException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.ServiceUnavailableException;
import com.bank.exception.ValidationException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.util.AccountIdGenerator;
import com.bank.util.AppLogger;
import com.bank.util.PinHasher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

// PIN checks, no overdraft, no sending money to yourself. SQL stays in the repos.
public class AccountServiceImpl implements AccountService {
    private static final int MAX_ID_ATTEMPTS = 20;
    static final int HISTORY_LIMIT = 20;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final Supplier<String> accountIdGenerator;

    public AccountServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this(accountRepository, transactionRepository, AccountIdGenerator::nextId);
    }

    // Tests pass in a fake ID generator so register() isn't random.
    AccountServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository, Supplier<String> accountIdGenerator) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accountIdGenerator = accountIdGenerator;
    }

    @Override
    public Account register(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new ValidationException("PIN must be exactly 4 digits.");
        }
        return callWithDatabase(() -> {
            String accountId = allocateAccountId();
            Account account = new Account(
                    accountId,
                    PinHasher.hash(pin),
                    new BigDecimal("0.00"),
                    Instant.now()
            );
            accountRepository.create(account);
            AppLogger.info("User successfully registered account " + accountId);
            return account;
        });
    }

    @Override
    public Account login(String accountId, String pin) {
        if (accountId == null || accountId.isBlank()) {
            throw new ValidationException("Please enter your Account ID.");
        }
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new ValidationException("PIN must be exactly 4 digits.");
        }
        return callWithDatabase(() -> {
            Account account = accountRepository.findById(accountId.trim()).orElse(null);
            // Don't tell them whether the ID or the PIN was the problem.
            if (account == null || !PinHasher.verify(pin, account.getPinHash())) {
                AppLogger.error("Incorrect PIN entered for account " + accountId);
                throw new AuthenticationException();
            }
            AppLogger.info("User successfully logged in: " + account.getAccountId());
            return account;
        });
    }

    @Override
    public BigDecimal getBalance(String accountId) {
        return callWithDatabase(() -> {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            AppLogger.info("User " + accountId + " checked account balance");
            return account.getBalance();
        });
    }

    @Override
    public void deposit(String accountId, BigDecimal amount) {
        BigDecimal validAmount = requirePositiveAmount(amount);
        runWithDatabase(() -> {
            accountRepository.deposit(accountId, validAmount);
            AppLogger.info("User " + accountId + " successfully deposited " + validAmount);
        });
    }

    @Override
    public void withdraw(String accountId, BigDecimal amount) {
        BigDecimal validAmount = requirePositiveAmount(amount);
        runWithDatabase(() -> {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            // Stop here so we never even call withdraw.
            if (account.getBalance().compareTo(validAmount) < 0) {
                AppLogger.error("Withdrawal rejected for " + accountId + ": insufficient funds");
                throw new InsufficientFundsException();
            }
            accountRepository.withdraw(accountId, validAmount);
            AppLogger.info("User " + accountId + " successfully withdrew " + validAmount);
        });
    }

    @Override
    public void transfer(String fromAccountId, String toAccountId, BigDecimal amount) {
        BigDecimal validAmount = requirePositiveAmount(amount);
        if (toAccountId == null || toAccountId.isBlank()) {
            throw new ValidationException("Please enter the destination Account ID.");
        }
        String destination = toAccountId.trim();
        // Sending money to yourself doesn't make sense.
        if (fromAccountId.equals(destination)) {
            throw new ValidationException("You cannot transfer money to the same account.");
        }
        runWithDatabase(() -> {
            Account source = accountRepository.findById(fromAccountId)
                    .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
            accountRepository.findById(destination)
                    .orElseThrow(() -> new AccountNotFoundException(destination));
            if (source.getBalance().compareTo(validAmount) < 0) {
                AppLogger.error("Transfer rejected for " + fromAccountId + ": insufficient funds");
                throw new InsufficientFundsException();
            }
            accountRepository.transfer(fromAccountId, destination, validAmount);
            AppLogger.info("User " + fromAccountId + " successfully transferred " + validAmount + " to " + destination);
        });
    }

    @Override
    public List<Transaction> getHistory(String accountId) {
        return callWithDatabase(() -> {
            // Unknown ID = error. Account with no transactions yet = empty list.
            accountRepository.findById(accountId)
                    .orElseThrow(() -> new AccountNotFoundException(accountId));
            List<Transaction> history = transactionRepository.findRecentByAccountId(accountId, HISTORY_LIMIT);
            AppLogger.info("User " + accountId + " viewed transaction history");
            return history;
        });
    }

    // $25.00 is fine. $25.001 or a negative number is not.
    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Please enter a valid amount.");
        }
        if (amount.scale() > 2) {
            throw new InvalidAmountException("Amount cannot have more than two decimal places.");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException();
        }
        return normalized;
    }

    // Keep trying until we get an ID that isn't taken. Give up after a while.
    private String allocateAccountId() {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            String candidate = accountIdGenerator.get();
            if (!accountRepository.existsById(candidate)) {
                return candidate;
            }
        }
        throw new ServiceUnavailableException(new IllegalStateException("Unable to allocate a unique Account ID"));
    }

    // If Postgres dies, log it and show "service unavailable" instead of a stack trace.
    private void runWithDatabase(Runnable action) {
        try {
            action.run();
        } catch (DataAccessException | IllegalStateException e) {
            AppLogger.error("Database connection lost", e);
            throw new ServiceUnavailableException(e);
        }
    }

    // Same as runWithDatabase, but for methods that return something.
    private <T> T callWithDatabase(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException | IllegalStateException e) {
            AppLogger.error("Database connection lost", e);
            throw new ServiceUnavailableException(e);
        }
    }
}
