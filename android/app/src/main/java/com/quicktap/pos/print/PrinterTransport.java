package com.quicktap.pos.print;

/** The physical channel a receipt gets sent through. */
public enum PrinterTransport {
    BLUETOOTH("bluetooth"),
    USB("usb"),
    NETWORK("network");

    public final String key;

    PrinterTransport(String key) {
        this.key = key;
    }

    public static PrinterTransport fromKey(String key) {
        if (USB.key.equals(key)) return USB;
        if (NETWORK.key.equals(key)) return NETWORK;
        return BLUETOOTH;
    }
}
