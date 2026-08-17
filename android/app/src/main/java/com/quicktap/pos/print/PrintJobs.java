package com.quicktap.pos.print;

import android.content.Context;
import android.widget.Toast;

import com.quicktap.pos.QuickTapApp;
import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import java.util.List;

/**
 * One-line helpers for the print, reprint and test-print actions. Routes the
 * ESC/POS bytes to whichever transport (Bluetooth / USB / Network) the
 * cashier picked in Printer settings.
 */
public final class PrintJobs {

    private PrintJobs() { }

    public static void print(Context context, Bill bill, List<BillItem> items) {
        byte[] payload = new ReceiptBuilder(AppPrefs.get(context)).build(bill, items);
        send(context, payload, "Receipt printed");
    }

    /** Loads the bill's items off the UI thread, then prints. */
    public static void reprint(Context context, long billId) {
        AppExecutors.io().execute(() -> {
            Bill bill = QuickTapApp.get().repo().billById(billId);
            if (bill == null) return;
            List<BillItem> items = QuickTapApp.get().repo().itemsOf(billId);
            AppExecutors.main().post(() -> print(context, bill, items));
        });
    }

    public static void reprintLast(Context context) {
        AppExecutors.io().execute(() -> {
            Bill bill = QuickTapApp.get().repo().lastBill();
            if (bill == null) {
                AppExecutors.main().post(() ->
                        Toast.makeText(context, "No bills yet", Toast.LENGTH_SHORT).show());
                return;
            }
            AppExecutors.main().post(() -> reprint(context, bill.id));
        });
    }

    public static void testPrint(Context context) {
        byte[] payload = new ReceiptBuilder(AppPrefs.get(context)).testPage();
        send(context, payload, "Test print sent");
    }

    /** Routes to the transport selected in PrinterPrefs. */
    private static void send(Context context, byte[] payload, String okMessage) {
        PrinterTransport transport = PrinterPrefs.get(context).getTransport();
        switch (transport) {
            case USB:
                UsbPrinter.get(context).print(payload, new UsbPrinter.Callback() {
                    @Override public void onSuccess() { toast(context, okMessage); }
                    @Override public void onError(String message) { toastLong(context, message); }
                });
                break;
            case NETWORK:
                NetworkPrinter.get(context).print(payload, new NetworkPrinter.Callback() {
                    @Override public void onSuccess() { toast(context, okMessage); }
                    @Override public void onError(String message) { toastLong(context, message); }
                });
                break;
            case BLUETOOTH:
            default:
                BluetoothPrinter.get(context).print(payload, new BluetoothPrinter.Callback() {
                    @Override public void onSuccess() { toast(context, okMessage); }
                    @Override public void onError(String message) { toastLong(context, message); }
                });
                break;
        }
    }

    private static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private static void toastLong(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
