package com.quicktap.pos.print;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Thin SharedPreferences wrapper for printer settings, sharing the same
 * "quicktap_prefs" file as AppPrefs. Keeps printer_mac / printer_name /
 * paper_chars keys backwards compatible with the original Bluetooth-only
 * settings while adding transport selection and USB / network destinations.
 */
public class PrinterPrefs {

    private static final String FILE = "quicktap_prefs";
    private static PrinterPrefs instance;

    private final SharedPreferences sp;

    private PrinterPrefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static synchronized PrinterPrefs get(Context context) {
        if (instance == null) instance = new PrinterPrefs(context);
        return instance;
    }

    /** Which transport the cashier picked to send receipts through. */
    public PrinterTransport getTransport() {
        return PrinterTransport.fromKey(sp.getString("printer_transport", PrinterTransport.BLUETOOTH.key));
    }

    public void setTransport(PrinterTransport transport) {
        sp.edit().putString("printer_transport", transport.key).apply();
    }

    // ---- bluetooth (legacy keys, kept as-is) ----
    public String getPrinterMac() { return sp.getString("printer_mac", null); }
    public String getPrinterName() { return sp.getString("printer_name", null); }
    public void setBluetoothPrinter(String mac, String name) {
        sp.edit().putString("printer_mac", mac).putString("printer_name", name).apply();
    }

    /** 32 chars for 58mm rolls, 48 chars for 80mm rolls. */
    public int getPaperChars() { return sp.getInt("paper_chars", 32); }
    public void setPaperChars(int v) { sp.edit().putInt("paper_chars", v).apply(); }

    // ---- usb ----
    public String getUsbDeviceName() { return sp.getString("printer_usb_device", null); }
    public void setUsbDevice(String deviceName) {
        sp.edit().putString("printer_usb_device", deviceName).apply();
    }

    // ---- network ----
    public String getNetworkHost() { return sp.getString("printer_net_host", ""); }
    public int getNetworkPort() { return sp.getInt("printer_net_port", 9100); }
    public void setNetworkPrinter(String host, int port) {
        sp.edit().putString("printer_net_host", host).putInt("printer_net_port", port).apply();
    }

    /** Short human summary of whichever printer is currently selected. */
    public String describeSelected() {
        switch (getTransport()) {
            case USB:
                String usb = getUsbDeviceName();
                return usb == null ? "No USB printer selected" : "USB · " + usb;
            case NETWORK:
                String host = getNetworkHost();
                return host.isEmpty() ? "No network printer set"
                        : "Network · " + host + ":" + getNetworkPort();
            case BLUETOOTH:
            default:
                String name = getPrinterName();
                return name == null ? "No Bluetooth printer paired" : "Bluetooth · " + name;
        }
    }
}
