package com.sanlei.pos.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sanlei.pos.data.api.ApiClient;
import com.sanlei.pos.data.api.ApiService;
import com.sanlei.pos.data.db.AppDatabase;
import com.sanlei.pos.data.db.entity.PendingSale;
import com.sanlei.pos.util.SessionManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Response;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        SessionManager session = new SessionManager(ctx);

        if (!session.isLoggedIn()) return Result.success();

        try {
            pushSales(ctx, session);
            pullProducts(ctx, session);
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed", e);
            return Result.retry();
        }
    }

    private void pushSales(Context ctx, SessionManager session) throws Exception {
        AppDatabase db = AppDatabase.getInstance(ctx);
        ApiService api = ApiClient.getService(ctx);
        List<PendingSale> unsynced = db.pendingSaleDao().getUnsynced();

        for (PendingSale sale : unsynced) {
            try {
                JsonObject body = JsonParser.parseString(sale.jsonPayload).getAsJsonObject();
                Response<JsonObject> resp = api.postSale(body).execute();
                if (resp.isSuccessful() && resp.body() != null) {
                    String invoice = resp.body().has("invoice_number") ?
                            resp.body().get("invoice_number").getAsString() : "synced";
                    db.pendingSaleDao().markSynced(sale.id, invoice);
                    Log.d(TAG, "Synced sale: " + sale.localId);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to sync sale: " + sale.localId, e);
            }
        }
    }

    private void pullProducts(Context ctx, SessionManager session) throws Exception {
        ApiService api = ApiClient.getService(ctx);
        Response<JsonObject> resp = api.getProducts(session.getLastSync()).execute();
        if (resp.isSuccessful() && resp.body() != null) {
            JsonObject body = resp.body();
            if (body.has("products")) {
                List<com.sanlei.pos.data.db.entity.ProductEntity> products = new java.util.ArrayList<>();
                for (var el : body.getAsJsonArray("products")) {
                    JsonObject p = el.getAsJsonObject();
                    com.sanlei.pos.data.db.entity.ProductEntity entity = new com.sanlei.pos.data.db.entity.ProductEntity();
                    entity.id = p.get("id").getAsInt();
                    entity.name = p.has("name") && !p.get("name").isJsonNull() ? p.get("name").getAsString() : "";
                    entity.genericName = p.has("generic_name") && !p.get("generic_name").isJsonNull() ? p.get("generic_name").getAsString() : null;
                    entity.barcode = p.has("barcode") && !p.get("barcode").isJsonNull() ? p.get("barcode").getAsString() : null;
                    entity.categoryId = p.has("category_id") && !p.get("category_id").isJsonNull() ? p.get("category_id").getAsInt() : 0;
                    entity.categoryName = p.has("category_name") && !p.get("category_name").isJsonNull() ? p.get("category_name").getAsString() : null;
                    entity.unit = p.has("unit") && !p.get("unit").isJsonNull() ? p.get("unit").getAsString() : "piece";
                    entity.costPrice = p.has("cost_price") ? p.get("cost_price").getAsDouble() : 0;
                    entity.sellingPrice = p.has("selling_price") ? p.get("selling_price").getAsDouble() : 0;
                    entity.isVatable = p.has("is_vatable") && p.get("is_vatable").getAsBoolean();
                    entity.isPrescription = p.has("is_prescription") && p.get("is_prescription").getAsBoolean();
                    entity.image = p.has("image") && !p.get("image").isJsonNull() ? p.get("image").getAsString() : null;
                    entity.stock = p.has("stock") ? p.get("stock").getAsInt() : 0;
                    entity.updatedAt = p.has("updated_at") ? p.get("updated_at").getAsString() : null;
                    products.add(entity);
                }
                AppDatabase.getInstance(ctx).productDao().insertAll(products);
            }
            if (body.has("synced_at")) {
                session.setLastSync(body.get("synced_at").getAsString());
            }
            Log.d(TAG, "Products pulled successfully");
        }
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "pos_sync", ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void syncNow(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        androidx.work.OneTimeWorkRequest request = new androidx.work.OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueue(request);
    }
}
