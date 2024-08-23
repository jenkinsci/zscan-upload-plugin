package com.zimperium.plugins.zDevJenkinsUploadPlugin.dtos;

public class LoginCredentials {
    private final String clientId;
    private final String secret;

    public LoginCredentials(String clientId, String secret) {
        this.clientId = clientId;
        this.secret = secret;
    }
}
