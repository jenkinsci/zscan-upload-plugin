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
}
