package com.sanlei.pos.printer;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothPrinterService {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String PREF_NAME = "printer_prefs";
    private static final String KEY_MAC = "printer_mac";
    private static final String KEY_NAME = "printer_name";

    private final Context context;
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;

    public BluetoothPrinterService(Context context) {
        this.context = context;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public static class PrinterInfo {
        public String name;
        public String macAddress;

        public PrinterInfo(String name, String macAddress) {
            this.name = name;
            this.macAddress = macAddress;
        }

        @Override
        public String toString() {
            return name + " (" + macAddress + ")";
        }
    }

    public boolean isBluetoothAvailable() {
        return adapter != null;
    }

    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    public List<PrinterInfo> getPairedDevices() {
        List<PrinterInfo> devices = new ArrayList<>();
        if (adapter == null) return devices;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return devices;
            }
        }

        Set<BluetoothDevice> paired = adapter.getBondedDevices();
        if (paired != null) {
            for (BluetoothDevice device : paired) {
                devices.add(new PrinterInfo(device.getName(), device.getAddress()));
            }
        }
        return devices;
    }

    public void savePrinter(String name, String mac) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_MAC, mac)
                .putString(KEY_NAME, name)
                .apply();
    }

    public String getSavedPrinterMac() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_MAC, null);
    }

    public String getSavedPrinterName() {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_NAME, null);
    }

    public boolean connect(String macAddress) {
        if (adapter == null) return false;

        try {
            disconnect();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }

            BluetoothDevice device = adapter.getRemoteDevice(macAddress);
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            outputStream = socket.getOutputStream();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            disconnect();
            return false;
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected();
    }

    public boolean print(byte[] data) {
        // Auto-connect to saved printer if not connected
        if (!isConnected()) {
            String savedMac = getSavedPrinterMac();
            if (savedMac != null) {
                if (!connect(savedMac)) return false;
            } else {
                return false;
            }
        }

        try {
            outputStream.write(data);
            outputStream.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            disconnect();
            return false;
        }
    }

    public void disconnect() {
        try {
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        outputStream = null;
        socket = null;
    }
}
