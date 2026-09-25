package com.bank.api;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

import com.bank.domain.Transaction;
import com.bank.exception.BankingException;
import com.bank.exception.DataAccessException;
import com.bank.service.AccountService;
import com.bank.util.AppLogger;
import com.bank.util.Money;

// What the user types at. Only talks to AccountService. Errors go to the log, not the screen.
public class BankCli {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final AccountService accountService;
    private final Scanner scanner;

    public BankCli(AccountService accountService, Scanner scanner) {
        this.accountService = accountService;
        this.scanner = scanner;
    }

    // Register / log in / quit.
    public void start() {
        boolean running = true;
        while (running) {
            System.out.println("BANK OF CLI");
            System.out.println("1) Register");
            System.out.println("2) Log in");
            System.out.println("3) Exit");
            String choice = prompt("Select an option");
            switch (choice) {
                case "1" -> handleRegister();
                case "2" -> handleLogin();
                case "3" -> running = false;
                default -> System.out.println("Please choose 1, 2, or 3.");
            }
            System.out.println();
        }
        System.out.println("Thank you for banking with Bank of CLI. Goodbye.");
    }

    private void handleRegister() {
        try {
            String pin = prompt("Choose a 4-digit PIN");
            String confirm = prompt("Confirm PIN");
            // Catch a typo before we save anything.
            if (!pin.equals(confirm)) {
                System.out.println("PINs did not match. Registration cancelled.");
                return;
            }
            var account = accountService.register(pin);
            System.out.println();
            System.out.println("Registration successful.");
            System.out.println("Your Account ID is: " + account.getAccountId());
            System.out.println("Keep your Account ID and PIN safe — you will need both to log in.");
        } catch (DataAccessException | IllegalStateException | BankingException e) {
            showFailure(e);
        }
    }

    private void handleLogin() {
        try {
            String accountId = prompt("Account ID");
            String pin = prompt("PIN");
            accountService.login(accountId, pin);
            System.out.println("Login successful. Welcome.");
            runSession(accountId.trim());
        } catch (DataAccessException | IllegalStateException | BankingException e) {
            showFailure(e);
        }
    }

    // Menu after login, until they pick log out.
    private void runSession(String accountId) {
        boolean inSession = true;
        while (inSession) {
            System.out.println();
            System.out.println("Account " + accountId);
            System.out.println("1) Check balance");
            System.out.println("2) Deposit");
            System.out.println("3) Withdraw");
            System.out.println("4) Transfer");
            System.out.println("5) Transaction history");
            System.out.println("6) Log out");
            String choice = prompt("Select an option");
            try {
                switch (choice) {
                    case "1" -> System.out.println(
                            "Current balance: " + Money.format(accountService.getBalance(accountId))
                    );
                    case "2" -> handleDeposit(accountId);
                    case "3" -> handleWithdraw(accountId);
                    case "4" -> handleTransfer(accountId);
                    case "5" -> showHistory(accountId);
                    case "6" -> inSession = false;
                    default -> System.out.println("Please choose a number from 1 to 6.");
                }
            } catch (DataAccessException | IllegalStateException | BankingException e) {
                showFailure(e);
            }
        }
        System.out.println("You have been logged out.");
    }

    private void handleDeposit(String accountId) {
        BigDecimal amount = promptAmount("Deposit amount");
        if (amount == null) {
            return;
        }
        accountService.deposit(accountId, amount);
        System.out.println("Deposit successful. New balance: " + Money.format(accountService.getBalance(accountId)));
    }

    private void handleWithdraw(String accountId) {
        BigDecimal amount = promptAmount("Withdrawal amount");
        if (amount == null) {
            return;
        }
        accountService.withdraw(accountId, amount);
        System.out.println("Withdrawal successful. New balance: " + Money.format(accountService.getBalance(accountId)));
    }

    private void handleTransfer(String accountId) {
        String destination = prompt("Destination Account ID");
        BigDecimal amount = promptAmount("Transfer amount");
        if (amount == null) {
            return;
        }
        accountService.transfer(accountId, destination, amount);
        System.out.println("Transfer successful. New balance: " + Money.format(accountService.getBalance(accountId)));
    }

    private void showHistory(String accountId) {
        List<Transaction> history = accountService.getHistory(accountId);
        if (history.isEmpty()) {
            System.out.println("No transactions yet.");
            return;
        }
        System.out.println();
        System.out.printf(Locale.US, "%-18s %-14s %12s  %s%n", "When", "Type", "Amount", "Details");
        System.out.println("-".repeat(72));
        for (Transaction transaction : history) {
            System.out.printf(
                    Locale.US,
                    "%-18s %-14s %12s  %s%n",
                    TIMESTAMP_FORMAT.format(transaction.getCreatedAt()),
                    transaction.getType().name().replace('_', ' '),
                    Money.format(transaction.getAmount()),
                    transaction.getDescription().orElse("")
            );
        }
    }

    private BigDecimal promptAmount(String label) {
        String raw = prompt(label);
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid amount, for example 25.00.");
            return null;
        }
    }

    // Don't dump SQLException text. If the DB died, just say service unavailable.
    private void showFailure(RuntimeException error) {
        if (error instanceof DataAccessException || error instanceof IllegalStateException) {
            AppLogger.error("Database connection lost", error);
            System.out.println("Service temporarily unavailable. Please try again later.");
            return;
        }
        System.out.println(((BankingException) error).getUserMessage());
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        if (!scanner.hasNextLine()) {
            return "";
        }
        return scanner.nextLine().trim();
    }
}
