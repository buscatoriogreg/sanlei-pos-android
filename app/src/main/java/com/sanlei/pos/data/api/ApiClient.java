package com.sanlei.pos.data.api;

import android.content.Context;

import com.sanlei.pos.util.SessionManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL = "https://sp.rgbpos.com/";
    private static ApiService apiService;

    public static ApiService getService(Context context) {
        if (apiService == null) {
            SessionManager session = new SessionManager(context);

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request.Builder builder = chain.request().newBuilder()
                                    .header("Accept", "application/json");
                            String token = session.getToken();
                            if (token != null) {
                                builder.header("Authorization", "Bearer " + token);
                            }
                            return chain.proceed(builder.build());
                        }
                    })
                    .addInterceptor(logging)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            apiService = retrofit.create(ApiService.class);
        }
        return apiService;
    }

    public static void reset() {
        apiService = null;
    }
}
