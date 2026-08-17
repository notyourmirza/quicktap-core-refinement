package com.quicktap.pos.print;

import android.content.Context;

import com.quicktap.pos.util.AppExecutors;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Raw TCP ("JetDirect" / port 9100) ESC/POS transport for network thermal
 * printers. A fresh socket is opened per job, which is simple and matches how
 * these printers are normally driven from POS software.
 */
public class NetworkPrinter {

    private static final int CONNECT_TIMEOUT_MS = 5000;

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private static NetworkPrinter instance;

    private final Context context;

    private NetworkPrinter(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized NetworkPrinter get(Context context) {
        if (instance == null) instance = new NetworkPrinter(context);
        return instance;
    }

    public void print(byte[] payload, Callback callback) {
        AppExecutors.io().execute(() -> {
            try {
                writeBlocking(payload);
                AppExecutors.main().post(callback::onSuccess);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Printer not reachable" : e.getMessage();
                AppExecutors.main().post(() -> callback.onError(message));
            }
        });
    }

    private void writeBlocking(byte[] payload) throws IOException {
        PrinterPrefs prefs = PrinterPrefs.get(context);
        String host = prefs.getNetworkHost();
        int port = prefs.getNetworkPort();
        if (host == null || host.trim().isEmpty()) {
            throw new IOException("No network printer configured. Open Settings > Printer.");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), CONNECT_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(payload);
            out.flush();
        }
    }
}
