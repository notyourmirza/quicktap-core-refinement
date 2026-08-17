package com.quicktap.pos.ui.settings;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.quicktap.pos.databinding.ActivityPrinterSettingsBinding;
import com.quicktap.pos.databinding.ItemPrinterBinding;
import com.quicktap.pos.print.BluetoothPrinter;
import com.quicktap.pos.print.PrintJobs;
import com.quicktap.pos.print.PrinterPrefs;
import com.quicktap.pos.print.PrinterTransport;
import com.quicktap.pos.print.UsbPrinter;
import com.quicktap.pos.util.AppPrefs;

import java.util.List;

/**
 * Lets the cashier pick a Bluetooth, USB or Wi-Fi/LAN thermal printer, choose
 * the paper width, and run a test print. Whichever device is tapped becomes
 * the active transport for every future receipt.
 */
public class PrinterSettingsActivity extends AppCompatActivity {

    private ActivityPrinterSettingsBinding binding;
    private AppPrefs prefs;
    private PrinterPrefs printerPrefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPrinterSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs = AppPrefs.get(this);
        printerPrefs = PrinterPrefs.get(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.radio58.setChecked(prefs.getPaperChars() == 32);
        binding.radio80.setChecked(prefs.getPaperChars() == 48);
        binding.radio58.setOnClickListener(v -> prefs.setPaperChars(32));
        binding.radio80.setOnClickListener(v -> prefs.setPaperChars(48));
        binding.buttonTestPrint.setOnClickListener(v -> PrintJobs.testPrint(this));

        binding.buttonRefreshBluetooth.setOnClickListener(v -> loadBluetoothDevices());
        binding.buttonRefreshUsb.setOnClickListener(v -> loadUsbDevices());
        binding.buttonSaveNetwork.setOnClickListener(v -> saveNetworkPrinter());

        refreshAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.quicktap.pos.ui.license.LicenseGuard.protect(this);
        refreshAll();
    }

    private void refreshAll() {
        updateSummary();
        loadBluetoothDevices();
        loadUsbDevices();
        prefillNetwork();
    }

    private void updateSummary() {
        binding.textSelectedSummary.setText(printerPrefs.describeSelected());
    }

    private void prefillNetwork() {
        String host = printerPrefs.getNetworkHost();
        if (!host.isEmpty() && binding.inputNetHost.getText() != null
                && binding.inputNetHost.getText().toString().isEmpty()) {
            binding.inputNetHost.setText(host);
        }
        if (binding.inputNetPort.getText() != null
                && binding.inputNetPort.getText().toString().trim().isEmpty()) {
            binding.inputNetPort.setText(String.valueOf(printerPrefs.getNetworkPort()));
        }
    }

    // ---------------- Bluetooth ----------------

    @SuppressLint("MissingPermission")
    private void loadBluetoothDevices() {
        binding.containerBluetoothDevices.removeAllViews();
        BluetoothPrinter printer = BluetoothPrinter.get(this);

        if (!printer.hasPermission()) {
            binding.textBluetoothHint.setText("Bluetooth permission is required to list printers.");
            return;
        }

        List<BluetoothDevice> devices = printer.pairedDevices();
        if (devices.isEmpty()) {
            binding.textBluetoothHint.setText(
                    "No paired devices. Pair your thermal printer in Android Bluetooth settings first.");
            return;
        }
        binding.textBluetoothHint.setText("Select your paired Bluetooth printer:");

        boolean isSelected = printerPrefs.getTransport() == PrinterTransport.BLUETOOTH;
        String selectedMac = printerPrefs.getPrinterMac();
        for (BluetoothDevice device : devices) {
            ItemPrinterBinding row = ItemPrinterBinding.inflate(
                    getLayoutInflater(), binding.containerBluetoothDevices, false);
            row.textName.setText(device.getName() == null ? "Unknown device" : device.getName());
            row.textMac.setText(device.getAddress());
            row.radioSelected.setChecked(isSelected && device.getAddress().equals(selectedMac));
            View.OnClickListener select = v -> {
                printerPrefs.setBluetoothPrinter(device.getAddress(), device.getName());
                printerPrefs.setTransport(PrinterTransport.BLUETOOTH);
                BluetoothPrinter.get(this).closeQuietly();
                Toast.makeText(this, "Bluetooth printer saved", Toast.LENGTH_SHORT).show();
                refreshAll();
            };
            row.getRoot().setOnClickListener(select);
            row.radioSelected.setOnClickListener(select);
            binding.containerBluetoothDevices.addView(row.getRoot());
        }
    }

    // ---------------- USB ----------------

    private void loadUsbDevices() {
        binding.containerUsbDevices.removeAllViews();
        UsbPrinter usbPrinter = UsbPrinter.get(this);
        List<UsbDevice> devices = usbPrinter.printerDevices();

        if (devices.isEmpty()) {
            binding.textUsbHint.setText(
                    "No USB printer detected. Plug it in via an OTG cable and tap Scan.");
            return;
        }
        binding.textUsbHint.setText("Select your connected USB printer:");

        boolean isSelected = printerPrefs.getTransport() == PrinterTransport.USB;
        String selectedName = printerPrefs.getUsbDeviceName();
        for (UsbDevice device : devices) {
            ItemPrinterBinding row = ItemPrinterBinding.inflate(
                    getLayoutInflater(), binding.containerUsbDevices, false);
            String label = device.getProductName() == null
                    ? "USB printer" : device.getProductName();
            row.textName.setText(label);
            row.textMac.setText(device.getDeviceName());
            row.radioSelected.setChecked(isSelected && device.getDeviceName().equals(selectedName));
            View.OnClickListener select = v -> usbPrinter.requestPermission(device,
                    new UsbPrinter.PermissionCallback() {
                        @Override public void onGranted(UsbDevice granted) {
                            printerPrefs.setUsbDevice(granted.getDeviceName());
                            printerPrefs.setTransport(PrinterTransport.USB);
                            Toast.makeText(PrinterSettingsActivity.this,
                                    "USB printer saved", Toast.LENGTH_SHORT).show();
                            refreshAll();
                        }
                        @Override public void onDenied() {
                            Toast.makeText(PrinterSettingsActivity.this,
                                    "USB permission denied", Toast.LENGTH_SHORT).show();
                        }
                    });
            row.getRoot().setOnClickListener(select);
            row.radioSelected.setOnClickListener(select);
            binding.containerUsbDevices.addView(row.getRoot());
        }
    }

    // ---------------- Network ----------------

    private void saveNetworkPrinter() {
        String host = binding.inputNetHost.getText() == null
                ? "" : binding.inputNetHost.getText().toString().trim();
        String portText = binding.inputNetPort.getText() == null
                ? "" : binding.inputNetPort.getText().toString().trim();
        if (host.isEmpty()) {
            Toast.makeText(this, "Enter the printer's IP address", Toast.LENGTH_SHORT).show();
            return;
        }
        int port = 9100;
        if (!portText.isEmpty()) {
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        printerPrefs.setNetworkPrinter(host, port);
        printerPrefs.setTransport(PrinterTransport.NETWORK);
        Toast.makeText(this, "Network printer saved", Toast.LENGTH_SHORT).show();
        updateSummary();
    }
}
