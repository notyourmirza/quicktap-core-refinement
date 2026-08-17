package com.quicktap.pos.print;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;

import androidx.annotation.Nullable;

import com.quicktap.pos.util.AppExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * USB ESC/POS transport. Most thermal printers show up as a USB printer-class
 * (interface class 7) device with a single bulk OUT endpoint that raw ESC/POS
 * bytes can be written to directly.
 */
public class UsbPrinter {

    private static final String ACTION_USB_PERMISSION = "com.quicktap.pos.USB_PERMISSION";
    private static final int PRINTER_INTERFACE_CLASS = 7; // USB_CLASS_PRINTER

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public interface PermissionCallback {
        void onGranted(UsbDevice device);
        void onDenied();
    }

    private static UsbPrinter instance;

    private final Context context;

    private UsbPrinter(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized UsbPrinter get(Context context) {
        if (instance == null) instance = new UsbPrinter(context);
        return instance;
    }

    private UsbManager manager() {
        return (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    /** All attached USB devices that look like a printer. */
    public List<UsbDevice> printerDevices() {
        List<UsbDevice> result = new ArrayList<>();
        UsbManager manager = manager();
        if (manager == null) return result;
        HashMap<String, UsbDevice> devices = manager.getDeviceList();
        for (UsbDevice device : devices.values()) {
            if (looksLikePrinter(device)) result.add(device);
        }
        return result;
    }

    private boolean looksLikePrinter(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == PRINTER_INTERFACE_CLASS) return true;
        }
        return false;
    }

    @Nullable
    public UsbDevice findByName(String deviceName) {
        UsbManager manager = manager();
        if (manager == null || deviceName == null) return null;
        return manager.getDeviceList().get(deviceName);
    }

    public boolean hasPermission(UsbDevice device) {
        UsbManager manager = manager();
        return manager != null && manager.hasPermission(device);
    }

    /** Asks Android for permission to talk to the device, if not already granted. */
    public void requestPermission(UsbDevice device, PermissionCallback callback) {
        UsbManager manager = manager();
        if (manager == null) {
            callback.onDenied();
            return;
        }
        if (manager.hasPermission(device)) {
            callback.onGranted(device);
            return;
        }
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), flags);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
                context.unregisterReceiver(this);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                UsbDevice granted_device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                AppExecutors.main().post(() -> {
                    if (granted && granted_device != null) callback.onGranted(granted_device);
                    else callback.onDenied();
                });
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
        manager.requestPermission(device, permissionIntent);
    }

    /** Prints on a background thread and reports back on the main thread. */
    public void print(byte[] payload, Callback callback) {
        AppExecutors.io().execute(() -> {
            try {
                writeBlocking(payload);
                AppExecutors.main().post(callback::onSuccess);
            } catch (Exception e) {
                String message = e.getMessage() == null ? "USB printer not reachable" : e.getMessage();
                AppExecutors.main().post(() -> callback.onError(message));
            }
        });
    }

    private void writeBlocking(byte[] payload) throws Exception {
        String deviceName = PrinterPrefs.get(context).getUsbDeviceName();
        if (deviceName == null) throw new Exception("No USB printer selected. Open Settings > Printer.");

        UsbManager manager = manager();
        if (manager == null) throw new Exception("USB host mode is not supported on this device.");
        UsbDevice device = manager.getDeviceList().get(deviceName);
        if (device == null) throw new Exception("USB printer is not connected.");
        if (!manager.hasPermission(device)) throw new Exception("USB permission not granted.");

        UsbInterface printerInterface = null;
        UsbEndpoint outEndpoint = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (iface.getInterfaceClass() != PRINTER_INTERFACE_CLASS) continue;
            for (int e = 0; e < iface.getEndpointCount(); e++) {
                UsbEndpoint endpoint = iface.getEndpoint(e);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                        && endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                    printerInterface = iface;
                    outEndpoint = endpoint;
                    break;
                }
            }
            if (outEndpoint != null) break;
        }
        if (printerInterface == null || outEndpoint == null) {
            throw new Exception("This USB device has no printer bulk endpoint.");
        }

        UsbDeviceConnection connection = manager.openDevice(device);
        if (connection == null) throw new Exception("Could not open the USB printer.");
        try {
            connection.claimInterface(printerInterface, true);
            int chunkSize = 4096;
            int offset = 0;
            while (offset < payload.length) {
                int length = Math.min(chunkSize, payload.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(payload, offset, chunk, 0, length);
                int sent = connection.bulkTransfer(outEndpoint, chunk, length, 5000);
                if (sent < 0) throw new Exception("USB transfer failed.");
                offset += length;
            }
            connection.releaseInterface(printerInterface);
        } finally {
            connection.close();
        }
    }
}
