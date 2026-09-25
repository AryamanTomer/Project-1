package com.bank.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bank.domain.Account;
import com.bank.domain.Transaction;
import com.bank.domain.TransactionType;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.AuthenticationException;
import com.bank.exception.InsufficientFundsException;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.ValidationException;
import com.bank.repository.AccountRepository;
import com.bank.repository.TransactionRepository;
import com.bank.util.PinHasher;

// Service tests. Repos are fakes — we're checking the rules, not Postgres.
// Each public method has a "it worked" test and a "it failed the way we wanted" test.
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {
    private static final String ACCOUNT_ID = "10000001";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        // Same ID every time so register tests aren't flaky.
        accountService = new AccountServiceImpl(accountRepository, transactionRepository, () -> ACCOUNT_ID);
    }

    @Test
    void register_createsAccountWhenPinIsValid() {
        when(accountRepository.existsById(ACCOUNT_ID)).thenReturn(false);

        Account created = accountService.register("1234");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).create(captor.capture());
        assertEquals(ACCOUNT_ID, created.getAccountId());
        assertEquals(new BigDecimal("0.00"), captor.getValue().getBalance());
        assertTrue(PinHasher.verify("1234", captor.getValue().getPinHash()));
    }

    @Test
    void register_rejectsPinThatIsNotFourDigits() {
        assertThrows(ValidationException.class, () -> accountService.register("12a4"));
        verify(accountRepository, never()).create(any());
    }

    @Test
    void login_returnsAccountWhenCredentialsAreValid() {
        Account stored = new Account(ACCOUNT_ID, PinHasher.hash("1234"), new BigDecimal("50.00"), Instant.now());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(stored));

        Account loggedIn = accountService.login(ACCOUNT_ID, "1234");

        assertEquals(ACCOUNT_ID, loggedIn.getAccountId());
        assertEquals(new BigDecimal("50.00"), loggedIn.getBalance());
    }

    @Test
    void login_rejectsIncorrectPin() {
        Account stored = new Account(ACCOUNT_ID, PinHasher.hash("1234"), BigDecimal.ZERO, Instant.now());
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(stored));

        assertThrows(AuthenticationException.class, () -> accountService.login(ACCOUNT_ID, "9999"));
    }

    @Test
    void getBalance_returnsCurrentBalance() {
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(new Account(ACCOUNT_ID, "hash", new BigDecimal("125.50"), Instant.now())));

        assertEquals(new BigDecimal("125.50"), accountService.getBalance(ACCOUNT_ID));
    }

    @Test
    void getBalance_throwsWhenAccountDoesNotExist() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.getBalance(ACCOUNT_ID));
    }

    @Test
    void deposit_creditsAccountWhenAmountIsValid() {
        accountService.deposit(ACCOUNT_ID, new BigDecimal("40.00"));

        verify(accountRepository).deposit(ACCOUNT_ID, new BigDecimal("40.00"));
    }

    @Test
    void deposit_rejectsNonPositiveAmount() {
        assertThrows(InvalidAmountException.class, () -> accountService.deposit(ACCOUNT_ID, new BigDecimal("-5.00")));
        verify(accountRepository, never()).deposit(any(), any());
    }

    @Test
    void withdraw_debitsAccountWhenFundsAreAvailable() {
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(new Account(ACCOUNT_ID, "hash", new BigDecimal("80.00"), Instant.now())));

        accountService.withdraw(ACCOUNT_ID, new BigDecimal("30.00"));

        verify(accountRepository).withdraw(ACCOUNT_ID, new BigDecimal("30.00"));
    }

    @Test
    void withdraw_rejectsWhenFundsAreInsufficient() {
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(new Account(ACCOUNT_ID, "hash", new BigDecimal("10.00"), Instant.now())));

        assertThrows(InsufficientFundsException.class,
                () -> accountService.withdraw(ACCOUNT_ID, new BigDecimal("10.01")));
        verify(accountRepository, never()).withdraw(any(), any());
    }

    @Test
    void transfer_movesFundsBetweenDistinctAccounts() {
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(new Account(ACCOUNT_ID, "hash", new BigDecimal("200.00"), Instant.now())));
        when(accountRepository.findById("10000002"))
                .thenReturn(Optional.of(new Account("10000002", "hash", new BigDecimal("5.00"), Instant.now())));

        accountService.transfer(ACCOUNT_ID, "10000002", new BigDecimal("50.00"));

        verify(accountRepository).transfer(ACCOUNT_ID, "10000002", new BigDecimal("50.00"));
    }

    @Test
    void transfer_rejectsTransferToTheSameAccount() {
        assertThrows(ValidationException.class,
                () -> accountService.transfer(ACCOUNT_ID, ACCOUNT_ID, new BigDecimal("10.00")));
        verify(accountRepository, never()).transfer(any(), any(), any());
    }

    @Test
    void getHistory_returnsRecentTransactions() {
        Transaction deposit = new Transaction(
                1L, ACCOUNT_ID, TransactionType.DEPOSIT, new BigDecimal("20.00"), null, "Deposit", Instant.now()
        );
        when(accountRepository.findById(ACCOUNT_ID))
                .thenReturn(Optional.of(new Account(ACCOUNT_ID, "hash", new BigDecimal("20.00"), Instant.now())));
        when(transactionRepository.findRecentByAccountId(ACCOUNT_ID, AccountServiceImpl.HISTORY_LIMIT))
                .thenReturn(List.of(deposit));

        List<Transaction> history = accountService.getHistory(ACCOUNT_ID);

        assertEquals(1, history.size());
        assertEquals(TransactionType.DEPOSIT, history.get(0).getType());
    }

    @Test
    void getHistory_throwsWhenAccountDoesNotExist() {
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.getHistory(ACCOUNT_ID));
    }
}
