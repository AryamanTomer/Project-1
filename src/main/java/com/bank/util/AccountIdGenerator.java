package com.bank.util;

import java.security.SecureRandom;

// I used a stringBuilder to concatenate the random integers to make all 8 combined digits into 1 string (by combined I don't mean the sum or addition).
public final class AccountIdGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountIdGenerator() {

    }

    public static String nextId() {
        StringBuilder id = new StringBuilder(8);
        id.append(RANDOM.nextInt(9) + 1);
        for(int i = 1; i < 8; i++){
            id.append(RANDOM.nextInt(10));
        }
        return id.toString();
    }
}