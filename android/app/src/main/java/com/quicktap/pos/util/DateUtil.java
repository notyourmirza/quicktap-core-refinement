package com.quicktap.pos.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Day/week/month range helpers shared by the dashboard and reports. */
public final class DateUtil {

    private static final SimpleDateFormat RECEIPT =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
    private static final SimpleDateFormat HEADER =
            new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
    private static final SimpleDateFormat FILE =
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private DateUtil() { }

    public static String receipt(long millis) { return RECEIPT.format(new Date(millis)); }

    public static String header(long millis) { return HEADER.format(new Date(millis)); }

    public static String fileStamp(long millis) { return FILE.format(new Date(millis)); }

    public static long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public static long endOfToday() { return startOfToday() + 86_400_000L - 1; }

    public static long startOfWeek() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfToday());
        c.set(Calendar.DAY_OF_WEEK, c.getFirstDayOfWeek());
        return c.getTimeInMillis();
    }

    public static long startOfMonth() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(startOfToday());
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }
}
