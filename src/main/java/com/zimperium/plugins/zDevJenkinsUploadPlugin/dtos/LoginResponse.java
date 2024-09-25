package com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class LoginResponse {

    // this class is not serialized to *storage*; it is only used in-memory
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    @SerializedName("accessToken")
    @Expose
    private String accessToken;

    // this class is not serialized to *storage*; it is only used in-memory
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    @SerializedName("refreshToken")
    @Expose
    private String refreshToken;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

}