package com.sanlei.pos.data.api;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    @POST("api/login")
    Call<JsonObject> login(@Body JsonObject body);

    @POST("api/logout")
    Call<JsonObject> logout();

    @GET("api/sync/products")
    Call<JsonObject> getProducts(@Query("since") String since);

    @GET("api/sync/categories")
    Call<JsonObject> getCategories();

    @POST("api/sales")
    Call<JsonObject> postSale(@Body JsonObject body);

    @GET("api/app-version")
    Call<JsonObject> getAppVersion();
}
