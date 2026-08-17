package com.quicktap.pos.print;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.quicktap.pos.util.AppExecutors;
import com.quicktap.pos.util.AppPrefs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bluetooth ESC/POS transport. Keeps one socket open to the remembered printer
 * and silently reconnects when the connection has dropped, so printing stays a
 * single tap for the cashier.
 */
public class BluetoothPrinter {

    /** Standard Serial Port Profile UUID used by virtually all thermal printers. */
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private static BluetoothPrinter instance;

    private final Context context;
    private BluetoothSocket socket;
    private OutputStream stream;
    private String connectedMac;

    private BluetoothPrinter(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized BluetoothPrinter get(Context context) {
        if (instance == null) instance = new BluetoothPrinter(context);
        return instance;
    }

    public boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public List<BluetoothDevice> pairedDevices() {
        List<BluetoothDevice> devices = new ArrayList<>();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled() || !hasPermission()) return devices;
        devices.addAll(adapter.getBondedDevices());
        return devices;
    }

    /** Prints on a background thread and reports back on the main thread. */
    public void print(byte[] payload, Callback callback) {
        AppExecutors.io().execute(() -> {
            try {
                writeBlocking(payload);
                AppExecutors.main().post(callback::onSuccess);
            } catch (Exception e) {
                closeQuietly();
                String message = e.getMessage() == null ? "Printer not reachable" : e.getMessage();
                AppExecutors.main().post(() -> callback.onError(message));
            }
        });
    }

    @SuppressLint("MissingPermission")
    private synchronized void writeBlocking(byte[] payload) throws IOException {
        String mac = AppPrefs.get(context).getPrinterMac();
        if (mac == null) throw new IOException("No printer selected. Open Settings > Printer.");
        if (!hasPermission()) throw new IOException("Bluetooth permission not granted.");

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) throw new IOException("This device has no Bluetooth.");
        if (!adapter.isEnabled()) throw new IOException("Turn Bluetooth on and try again.");

        if (socket == null || !socket.isConnected() || !mac.equals(connectedMac)) {
            closeQuietly();
            BluetoothDevice device = adapter.getRemoteDevice(mac);
            socket = device.createRfcommSocketToServiceRecord(SPP);
            adapter.cancelDiscovery();
            socket.connect();
            stream = socket.getOutputStream();
            connectedMac = mac;
        }

        try {
            stream.write(payload);
            stream.flush();
        } catch (IOException first) {
            // The socket went stale while idle: reconnect once, then retry.
            closeQuietly();
            BluetoothDevice device = adapter.getRemoteDevice(mac);
            socket = device.createRfcommSocketToServiceRecord(SPP);
            socket.connect();
            stream = socket.getOutputStream();
            connectedMac = mac;
            stream.write(payload);
            stream.flush();
        }
    }

    public synchronized void closeQuietly() {
        try { if (stream != null) stream.close(); } catch (IOException ignored) { }
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        stream = null;
        socket = null;
        connectedMac = null;
    }
}
