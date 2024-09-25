package com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos;

public class RefreshCredentials {
    // this class is not serialized to *storage*; it is only used in-memory
    @SuppressWarnings({"unused", "lgtm[jenkins/plaintext-storage]"})
    private final String refreshToken;

    public RefreshCredentials(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}