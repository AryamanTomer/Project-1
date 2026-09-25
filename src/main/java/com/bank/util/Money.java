package com.bank.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;


public final class Money {
    // Keep the money rounded to 2 decimal places so that we don't have to worry about floating point errors and keep the current format to the USA since it is the standard.
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);

    private Money(){

    }

    public static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static String format(BigDecimal amount) {
        return CURRENCY.format(scale(amount));
    }
}
