package com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos;

public class LoginCredentials {
    // this class is not serialized to *storage*; it is only used in-memory
    @SuppressWarnings({"unused", "lgtm[jenkins/plaintext-storage]"})
    private final String clientId;
    // this class is not serialized to *storage*; it is only used in-memory
    @SuppressWarnings({"unused", "lgtm[jenkins/plaintext-storage]"})
    private final String secret;

    public LoginCredentials(String clientId, String secret) {
        this.clientId = clientId;
        this.secret = secret;
    }
}
