package com.quicktap.pos.util;

import java.text.DecimalFormat;

/** Consistent money formatting across UI and printed receipts. */
public final class Money {

    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PLAIN = new DecimalFormat("0.00");

    private Money() { }

    public static String format(double value) { return DF.format(value); }

    public static String withCurrency(String currency, double value) {
        return currency + " " + DF.format(value);
    }

    public static String plain(double value) { return PLAIN.format(value); }
}
