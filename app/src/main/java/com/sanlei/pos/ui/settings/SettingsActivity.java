package com.sanlei.pos.ui.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.sanlei.pos.BuildConfig;
import com.sanlei.pos.R;
import com.sanlei.pos.printer.BluetoothPrinterService;
import com.sanlei.pos.printer.EscPosCommands;
import com.sanlei.pos.sync.SyncWorker;
import com.sanlei.pos.ui.login.LoginActivity;
import com.sanlei.pos.update.AppUpdater;
import com.sanlei.pos.util.NetworkUtils;
import com.sanlei.pos.util.SessionManager;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private BluetoothPrinterService printerService;

    private TextView txtServerUrl;
    private TextView txtBranchName;
    private TextView txtUserName;
    private TextView txtLastSync;
    private TextView txtPrinterName;
    private TextView txtAppVersion;

    private ActivityResultLauncher<String> bluetoothPermissionLauncher;
    private Runnable pendingBluetoothAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sessionManager = new SessionManager(this);
        printerService = new BluetoothPrinterService(this);

        setupToolbar();
        bindViews();
        populateInfo();
        setupButtons();
        setupBluetoothPermissionLauncher();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
    }

    private void bindViews() {
        txtServerUrl = findViewById(R.id.txtServerUrl);
        txtBranchName = findViewById(R.id.txtBranchName);
        txtUserName = findViewById(R.id.txtUserName);
        txtLastSync = findViewById(R.id.txtLastSync);
        txtPrinterName = findViewById(R.id.txtPrinterName);
        txtAppVersion = findViewById(R.id.txtAppVersion);
    }

    private void populateInfo() {
        txtServerUrl.setText("https://sp.rgbpos.com");
        txtBranchName.setText(sessionManager.getBranchName());
        txtUserName.setText(sessionManager.getUserName());

        String lastSync = sessionManager.getLastSync();
        txtLastSync.setText(lastSync != null ? lastSync : "Never synced");

        String savedPrinter = printerService.getSavedPrinterName();
        txtPrinterName.setText(savedPrinter != null ? savedPrinter : "No printer selected");

        txtAppVersion.setText("v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
    }

    private void setupButtons() {
        findViewById(R.id.btnSyncNow).setOnClickListener(v -> onSyncNow());
        findViewById(R.id.btnSelectPrinter).setOnClickListener(v -> onSelectPrinter());
        findViewById(R.id.btnTestPrint).setOnClickListener(v -> onTestPrint());
        findViewById(R.id.btnCheckUpdates).setOnClickListener(v -> onCheckUpdate());
        findViewById(R.id.btnLogout).setOnClickListener(v -> onLogout());
    }

    private void setupBluetoothPermissionLauncher() {
        bluetoothPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted && pendingBluetoothAction != null) {
                        pendingBluetoothAction.run();
                        pendingBluetoothAction = null;
                    } else if (!granted) {
                        Toast.makeText(this, "Bluetooth permission is required", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private boolean ensureBluetoothPermission(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                pendingBluetoothAction = action;
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
                return false;
            }
        }
        return true;
    }

    // --- Sync ---

    private void onSyncNow() {
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }
        SyncWorker.syncNow(this);
        Toast.makeText(this, "Sync started", Toast.LENGTH_SHORT).show();
    }

    // --- Printer Selection ---

    private void onSelectPrinter() {
        if (!ensureBluetoothPermission(this::showPrinterDialog)) {
            return;
        }
        showPrinterDialog();
    }

    private void showPrinterDialog() {
        if (!printerService.isBluetoothAvailable()) {
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!printerService.isBluetoothEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }

        List<BluetoothPrinterService.PrinterInfo> devices = printerService.getPairedDevices();
        if (devices.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found. Pair a printer in system settings first.", Toast.LENGTH_LONG).show();
            return;
        }

        String[] deviceNames = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            deviceNames[i] = devices.get(i).toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Printer")
                .setItems(deviceNames, (dialog, which) -> {
                    BluetoothPrinterService.PrinterInfo selected = devices.get(which);
                    printerService.savePrinter(selected.name, selected.macAddress);
                    txtPrinterName.setText(selected.name);
                    Toast.makeText(this, "Printer saved: " + selected.name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Test Print ---

    private void onTestPrint() {
        if (!ensureBluetoothPermission(this::doTestPrint)) {
            return;
        }
        doTestPrint();
    }

    private void doTestPrint() {
        String savedMac = printerService.getSavedPrinterMac();
        if (savedMac == null) {
            Toast.makeText(this, "Please select a printer first", Toast.LENGTH_SHORT).show();
            return;
        }

        EscPosCommands.ReceiptData testData = new EscPosCommands.ReceiptData();
        testData.branchName = sessionManager.getBranchName();
        testData.branchAddress = sessionManager.getBranchAddress();
        testData.branchPhone = sessionManager.getBranchPhone();
        testData.invoiceNumber = "TEST-001";
        testData.cashierName = sessionManager.getUserName();
        testData.dateTime = null; // will use current time
        testData.items = new java.util.ArrayList<>();

        EscPosCommands.ReceiptItem item = new EscPosCommands.ReceiptItem();
        item.name = "Test Item";
        item.quantity = 1;
        item.unitPrice = 100.00;
        item.total = 100.00;
        item.discount = 0;
        testData.items.add(item);

        testData.subtotal = 100.00;
        testData.discountAmount = 0;
        testData.taxAmount = 10.71;
        testData.totalAmount = 100.00;
        testData.amountPaid = 100.00;
        testData.change = 0;
        testData.paymentMethod = "CASH";

        byte[] receipt = EscPosCommands.buildReceipt(testData);

        new Thread(() -> {
            boolean success = printerService.print(receipt);
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, "Test print sent successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Print failed. Check printer connection.", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    // --- Check for Updates ---

    private void onCheckUpdate() {
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();

        AppUpdater.checkForUpdate(this, new AppUpdater.UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String downloadUrl) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Update Available")
                        .setMessage("A new version (" + newVersion + ") is available. Download now?")
                        .setPositiveButton("Download", (dialog, which) ->
                                AppUpdater.downloadAndInstall(SettingsActivity.this, downloadUrl))
                        .setNegativeButton("Later", null)
                        .show();
            }

            @Override
            public void onNoUpdate() {
                Toast.makeText(SettingsActivity.this, "You are on the latest version", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SettingsActivity.this, "Update check failed: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- Logout ---

    private void onLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    sessionManager.logout();
                    printerService.disconnect();
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
