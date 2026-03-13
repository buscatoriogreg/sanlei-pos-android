package com.sanlei.pos.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.gson.JsonObject;
import com.sanlei.pos.data.api.ApiClient;

import java.io.File;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AppUpdater {

    public interface UpdateCheckCallback {
        void onUpdateAvailable(String newVersion, String downloadUrl);
        void onNoUpdate();
        void onError(String message);
    }

    public static void checkForUpdate(Context context, UpdateCheckCallback callback) {
        ApiClient.getService(context).getAppVersion().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject body = response.body();
                    int serverCode = body.get("version_code").getAsInt();
                    String serverVersion = body.get("version_name").getAsString();
                    String downloadUrl = body.get("download_url").getAsString();

                    int currentCode = getCurrentVersionCode(context);
                    if (serverCode > currentCode) {
                        callback.onUpdateAvailable(serverVersion, downloadUrl);
                    } else {
                        callback.onNoUpdate();
                    }
                } else {
                    callback.onError("Failed to check for updates");
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public static void downloadAndInstall(Context context, String downloadUrl) {
        Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show();

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("Sanlei POS Update")
                .setDescription("Downloading new version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "sanlei-pos-update.apk");

        long downloadId = dm.enqueue(request);

        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    ctx.unregisterReceiver(this);
                    installApk(ctx);
                }
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
    }

    private static void installApk(Context context) {
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), "sanlei-pos-update.apk");

        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static int getCurrentVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }
}
