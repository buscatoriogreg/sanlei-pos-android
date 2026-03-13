package com.sanlei.pos.ui.pos;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sanlei.pos.R;
import com.sanlei.pos.data.db.AppDatabase;
import com.sanlei.pos.data.db.dao.PendingSaleDao;
import com.sanlei.pos.data.db.dao.ProductDao;
import com.sanlei.pos.data.db.entity.PendingSale;
import com.sanlei.pos.data.db.entity.ProductEntity;
import com.sanlei.pos.printer.BluetoothPrinterService;
import com.sanlei.pos.printer.EscPosCommands;
import com.sanlei.pos.sync.SyncWorker;
import com.sanlei.pos.ui.login.LoginActivity;
import com.sanlei.pos.ui.settings.SettingsActivity;
import com.sanlei.pos.update.AppUpdater;
import com.sanlei.pos.util.NetworkUtils;
import com.sanlei.pos.util.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PosActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSION = 1001;

    // Views
    private MaterialToolbar toolbar;
    private EditText editSearch;
    private LinearLayout letterFilterLayout;
    private LinearLayout syncStatusBar;
    private TextView txtSyncStatus;
    private RecyclerView recyclerProducts;
    private RecyclerView recyclerCart;
    private CheckBox chkScPwd;
    private EditText editScPwdId;
    private TextView txtSubtotal, txtDiscount, txtTotal, txtCartCount;
    private LinearLayout layoutDiscount;
    private ChipGroup chipGroupPayment;
    private Chip chipCash, chipGcash, chipMaya;
    private LinearLayout layoutCashPayment;
    private EditText editAmountPaid;
    private LinearLayout layoutDenominations;
    private MaterialButton btnCharge;

    // Data
    private final ArrayList<CartItem> cartItems = new ArrayList<>();
    private List<ProductEntity> allProducts = new ArrayList<>();
    private ProductAdapter productAdapter;
    private CartAdapter cartAdapter;

    // Services
    private SessionManager session;
    private BluetoothPrinterService printerService;
    private AppDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Denomination values
    private static final int[] DENOMINATIONS = {5, 10, 20, 50, 100, 200, 500, 1000};
    private final List<MaterialButton> denomButtons = new ArrayList<>();

    // Current payment method
    private String paymentMethod = "cash";

    // View mode
    private boolean isGridView = true;

    // -------------------------------------------------------------------------
    // onCreate
    // -------------------------------------------------------------------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pos);

        session = new SessionManager(this);
        printerService = new BluetoothPrinterService(this);
        db = AppDatabase.getInstance(this);

        findViews();
        setupToolbar();
        setupProductRecycler();
        setupCartRecycler();
        setupSearch();
        setupLetterFilter();
        setupDenominationButtons();
        setupPaymentChips();
        setupScPwdToggle();
        setupChargeButton();
        checkSyncStatus();

        loadProducts();

        SyncWorker.schedule(this);
        checkForUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------
    private void findViews() {
        toolbar = findViewById(R.id.toolbar);
        editSearch = findViewById(R.id.editSearch);
        letterFilterLayout = findViewById(R.id.letterFilterLayout);
        syncStatusBar = findViewById(R.id.syncStatusBar);
        txtSyncStatus = findViewById(R.id.txtSyncStatus);
        recyclerProducts = findViewById(R.id.recyclerProducts);
        recyclerCart = findViewById(R.id.recyclerCart);
        chkScPwd = findViewById(R.id.chkScPwd);
        editScPwdId = findViewById(R.id.editScPwdId);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        layoutDiscount = findViewById(R.id.layoutDiscount);
        txtDiscount = findViewById(R.id.txtDiscount);
        txtTotal = findViewById(R.id.txtTotal);
        txtCartCount = findViewById(R.id.txtCartCount);
        chipGroupPayment = findViewById(R.id.chipGroupPayment);
        chipCash = findViewById(R.id.chipCash);
        chipGcash = findViewById(R.id.chipGcash);
        chipMaya = findViewById(R.id.chipMaya);
        layoutCashPayment = findViewById(R.id.layoutCashPayment);
        editAmountPaid = findViewById(R.id.editAmountPaid);
        layoutDenominations = findViewById(R.id.layoutDenominations);
        btnCharge = findViewById(R.id.btnCharge);
    }

    // -------------------------------------------------------------------------
    // Toolbar & menu
    // -------------------------------------------------------------------------
    private void setupToolbar() {
        toolbar.setSubtitle(session.getBranchName());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_view_toggle) {
                toggleProductView();
                return true;
            } else if (id == R.id.action_sync) {
                onSyncNow();
                return true;
            } else if (id == R.id.action_printer_setup) {
                showPrinterSetupDialog();
                return true;
            } else if (id == R.id.action_settings) {
                onOpenSettings();
                return true;
            } else if (id == R.id.action_logout) {
                onLogout();
                return true;
            }
            return false;
        });
    }

    private void toggleProductView() {
        isGridView = !isGridView;
        if (isGridView) {
            recyclerProducts.setLayoutManager(new GridLayoutManager(this, 3));
            productAdapter.setViewType(ProductAdapter.VIEW_TYPE_GRID);
        } else {
            recyclerProducts.setLayoutManager(new LinearLayoutManager(this));
            productAdapter.setViewType(ProductAdapter.VIEW_TYPE_LIST);
        }
    }

    // -------------------------------------------------------------------------
    // Product RecyclerView
    // -------------------------------------------------------------------------
    private void setupProductRecycler() {
        recyclerProducts.setLayoutManager(new GridLayoutManager(this, 3));
        productAdapter = new ProductAdapter(product -> addToCart(product));
        recyclerProducts.setAdapter(productAdapter);
    }

    private void loadProducts() {
        executor.execute(() -> {
            List<ProductEntity> products = db.productDao().getAllInStock();
            runOnUiThread(() -> {
                allProducts = products;
                productAdapter.setProducts(products);
            });
        });
    }

    // -------------------------------------------------------------------------
    // Cart RecyclerView
    // -------------------------------------------------------------------------
    private void setupCartRecycler() {
        recyclerCart.setLayoutManager(new LinearLayoutManager(this));
        cartAdapter = new CartAdapter(new CartAdapter.CartActionListener() {
            @Override
            public void onQuantityChanged(int position, int newQuantity) {
                updateQuantity(position, newQuantity);
            }

            @Override
            public void onRemoveItem(int position) {
                removeFromCart(position);
            }
        });
        cartAdapter.setItems(cartItems);
        recyclerCart.setAdapter(cartAdapter);
    }

    // -------------------------------------------------------------------------
    // Search
    // -------------------------------------------------------------------------
    private void setupSearch() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (query.isEmpty()) {
                    productAdapter.setProducts(allProducts);
                } else {
                    filterProducts(query);
                }
            }
        });
    }

    private void filterProducts(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        List<ProductEntity> filtered = new ArrayList<>();
        for (ProductEntity p : allProducts) {
            boolean matchName = p.name != null && p.name.toLowerCase(Locale.ROOT).contains(lower);
            boolean matchGeneric = p.genericName != null && p.genericName.toLowerCase(Locale.ROOT).contains(lower);
            boolean matchBarcode = p.barcode != null && p.barcode.equals(query);
            if (matchName || matchGeneric || matchBarcode) {
                filtered.add(p);
            }
        }
        productAdapter.setProducts(filtered);

        // If exact barcode match with single result, auto-add to cart
        if (filtered.size() == 1 && filtered.get(0).barcode != null
                && filtered.get(0).barcode.equals(query)) {
            addToCart(filtered.get(0));
            editSearch.setText("");
        }
    }

    // -------------------------------------------------------------------------
    // Letter filter (A-Z)
    // -------------------------------------------------------------------------
    private void setupLetterFilter() {
        letterFilterLayout.removeAllViews();

        int padH = dpToPx(16);
        int padV = dpToPx(8);
        int margin = dpToPx(3);

        // "All" button
        MaterialButton allBtn = createLetterButton("All", padH, padV, margin);
        allBtn.setOnClickListener(v -> {
            editSearch.setText("");
            productAdapter.setProducts(allProducts);
        });
        letterFilterLayout.addView(allBtn);

        // A-Z buttons
        for (char c = 'A'; c <= 'Z'; c++) {
            String letter = String.valueOf(c);
            MaterialButton btn = createLetterButton(letter, padH, padV, margin);
            btn.setOnClickListener(v -> {
                editSearch.setText("");
                String l = letter.toLowerCase(Locale.ROOT);
                List<ProductEntity> filtered = new ArrayList<>();
                for (ProductEntity p : allProducts) {
                    if (p.name != null && p.name.toLowerCase(Locale.ROOT).startsWith(l)) {
                        filtered.add(p);
                    }
                }
                productAdapter.setProducts(filtered);
            });
            letterFilterLayout.addView(btn);
        }
    }

    private MaterialButton createLetterButton(String text, int padH, int padV, int margin) {
        MaterialButton btn = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btn.setMinWidth(dpToPx(40));
        btn.setMinimumWidth(dpToPx(40));
        btn.setMinHeight(dpToPx(36));
        btn.setMinimumHeight(dpToPx(36));
        btn.setPadding(padH, padV, padH, padV);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(margin, 0, margin, 0);
        btn.setLayoutParams(lp);
        return btn;
    }

    // -------------------------------------------------------------------------
    // Denomination buttons
    // -------------------------------------------------------------------------
    private void setupDenominationButtons() {
        layoutDenominations.removeAllViews();
        denomButtons.clear();

        int padH = dpToPx(14);
        int padV = dpToPx(8);
        int margin = dpToPx(4);

        for (int denom : DENOMINATIONS) {
            MaterialButton btn = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText("P" + denom);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            btn.setMinWidth(dpToPx(56));
            btn.setMinimumWidth(dpToPx(56));
            btn.setMinHeight(dpToPx(40));
            btn.setMinimumHeight(dpToPx(40));
            btn.setPadding(padH, padV, padH, padV);
            btn.setInsetTop(0);
            btn.setInsetBottom(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(margin, 0, margin, 0);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                editAmountPaid.setText(String.valueOf(denom));
                editAmountPaid.setSelection(editAmountPaid.getText().length());
            });

            btn.setTag(denom);
            denomButtons.add(btn);
            layoutDenominations.addView(btn);
        }
    }

    private void updateDenominationVisibility(double total) {
        for (MaterialButton btn : denomButtons) {
            int denom = (int) btn.getTag();
            btn.setVisibility(denom >= Math.ceil(total) ? View.VISIBLE : View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // Payment chips
    // -------------------------------------------------------------------------
    private void setupPaymentChips() {
        chipGroupPayment.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int checkedId = checkedIds.get(0);
            if (checkedId == R.id.chipCash) {
                paymentMethod = "cash";
                layoutCashPayment.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.chipGcash) {
                paymentMethod = "gcash";
                layoutCashPayment.setVisibility(View.GONE);
            } else if (checkedId == R.id.chipMaya) {
                paymentMethod = "maya";
                layoutCashPayment.setVisibility(View.GONE);
            }
        });
    }

    // -------------------------------------------------------------------------
    // SC/PWD toggle
    // -------------------------------------------------------------------------
    private void setupScPwdToggle() {
        chkScPwd.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editScPwdId.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateTotals();
        });
    }

    // -------------------------------------------------------------------------
    // Charge button
    // -------------------------------------------------------------------------
    private void setupChargeButton() {
        btnCharge.setOnClickListener(v -> processCharge());
    }

    // -------------------------------------------------------------------------
    // Cart operations
    // -------------------------------------------------------------------------
    private void addToCart(ProductEntity product) {
        // Check if already in cart
        for (int i = 0; i < cartItems.size(); i++) {
            CartItem item = cartItems.get(i);
            if (item.productId == product.id) {
                if (item.quantity >= item.maxStock) {
                    Toast.makeText(this, "Not enough stock for " + product.name,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                item.quantity++;
                cartAdapter.notifyItemChanged(i);
                updateTotals();
                return;
            }
        }

        // New item
        if (product.stock <= 0) {
            Toast.makeText(this, "Out of stock: " + product.name, Toast.LENGTH_SHORT).show();
            return;
        }

        CartItem newItem = new CartItem(
                product.id,
                product.name,
                product.sellingPrice,
                1,
                product.isVatable,
                product.stock,
                product.image
        );
        cartItems.add(newItem);
        cartAdapter.notifyItemInserted(cartItems.size() - 1);
        recyclerCart.scrollToPosition(cartItems.size() - 1);
        updateTotals();
    }

    private void removeFromCart(int position) {
        if (position >= 0 && position < cartItems.size()) {
            cartItems.remove(position);
            cartAdapter.notifyItemRemoved(position);
            cartAdapter.notifyItemRangeChanged(position, cartItems.size() - position);
            updateTotals();
        }
    }

    private void updateQuantity(int position, int newQty) {
        if (position >= 0 && position < cartItems.size()) {
            CartItem item = cartItems.get(position);
            if (newQty <= 0) {
                removeFromCart(position);
                return;
            }
            if (newQty > item.maxStock) {
                Toast.makeText(this, "Only " + item.maxStock + " in stock",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            item.quantity = newQty;
            cartAdapter.notifyItemChanged(position);
            updateTotals();
        }
    }

    // -------------------------------------------------------------------------
    // Totals calculation
    // -------------------------------------------------------------------------
    private void updateTotals() {
        double subtotal = 0;
        double discount = 0;
        boolean isScPwd = chkScPwd.isChecked();

        for (CartItem item : cartItems) {
            double lineTotal = item.getLineTotal();
            subtotal += lineTotal;

            if (isScPwd && item.isVatable) {
                // Remove VAT (price is VAT-inclusive): base = price / 1.12
                double vatExclusive = item.unitPrice / 1.12;
                double lineBase = vatExclusive * item.quantity;
                // 20% discount on VAT-exclusive price
                double lineDiscount = lineBase * 0.20;
                discount += lineDiscount;
            }
        }

        double total = subtotal - discount;
        if (total < 0) total = 0;

        txtSubtotal.setText(formatPeso(subtotal));
        txtDiscount.setText("-" + formatPeso(discount));
        txtTotal.setText(formatPeso(total));

        if (isScPwd && discount > 0) {
            layoutDiscount.setVisibility(View.VISIBLE);
        } else {
            layoutDiscount.setVisibility(View.GONE);
        }

        int totalItems = 0;
        for (CartItem item : cartItems) {
            totalItems += item.quantity;
        }
        txtCartCount.setText(totalItems + (totalItems == 1 ? " item" : " items"));

        btnCharge.setText("CHARGE " + formatPeso(total));

        updateDenominationVisibility(total);
    }

    private double calculateTotal() {
        double subtotal = 0;
        double discount = 0;
        boolean isScPwd = chkScPwd.isChecked();

        for (CartItem item : cartItems) {
            subtotal += item.getLineTotal();
            if (isScPwd && item.isVatable) {
                double vatExclusive = item.unitPrice / 1.12;
                double lineBase = vatExclusive * item.quantity;
                discount += lineBase * 0.20;
            }
        }

        double total = subtotal - discount;
        return Math.max(total, 0);
    }

    private double calculateSubtotal() {
        double subtotal = 0;
        for (CartItem item : cartItems) {
            subtotal += item.getLineTotal();
        }
        return subtotal;
    }

    private double calculateDiscount() {
        double discount = 0;
        boolean isScPwd = chkScPwd.isChecked();
        if (!isScPwd) return 0;

        for (CartItem item : cartItems) {
            if (item.isVatable) {
                double vatExclusive = item.unitPrice / 1.12;
                double lineBase = vatExclusive * item.quantity;
                discount += lineBase * 0.20;
            }
        }
        return discount;
    }

    // -------------------------------------------------------------------------
    // Process charge / sale
    // -------------------------------------------------------------------------
    private void processCharge() {
        if (cartItems.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        double total = calculateTotal();
        double amountPaid = total; // default for non-cash

        if ("cash".equals(paymentMethod)) {
            String amountText = editAmountPaid.getText().toString().trim();
            if (amountText.isEmpty()) {
                Toast.makeText(this, "Enter amount paid", Toast.LENGTH_SHORT).show();
                editAmountPaid.requestFocus();
                return;
            }
            try {
                amountPaid = Double.parseDouble(amountText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
                return;
            }
            if (amountPaid < total) {
                Toast.makeText(this, "Insufficient amount. Total is " + formatPeso(total),
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // SC/PWD validation
        String discountType = null;
        String discountIdNumber = null;
        if (chkScPwd.isChecked()) {
            discountType = "sc_pwd";
            discountIdNumber = editScPwdId.getText().toString().trim();
            if (discountIdNumber.isEmpty()) {
                Toast.makeText(this, "Enter SC/PWD ID number", Toast.LENGTH_SHORT).show();
                editScPwdId.requestFocus();
                return;
            }
        }

        // Build sale JSON
        String localId = UUID.randomUUID().toString();
        double subtotal = calculateSubtotal();
        double discountAmount = calculateDiscount();
        double change = amountPaid - total;

        JsonObject saleJson = new JsonObject();
        saleJson.addProperty("local_id", localId);
        saleJson.addProperty("branch_id", session.getBranchId());
        saleJson.addProperty("user_id", session.getUserId());
        saleJson.addProperty("payment_method", paymentMethod);
        saleJson.addProperty("subtotal", subtotal);
        saleJson.addProperty("discount_amount", discountAmount);
        saleJson.addProperty("total", total);
        saleJson.addProperty("amount_paid", amountPaid);
        saleJson.addProperty("change", change);

        if (discountType != null) {
            saleJson.addProperty("discount_type", discountType);
            saleJson.addProperty("discount_id", discountIdNumber);
        }

        JsonArray itemsArray = new JsonArray();
        for (CartItem item : cartItems) {
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("product_id", item.productId);
            itemObj.addProperty("quantity", item.quantity);
            itemObj.addProperty("unit_price", item.unitPrice);
            itemsArray.add(itemObj);
        }
        saleJson.add("items", itemsArray);

        // Save to Room and decrement stock
        final double finalAmountPaid = amountPaid;
        final double finalChange = change;
        final String finalDiscountType = discountType;
        final String finalDiscountIdNumber = discountIdNumber;

        // Copy cart items for receipt before clearing
        final ArrayList<CartItem> receiptItems = new ArrayList<>(cartItems);

        executor.execute(() -> {
            // Save pending sale
            PendingSale pendingSale = new PendingSale();
            pendingSale.localId = localId;
            pendingSale.jsonPayload = saleJson.toString();
            pendingSale.createdAt = System.currentTimeMillis();
            pendingSale.synced = false;
            db.pendingSaleDao().insert(pendingSale);

            // Decrement stock locally
            for (CartItem item : receiptItems) {
                db.productDao().decrementStock(item.productId, item.quantity);
            }

            // Print receipt
            printReceipt(localId, receiptItems, subtotal, discountAmount, total,
                    finalAmountPaid, finalChange, finalDiscountType, finalDiscountIdNumber);

            // Reload products to reflect stock changes
            List<ProductEntity> updated = db.productDao().getAllInStock();

            runOnUiThread(() -> {
                allProducts = updated;
                productAdapter.setProducts(updated);

                // Clear cart
                cartItems.clear();
                cartAdapter.notifyDataSetChanged();
                chkScPwd.setChecked(false);
                editAmountPaid.setText("");
                editSearch.setText("");
                chipCash.setChecked(true);
                updateTotals();

                // Show success dialog
                showSaleSuccessDialog(total, finalAmountPaid, finalChange);
            });

            // Trigger sync if online
            if (NetworkUtils.isOnline(PosActivity.this)) {
                SyncWorker.syncNow(PosActivity.this);
            }
        });
    }

    private void showSaleSuccessDialog(double total, double amountPaid, double change) {
        StringBuilder msg = new StringBuilder();
        msg.append("Total: ").append(formatPeso(total)).append("\n");
        if ("cash".equals(paymentMethod)) {
            msg.append("Paid: ").append(formatPeso(amountPaid)).append("\n");
            msg.append("Change: ").append(formatPeso(change));
        } else {
            msg.append("Payment: ").append(paymentMethod.toUpperCase(Locale.ROOT));
        }

        new AlertDialog.Builder(this)
                .setTitle("Sale Complete")
                .setMessage(msg.toString())
                .setPositiveButton("OK", null)
                .setCancelable(true)
                .show();
    }

    // -------------------------------------------------------------------------
    // Receipt printing
    // -------------------------------------------------------------------------
    private void printReceipt(String localId, List<CartItem> items, double subtotal, double discountAmount,
                              double total, double amountPaid, double change,
                              String discountType, String discountIdNumber) {
        try {
            EscPosCommands.ReceiptData data = new EscPosCommands.ReceiptData();
            data.branchName = session.getBranchName();
            data.branchAddress = session.getBranchAddress();
            data.branchPhone = session.getBranchPhone();
            data.invoiceNumber = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date()) + "-" + localId.substring(0, 4).toUpperCase(Locale.ROOT);
            data.cashierName = session.getUserName();
            data.dateTime = new SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.US).format(new Date());

            List<EscPosCommands.ReceiptItem> receiptItems = new ArrayList<>();
            for (CartItem item : items) {
                EscPosCommands.ReceiptItem ri = new EscPosCommands.ReceiptItem();
                ri.name = item.name;
                ri.quantity = item.quantity;
                ri.unitPrice = item.unitPrice;
                ri.total = item.getLineTotal();
                ri.discount = 0;
                if (discountType != null && item.isVatable) {
                    double base = (item.unitPrice / 1.12) * item.quantity;
                    ri.discount = base * 0.20;
                }
                receiptItems.add(ri);
            }
            data.items = receiptItems;
            data.subtotal = subtotal;
            data.discountAmount = discountAmount;
            data.discountType = discountType != null ? "SC/PWD" : null;
            data.discountIdNumber = discountIdNumber;
            data.totalAmount = total;
            data.amountPaid = amountPaid;
            data.change = change;
            data.paymentMethod = paymentMethod;

            // Calculate tax display
            if (discountType != null) {
                data.taxAmount = 0; // VAT exempt for SC/PWD
            } else {
                // VAT is included in price, show the VAT portion
                double vatAmount = 0;
                for (CartItem item : items) {
                    if (item.isVatable) {
                        vatAmount += (item.unitPrice - item.unitPrice / 1.12) * item.quantity;
                    }
                }
                data.taxAmount = vatAmount;
            }

            byte[] receiptBytes = EscPosCommands.buildReceipt(data);
            printerService.print(receiptBytes);
        } catch (Exception e) {
            // Silently fail - sale is already saved offline
            e.printStackTrace();
        }
    }

    // -------------------------------------------------------------------------
    // Menu actions
    // -------------------------------------------------------------------------
    private void onSyncNow() {
        SyncWorker.syncNow(this);
        Toast.makeText(this, "Sync triggered", Toast.LENGTH_SHORT).show();

        // Reload products after a short delay to pick up synced data
        executor.execute(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
            List<ProductEntity> products = db.productDao().getAllInStock();
            runOnUiThread(() -> {
                allProducts = products;
                productAdapter.setProducts(products);
                checkSyncStatus();
            });
        });
    }

    private void showPrinterSetupDialog() {
        // Check Bluetooth permission for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_BLUETOOTH_PERMISSION);
                return;
            }
        }

        if (!printerService.isBluetoothAvailable()) {
            Toast.makeText(this, "Bluetooth not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!printerService.isBluetoothEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        List<BluetoothPrinterService.PrinterInfo> devices = printerService.getPairedDevices();
        if (devices.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices. Pair a printer first.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        String[] names = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            names[i] = devices.get(i).toString();
        }

        String savedMac = printerService.getSavedPrinterMac();
        int checkedItem = -1;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).macAddress.equals(savedMac)) {
                checkedItem = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Printer")
                .setSingleChoiceItems(names, checkedItem, (dialog, which) -> {
                    BluetoothPrinterService.PrinterInfo selected = devices.get(which);
                    printerService.savePrinter(selected.name, selected.macAddress);
                    Toast.makeText(PosActivity.this,
                            "Printer set: " + selected.name, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void onOpenSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void onLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    session.logout();
                    printerService.disconnect();
                    Intent intent = new Intent(PosActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------------------------------------------------------------
    // Sync status
    // -------------------------------------------------------------------------
    private void checkSyncStatus() {
        boolean online = NetworkUtils.isOnline(this);
        if (!online) {
            syncStatusBar.setVisibility(View.VISIBLE);
            txtSyncStatus.setText("Offline mode - sales will sync when connected");
        } else {
            executor.execute(() -> {
                int unsyncedCount = db.pendingSaleDao().getUnsyncedCount();
                runOnUiThread(() -> {
                    if (unsyncedCount > 0) {
                        syncStatusBar.setVisibility(View.VISIBLE);
                        txtSyncStatus.setText(unsyncedCount + " sale(s) pending sync");
                    } else {
                        syncStatusBar.setVisibility(View.GONE);
                    }
                });
            });
        }
    }

    // -------------------------------------------------------------------------
    // App updates
    // -------------------------------------------------------------------------
    private void checkForUpdates() {
        AppUpdater.checkForUpdate(this, new AppUpdater.UpdateCheckCallback() {
            @Override
            public void onUpdateAvailable(String newVersion, String downloadUrl) {
                new AlertDialog.Builder(PosActivity.this)
                        .setTitle("Update Available")
                        .setMessage("Version " + newVersion + " is available. Update now?")
                        .setPositiveButton("Update", (d, w) ->
                                AppUpdater.downloadAndInstall(PosActivity.this, downloadUrl))
                        .setNegativeButton("Later", null)
                        .show();
            }

            @Override
            public void onNoUpdate() {}

            @Override
            public void onError(String message) {}
        });
    }

    // -------------------------------------------------------------------------
    // Bluetooth permission callback
    // -------------------------------------------------------------------------
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showPrinterSetupDialog();
            } else {
                Toast.makeText(this, "Bluetooth permission required for printing",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------
    private String formatPeso(double amount) {
        return String.format("P%,.2f", amount);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}
