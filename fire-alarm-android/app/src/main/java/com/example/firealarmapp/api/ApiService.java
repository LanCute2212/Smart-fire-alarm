package com.example.firealarmapp.api;

import com.example.firealarmapp.models.ClaimDeviceRequest;
import com.example.firealarmapp.models.AlertHistoryModel;
import com.example.firealarmapp.models.AuthRequest;
import com.example.firealarmapp.models.AuthResponse;
import com.example.firealarmapp.models.Device;
import com.example.firealarmapp.models.RegisterRequest;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    
    @POST("/api/auth/login")
    Call<AuthResponse> login(@Body AuthRequest request);

    @POST("/api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @GET("/api/devices")
    Call<List<Device>> getDevices();

    @GET("/api/alerts/{deviceId}")
    Call<List<AlertHistoryModel>> getAlertHistory(@Path("deviceId") String deviceId);

    @PATCH("/api/alerts/{alertId}/resolve")
    Call<Void> resolveAlert(@Path("alertId") String alertId);

    @POST("/api/devices/{deviceId}/silence")
    Call<String> silenceAlarm(@Path("deviceId") String deviceId, @Query("durationSeconds") int durationSeconds);

    @POST("/api/devices/claim")
    Call<Map<String, Object>> claimDevice(@Body ClaimDeviceRequest request);

    @POST("/api/devices/provision")
    Call<Map<String, Object>> provisionDevice(@Body Map<String, String> request);

    @DELETE("/api/devices/{deviceId}")
    Call<Map<String, Object>> deleteDevice(@Path("deviceId") String deviceId);

    @PATCH("/api/devices/{deviceId}")
    Call<Device> updateDevice(@Path("deviceId") String deviceId, @Body Map<String, String> body);
}
