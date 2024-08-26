package com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos;

public class RefreshCredentials {
    @SuppressWarnings("unused")
    private final String refreshToken;

    public RefreshCredentials(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}