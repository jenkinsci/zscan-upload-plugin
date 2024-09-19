package com.zimperium.plugins.zDevJenkinsUploadPlugin.services;

import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.LoginCredentials;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.LoginResponse;
import com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos.RefreshCredentials;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Service;
import retrofit2.Call;
import retrofit2.http.*;

@Service
public interface UploadPluginService {

    @Headers("Content-Type: application/json")
    @POST("api/auth/v1/api_keys/login")
    Call<LoginResponse> login(@Body LoginCredentials body);

    @Headers("Content-Type: application/json")
    @POST("api/auth/v1/api_keys/access")
    Call<LoginResponse> refreshAccess(@Body RefreshCredentials body);

    @POST("api/zdev-upload/v1/uploads/build")
    Call<ResponseBody> upload(@Header("Authorization") String clientSecret, @Body RequestBody body);

    @PUT("api/zdev-app/public/v1/apps/{appId}/upload")
    Call<ResponseBody> assignTeam(@Path("appId") String appId, @Header("Authorization") String clientSecret, @Body RequestBody body);

    @GET("api/auth/public/v1/teams")
    Call<ResponseBody> listTeams(@Header("Authorization") String clientSecret);

    @GET("api/zdev-app/pub/v1/assessments/status")
    Call<ResponseBody> checkStatus(@Query("buildId") String buildId,  @Header("Authorization") String clientSecret);

    @GET("api/zdev-app/pub/v1/assessments/{assessmentId}/{report_format}")
    @Streaming
    Call<ResponseBody> downloadReport(@Path("assessmentId") String assessmentId, @Path("report_format") String reportFormat,  @Header("Authorization") String clientSecret);
}
