package com.sanlei.pos.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;
import com.sanlei.pos.R;
import com.sanlei.pos.data.api.ApiClient;
import com.sanlei.pos.ui.pos.PosActivity;
import com.sanlei.pos.util.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText editEmail, editPassword;
    private MaterialButton btnLogin;
    private TextView txtError;
    private ProgressBar progress;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);

        if (session.isLoggedIn()) {
            goToPOS();
            return;
        }

        setContentView(R.layout.activity_login);

        editEmail = findViewById(R.id.editEmail);
        editPassword = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        txtError = findViewById(R.id.txtError);
        progress = findViewById(R.id.progressLogin);

        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void doLogin() {
        String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";
        String password = editPassword.getText() != null ? editPassword.getText().toString().trim() : "";

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter email and password");
            return;
        }

        btnLogin.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        txtError.setVisibility(View.GONE);

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("device_name", android.os.Build.MODEL);

        ApiClient.getService(this).login(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                btnLogin.setEnabled(true);
                progress.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    JsonObject data = response.body();
                    String token = data.get("token").getAsString();
                    JsonObject user = data.getAsJsonObject("user");

                    session.saveLogin(
                            token,
                            user.get("id").getAsInt(),
                            user.get("name").getAsString(),
                            user.get("email").getAsString(),
                            user.get("role").getAsString(),
                            user.get("branch_id").getAsInt(),
                            user.has("branch_name") && !user.get("branch_name").isJsonNull() ? user.get("branch_name").getAsString() : "",
                            user.has("branch_code") && !user.get("branch_code").isJsonNull() ? user.get("branch_code").getAsString() : "",
                            user.has("branch_address") && !user.get("branch_address").isJsonNull() ? user.get("branch_address").getAsString() : "",
                            user.has("branch_phone") && !user.get("branch_phone").isJsonNull() ? user.get("branch_phone").getAsString() : ""
                    );

                    ApiClient.reset(); // reset to use new token
                    goToPOS();
                } else {
                    showError("Invalid email or password");
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                btnLogin.setEnabled(true);
                progress.setVisibility(View.GONE);
                showError("Network error. Check your connection.");
            }
        });
    }

    private void showError(String msg) {
        txtError.setText(msg);
        txtError.setVisibility(View.VISIBLE);
    }

    private void goToPOS() {
        startActivity(new Intent(this, PosActivity.class));
        finish();
    }
}
