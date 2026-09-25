package com.bank.api;

import java.util.Scanner;

import com.bank.repository.AccountRepository;
import com.bank.repository.AccountRepositoryImpl;
import com.bank.repository.TransactionRepository;
import com.bank.repository.TransactionRepositoryImpl;
import com.bank.service.AccountService;
import com.bank.service.AccountServiceImpl;
import com.bank.util.AppLogger;

// Glue: repos -> service -> menus. BankCli never sees JDBC.
public class Main {
    public static void main(String[] args) {
        try {
            AccountRepository accountRepository = new AccountRepositoryImpl();
            TransactionRepository transactionRepository = new TransactionRepositoryImpl();
            AccountService service = new AccountServiceImpl(accountRepository, transactionRepository);
            try (Scanner scanner = new Scanner(System.in)) {
                new BankCli(service, scanner).start();
            }
        } catch (IllegalStateException e) {
            // Postgres wasn't up, or db.properties is wrong.
            AppLogger.error("Database connection lost", e);
            System.out.println("Service temporarily unavailable. Please try again later.");
        }
    }
}
