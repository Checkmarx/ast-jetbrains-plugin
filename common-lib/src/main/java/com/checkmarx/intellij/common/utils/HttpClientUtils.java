package com.checkmarx.intellij.common.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Objects;

import static java.lang.String.format;

/**
 * HttpClientUtils class responsible to perform http client related operations.
 * E.g., building an http client, adding proxy support, etc.
 */
public final class HttpClientUtils {

    private static final Logger LOGGER = Utils.getLogger(HttpClientUtils.class);

    /**
     * Creating an http client with proxy support.
     * Supported proxy IntelliJ IDEA and OS level.
     *
     * @param endpoint - endpoint which is called using an http client
     * @return HttpClient with proxy support
     */
    public static HttpClient createHttpClient(@NotNull String endpoint) {
        try {
            LOGGER.info("HttpClient: Creating http client with proxy support.");
            HttpClient.Builder builder          = HttpClient.newBuilder();
            URI                uri              = URI.create(endpoint);
            HttpConfigurable proxyConfig        = HttpConfigurable.getInstance();
            ProxySelector intelliJProxySelector = proxyConfig.getOnlyBySettingsSelector();
            List<Proxy> proxies                 = intelliJProxySelector.select(uri);

            // In intellij we can configure only one proxy (OS default or Intellij manual)
            Proxy proxy = (proxies == null || proxies.isEmpty()) ? Proxy.NO_PROXY : proxies.get(0);

            if (proxy != null && proxy.type() != Proxy.Type.DIRECT) {
                buildHttpClientWithIntellijProxy(builder, proxy, proxyConfig);
            } else {
                // Fallback to system/OS proxy
                builder.proxy(ProxySelector.getDefault());
            }
            return builder.build();
        } catch (Exception exception) {
            LOGGER.warn(format("HttpClient: Exception occurred while creating http client with proxy support. Root Cause:%s",
                    exception.getMessage()));
            return fallbackHttpClient();
        }
    }

    /**
     * Building an http client with IntelliJ IDEA proxy support
     *
     * @param builder     HttpClient.Builder to add proxy to a client
     * @param proxy       proxy details
     * @param proxyConfig Proxy configuration details
     */
    private static void buildHttpClientWithIntellijProxy(HttpClient.Builder builder, Proxy proxy, HttpConfigurable proxyConfig) {
        LOGGER.info("HttpClient: Building http client with Intellij proxy support.");
        builder.proxy(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                LOGGER.warn(format("HttpClient: Connection failed with the IntelliJ proxy. Root Cause:%s", ioe.getMessage()));
            }
        });
        // Proxy authentication support
        if (proxyConfig.PROXY_AUTHENTICATION) {
            builder.authenticator(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    try {
                        return new PasswordAuthentication(
                                proxyConfig.getProxyLogin(),
                                Objects.requireNonNull(proxyConfig.getPlainProxyPassword()).toCharArray()
                        );
                    } catch (Exception exception) {
                        LOGGER.warn(format("HttpClient: Exception occurred while getting authentication details for Intellij proxy. Root Cause:%s",
                                exception.getMessage()));
                        throw new RuntimeException(exception);
                    }
                }
            });
        }
    }

    /**
     * Fallback method to create http client with system/OS proxy
     *
     * @return HttpClient
     */
    private static HttpClient fallbackHttpClient() {
        try {
            LOGGER.info("HttpClient: Creating http client with OS proxy support.");
            return HttpClient.newBuilder()
                    .proxy(ProxySelector.getDefault())
                    .build();
        } catch (Exception exception) {
            LOGGER.warn(format("HttpClient: Exception occurred while creating http client with OS proxy support. Root Cause:%s",
                    exception.getMessage()));
            return HttpClient.newHttpClient();
        }
    }
}
