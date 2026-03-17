package com.sanlei.pos.ui.transactions;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sanlei.pos.R;
import com.sanlei.pos.data.api.ApiClient;
import com.sanlei.pos.data.api.ApiService;
import com.sanlei.pos.printer.BluetoothPrinterService;
import com.sanlei.pos.printer.EscPosCommands;
import com.sanlei.pos.util.NetworkUtils;
import com.sanlei.pos.util.SessionManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TransactionsActivity extends AppCompatActivity {

    private RecyclerView recyclerTransactions;
    private ProgressBar progressBar;
    private TextView txtEmpty;
    private EditText editSearch;
    private ChipGroup chipGroupFilter;

    private SessionManager session;
    private ApiService api;
    private BluetoothPrinterService printerService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<JsonObject> allSales = new ArrayList<>();
    private List<JsonObject> filteredSales = new ArrayList<>();
    private TransactionAdapter adapter;
    private String currentFilter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transactions);

        session = new SessionManager(this);
        api = ApiClient.getService(session.getToken());
        printerService = new BluetoothPrinterService(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        editSearch = findViewById(R.id.editSearch);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        recyclerTransactions = findViewById(R.id.recyclerTransactions);
        progressBar = findViewById(R.id.progressBar);
        txtEmpty = findViewById(R.id.txtEmpty);

        adapter = new TransactionAdapter();
        recyclerTransactions.setLayoutManager(new LinearLayoutManager(this));
        recyclerTransactions.setAdapter(adapter);

        setupSearch();
        setupFilterChips();
        loadTransactions(null);
    }

    private void setupSearch() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                applySearchFilter();
            }
        });
    }

    private void setupFilterChips() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipAll) currentFilter = null;
            else if (id == R.id.chipCompleted) currentFilter = "completed";
            else if (id == R.id.chipVoided) currentFilter = "voided";
            else if (id == R.id.chipRefunded) currentFilter = "refunded";
            loadTransactions(currentFilter);
        });
    }

    private void loadTransactions(String status) {
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        txtEmpty.setVisibility(View.GONE);

        api.getRecentSales(status).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    allSales.clear();
                    JsonArray arr = response.body().getAsJsonArray("sales");
                    for (int i = 0; i < arr.size(); i++) {
                        allSales.add(arr.get(i).getAsJsonObject());
                    }
                    applySearchFilter();
                } else {
                    Toast.makeText(TransactionsActivity.this, "Failed to load", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TransactionsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applySearchFilter() {
        String query = editSearch.getText().toString().trim().toLowerCase();
        filteredSales.clear();
        for (JsonObject sale : allSales) {
            String invoice = sale.get("invoice_number").getAsString().toLowerCase();
            if (query.isEmpty() || invoice.contains(query)) {
                filteredSales.add(sale);
            }
        }
        adapter.notifyDataSetChanged();
        txtEmpty.setVisibility(filteredSales.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerTransactions.setVisibility(filteredSales.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showSaleDetail(int saleId) {
        progressBar.setVisibility(View.VISIBLE);
        api.getSale(saleId).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject sale = response.body().getAsJsonObject("sale");
                    showDetailDialog(sale);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TransactionsActivity.this, "Failed to load details", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showDetailDialog(JsonObject sale) {
        int saleId = sale.get("id").getAsInt();
        String status = sale.get("status").getAsString();
        String invoice = sale.get("invoice_number").getAsString();
        double total = sale.get("total_amount").getAsDouble();
        double subtotal = sale.get("subtotal").getAsDouble();
        double discount = sale.get("discount_amount").getAsDouble();
        double paid = sale.get("amount_paid").getAsDouble();
        double change = sale.get("change_amount").getAsDouble();
        String payment = sale.get("payment_method").getAsString().toUpperCase();
        String cashier = sale.has("cashier") && !sale.get("cashier").isJsonNull() ? sale.get("cashier").getAsString() : "";
        String dateStr = sale.get("created_at").getAsString();
        JsonArray items = sale.getAsJsonArray("items");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(8));
        scrollView.addView(layout);

        // Header
        addBoldText(layout, invoice, 18, Color.parseColor("#065F46"));
        addText(layout, formatDate(dateStr) + " | " + cashier, 13, Color.parseColor("#6B7280"));
        addText(layout, payment + " | " + status.toUpperCase(), 13,
                status.equals("completed") ? Color.parseColor("#059669") :
                status.equals("voided") ? Color.parseColor("#DC2626") : Color.parseColor("#D97706"));

        // Divider
        addDivider(layout);

        // Items
        addBoldText(layout, "Items", 14, Color.parseColor("#111827"));
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            String name = item.has("product_name") && !item.get("product_name").isJsonNull()
                    ? item.get("product_name").getAsString() : "Product #" + item.get("product_id").getAsInt();
            int qty = item.get("quantity").getAsInt();
            double price = item.get("unit_price").getAsDouble();
            double lineTotal = item.get("total").getAsDouble();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(4), 0, dp(4));

            TextView left = new TextView(this);
            left.setText(name + " x" + qty + " @" + formatPeso(price));
            left.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            row.addView(left);

            TextView right = new TextView(this);
            right.setText(formatPeso(lineTotal));
            right.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            right.setTextColor(Color.parseColor("#111827"));
            right.setTypeface(null, Typeface.BOLD);
            row.addView(right);

            layout.addView(row);
        }

        addDivider(layout);

        // Totals
        addTotalRow(layout, "Subtotal", formatPeso(subtotal));
        if (discount > 0) addTotalRow(layout, "Discount", "-" + formatPeso(discount));
        addTotalRow(layout, "Total", formatPeso(total));
        addTotalRow(layout, "Paid", formatPeso(paid));
        if (change > 0) addTotalRow(layout, "Change", formatPeso(change));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(scrollView);

        // Action buttons
        if (status.equals("completed")) {
            builder.setNeutralButton("Void Sale", (d, w) -> showVoidDialog(saleId, invoice));
            builder.setNegativeButton("Refund", (d, w) -> showRefundDialog(saleId, invoice, items));
        }
        builder.setPositiveButton("Reprint", (d, w) -> reprintReceipt(sale));
        builder.show();
    }

    private void showVoidDialog(int saleId, String invoice) {
        EditText input = new EditText(this);
        input.setHint("Reason for voiding");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        new AlertDialog.Builder(this)
                .setTitle("Void Sale " + invoice)
                .setMessage("This will void the entire sale and restore inventory. This cannot be undone.")
                .setView(input)
                .setPositiveButton("Void", (d, w) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Reason is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performVoid(saleId, reason);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performVoid(int saleId, String reason) {
        JsonObject body = new JsonObject();
        body.addProperty("reason", reason);
        progressBar.setVisibility(View.VISIBLE);

        api.voidSale(saleId, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Toast.makeText(TransactionsActivity.this, "Sale voided", Toast.LENGTH_SHORT).show();
                    loadTransactions(currentFilter);
                } else {
                    String msg = "Failed to void";
                    try {
                        JsonObject err = new com.google.gson.JsonParser().parse(
                                response.errorBody().string()).getAsJsonObject();
                        msg = err.get("message").getAsString();
                    } catch (Exception ignored) {}
                    Toast.makeText(TransactionsActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TransactionsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showRefundDialog(int saleId, String invoice, JsonArray items) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(8));
        scrollView.addView(layout);

        addBoldText(layout, "Refund Items - " + invoice, 16, Color.parseColor("#D97706"));
        addText(layout, "Select items and quantities to refund:", 13, Color.parseColor("#6B7280"));

        List<CheckBox> checkBoxes = new ArrayList<>();
        List<EditText> qtyInputs = new ArrayList<>();
        List<Integer> itemIds = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            int itemId = item.get("id").getAsInt();
            String name = item.has("product_name") && !item.get("product_name").isJsonNull()
                    ? item.get("product_name").getAsString() : "Product";
            int qty = item.get("quantity").getAsInt();
            double price = item.get("unit_price").getAsDouble();

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));

            CheckBox cb = new CheckBox(this);
            cb.setText(name + " @" + formatPeso(price));
            cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            cb.setLayoutParams(cbLp);
            row.addView(cb);
            checkBoxes.add(cb);
            itemIds.add(itemId);

            TextView label = new TextView(this);
            label.setText("Qty: ");
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.addView(label);

            EditText qtyInput = new EditText(this);
            qtyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            qtyInput.setText(String.valueOf(qty));
            qtyInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            qtyInput.setWidth(dp(50));
            row.addView(qtyInput);
            qtyInputs.add(qtyInput);

            TextView maxLabel = new TextView(this);
            maxLabel.setText("/" + qty);
            maxLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            maxLabel.setTextColor(Color.parseColor("#9CA3AF"));
            row.addView(maxLabel);

            layout.addView(row);
        }

        addDivider(layout);

        // Reason
        addBoldText(layout, "Reason *", 13, Color.parseColor("#374151"));
        EditText reasonInput = new EditText(this);
        reasonInput.setHint("Reason for refund");
        reasonInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reasonInput.setMinLines(2);
        reasonInput.setPadding(dp(8), dp(8), dp(8), dp(8));
        layout.addView(reasonInput);

        new AlertDialog.Builder(this)
                .setView(scrollView)
                .setPositiveButton("Confirm Refund", (d, w) -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(this, "Reason is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    JsonArray refundItems = new JsonArray();
                    for (int i = 0; i < checkBoxes.size(); i++) {
                        if (checkBoxes.get(i).isChecked()) {
                            int qty;
                            try {
                                qty = Integer.parseInt(qtyInputs.get(i).getText().toString());
                            } catch (NumberFormatException e) {
                                continue;
                            }
                            if (qty <= 0) continue;
                            JsonObject ri = new JsonObject();
                            ri.addProperty("sale_item_id", itemIds.get(i));
                            ri.addProperty("quantity", qty);
                            refundItems.add(ri);
                        }
                    }

                    if (refundItems.size() == 0) {
                        Toast.makeText(this, "Select at least one item", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    performRefund(saleId, reason, refundItems);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performRefund(int saleId, String reason, JsonArray items) {
        JsonObject body = new JsonObject();
        body.addProperty("reason", reason);
        body.add("items", items);
        progressBar.setVisibility(View.VISIBLE);

        api.refundSale(saleId, body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    double amt = response.body().get("refund_amount").getAsDouble();
                    Toast.makeText(TransactionsActivity.this,
                            "Refunded " + formatPeso(amt), Toast.LENGTH_LONG).show();
                    loadTransactions(currentFilter);
                } else {
                    String msg = "Failed to refund";
                    try {
                        JsonObject err = new com.google.gson.JsonParser().parse(
                                response.errorBody().string()).getAsJsonObject();
                        msg = err.get("message").getAsString();
                    } catch (Exception ignored) {}
                    Toast.makeText(TransactionsActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(TransactionsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void reprintReceipt(JsonObject sale) {
        executor.execute(() -> {
            try {
                EscPosCommands.ReceiptData rd = new EscPosCommands.ReceiptData();
                rd.storeName = "Sanlei Pharmacy";
                rd.branchName = session.getBranchName();
                rd.invoiceNumber = sale.get("invoice_number").getAsString();
                rd.cashierName = sale.has("cashier") && !sale.get("cashier").isJsonNull()
                        ? sale.get("cashier").getAsString() : "";
                rd.dateTime = formatDate(sale.get("created_at").getAsString());
                rd.paymentMethod = sale.get("payment_method").getAsString();
                rd.subtotal = sale.get("subtotal").getAsDouble();
                rd.discount = sale.get("discount_amount").getAsDouble();
                rd.total = sale.get("total_amount").getAsDouble();
                rd.amountPaid = sale.get("amount_paid").getAsDouble();
                rd.change = sale.get("change_amount").getAsDouble();

                rd.items = new ArrayList<>();
                JsonArray items = sale.getAsJsonArray("items");
                for (int i = 0; i < items.size(); i++) {
                    JsonObject item = items.get(i).getAsJsonObject();
                    EscPosCommands.ReceiptItem ri = new EscPosCommands.ReceiptItem();
                    ri.name = item.has("product_name") && !item.get("product_name").isJsonNull()
                            ? item.get("product_name").getAsString() : "Item";
                    ri.quantity = item.get("quantity").getAsInt();
                    ri.unitPrice = item.get("unit_price").getAsDouble();
                    ri.total = item.get("total").getAsDouble();
                    rd.items.add(ri);
                }

                byte[] receipt = EscPosCommands.buildReceipt(rd);
                printerService.print(receipt);
                runOnUiThread(() -> Toast.makeText(this, "Receipt printed", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Print failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ---- Helpers ----

    private String formatPeso(double amount) {
        return String.format(Locale.US, "P%,.2f", amount);
    }

    private String formatDate(String isoDate) {
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            Date date = iso.parse(isoDate);
            SimpleDateFormat display = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.US);
            return display.format(date);
        } catch (Exception e) {
            return isoDate;
        }
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    private void addText(LinearLayout parent, String text, int sizeSp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        tv.setPadding(0, dp(2), 0, dp(2));
        parent.addView(tv);
    }

    private void addBoldText(LinearLayout parent, String text, int sizeSp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(2), 0, dp(2));
        parent.addView(tv);
    }

    private void addDivider(LinearLayout parent) {
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#E5E7EB"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        div.setLayoutParams(lp);
        parent.addView(div);
    }

    private void addTotalRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(2), 0, dp(2));

        TextView left = new TextView(this);
        left.setText(label);
        left.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        left.setTextColor(Color.parseColor("#6B7280"));
        left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(left);

        TextView right = new TextView(this);
        right.setText(value);
        right.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        right.setTextColor(Color.parseColor("#111827"));
        right.setTypeface(null, Typeface.BOLD);
        row.addView(right);

        parent.addView(row);
    }

    // ---- Adapter ----

    class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_transaction, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            JsonObject sale = filteredSales.get(position);
            h.txtInvoice.setText(sale.get("invoice_number").getAsString());
            h.txtDate.setText(formatDate(sale.get("created_at").getAsString()));
            h.txtTotal.setText(formatPeso(sale.get("total_amount").getAsDouble()));
            h.txtPayment.setText(sale.get("payment_method").getAsString().toUpperCase());
            h.txtItemsCount.setText(sale.get("items_count").getAsInt() + " items");
            h.txtCashier.setText(sale.has("cashier") && !sale.get("cashier").isJsonNull()
                    ? sale.get("cashier").getAsString() : "");

            String status = sale.get("status").getAsString();
            h.txtStatus.setText(status.toUpperCase());
            switch (status) {
                case "completed":
                    h.txtStatus.setTextColor(Color.parseColor("#059669"));
                    h.txtStatus.setBackgroundColor(Color.parseColor("#D1FAE5"));
                    break;
                case "voided":
                    h.txtStatus.setTextColor(Color.parseColor("#DC2626"));
                    h.txtStatus.setBackgroundColor(Color.parseColor("#FEE2E2"));
                    h.txtTotal.setTextColor(Color.parseColor("#DC2626"));
                    break;
                case "refunded":
                    h.txtStatus.setTextColor(Color.parseColor("#D97706"));
                    h.txtStatus.setBackgroundColor(Color.parseColor("#FEF3C7"));
                    h.txtTotal.setTextColor(Color.parseColor("#D97706"));
                    break;
                default:
                    h.txtTotal.setTextColor(Color.parseColor("#059669"));
                    break;
            }

            h.itemView.setOnClickListener(v -> showSaleDetail(sale.get("id").getAsInt()));
        }

        @Override
        public int getItemCount() {
            return filteredSales.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView txtInvoice, txtDate, txtTotal, txtPayment, txtStatus, txtItemsCount, txtCashier;

            VH(@NonNull View v) {
                super(v);
                txtInvoice = v.findViewById(R.id.txtInvoice);
                txtDate = v.findViewById(R.id.txtDate);
                txtTotal = v.findViewById(R.id.txtTotal);
                txtPayment = v.findViewById(R.id.txtPayment);
                txtStatus = v.findViewById(R.id.txtStatus);
                txtItemsCount = v.findViewById(R.id.txtItemsCount);
                txtCashier = v.findViewById(R.id.txtCashier);
            }
        }
    }
}
