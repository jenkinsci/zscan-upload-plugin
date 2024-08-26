package com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos;

public class LoginCredentials {
    @SuppressWarnings("unused")
    private final String clientId;
    @SuppressWarnings("unused")
    private final String secret;

    public LoginCredentials(String clientId, String secret) {
        this.clientId = clientId;
        this.secret = secret;
    }
}
