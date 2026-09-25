package com.bank.exception;

//This AccountID isn't in the database.
public class AccountNotFoundException extends BankingException{
    public AccountNotFoundException(String accountId) {
        super("No account was found for ID " + accountId + ".");
    }
}
