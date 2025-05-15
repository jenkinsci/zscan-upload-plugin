package com.zimperium.plugins.zDevJenkinsUploadPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

import org.apache.commons.lang.StringUtils;

import hudson.ProxyConfiguration;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import okhttp3.Authenticator;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.Request;

public class ZDevHTTPClient {

    public OkHttpClient getHttpClient(Boolean useProxy, int connectionTimeoutMillis, int writeTimeoutMillis, int readTimeoutMillis) {
        OkHttpClient.Builder okClientBuilder = new OkHttpClient.Builder()
                .connectTimeout(connectionTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS);
        if (useProxy) {
            ProxyConfiguration proxyConfig = Jenkins.get().getProxy();
            if(proxyConfig != null) {
                Proxy _httpProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyConfig.getName(), proxyConfig.getPort()));
                // check if proxy requires authentication
                if(proxyConfig.getUserName() != null && StringUtils.isNotBlank(proxyConfig.getUserName())) {
                
                    String proxyUserInfo = proxyConfig.getUserName() + ":" + Secret.toString(proxyConfig.getSecretPassword());
                    String basicAuth = new String(
                            Base64.getEncoder() // get the base64 encoder
                                    .encode(proxyUserInfo.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                    Authenticator _httpProxyAuth = new Authenticator() {
                        @Nullable
                        @Override
                        public Request authenticate(Route route, Response response) throws IOException {
                            return response.request().newBuilder()
                                    .addHeader("Proxy-Authorization", "Basic " + basicAuth) // add auth
                                    .build();
                        }
                    } ;
                    return okClientBuilder.proxyAuthenticator(_httpProxyAuth).proxy(_httpProxy).build();
                }
                // if no authentication is required, just set the proxy
                return okClientBuilder.proxy(_httpProxy).build();
            }
        }
        // if no proxy is required, just build the client
        return okClientBuilder.build();
    }
}
