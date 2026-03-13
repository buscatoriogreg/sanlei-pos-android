package com.sanlei.pos.util;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "sanlei_pos_session";
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(String token, int userId, String userName, String userEmail,
                          String role, int branchId, String branchName, String branchCode,
                          String branchAddress, String branchPhone) {
        prefs.edit()
                .putString("token", token)
                .putInt("user_id", userId)
                .putString("user_name", userName)
                .putString("user_email", userEmail)
                .putString("role", role)
                .putInt("branch_id", branchId)
                .putString("branch_name", branchName)
                .putString("branch_code", branchCode)
                .putString("branch_address", branchAddress)
                .putString("branch_phone", branchPhone)
                .apply();
    }

    public String getToken() { return prefs.getString("token", null); }
    public int getUserId() { return prefs.getInt("user_id", 0); }
    public String getUserName() { return prefs.getString("user_name", ""); }
    public String getBranchName() { return prefs.getString("branch_name", ""); }
    public String getBranchCode() { return prefs.getString("branch_code", ""); }
    public String getBranchAddress() { return prefs.getString("branch_address", ""); }
    public String getBranchPhone() { return prefs.getString("branch_phone", ""); }
    public int getBranchId() { return prefs.getInt("branch_id", 0); }
    public boolean isLoggedIn() { return getToken() != null; }

    public void setLastSync(String timestamp) {
        prefs.edit().putString("last_sync", timestamp).apply();
    }
    public String getLastSync() { return prefs.getString("last_sync", null); }

    public void logout() {
        prefs.edit().clear().apply();
    }
}
