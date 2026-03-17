package com.sanlei.pos.data.api;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
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

    @GET("api/sales/recent")
    Call<JsonObject> getRecentSales(@Query("status") String status);

    @GET("api/sales/{id}")
    Call<JsonObject> getSale(@Path("id") int id);

    @POST("api/sales/{id}/void")
    Call<JsonObject> voidSale(@Path("id") int id, @Body JsonObject body);

    @POST("api/sales/{id}/refund")
    Call<JsonObject> refundSale(@Path("id") int id, @Body JsonObject body);

    @GET("api/app-version")
    Call<JsonObject> getAppVersion();
}
