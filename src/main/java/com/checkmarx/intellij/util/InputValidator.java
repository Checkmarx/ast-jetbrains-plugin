package com.checkmarx.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.CompletableFuture;

public class InputValidator {

    private static final Logger LOGGER = Logger.getInstance(InputValidator.class);

    public static CompletableFuture<ValidationResult> validateConnection(String baseUri, String tenant) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isValidUrl(baseUri)) {
                    return new ValidationResult(false, "Invalid URL protocol. Please use http:// or https://");
                }

                if (tenant == null || tenant.trim().isEmpty()) {
                    return new ValidationResult(false, "Tenant name cannot be empty");
                }

                if (!checkUrlExists(baseUri, false)) {
                    return new ValidationResult(false, "Please check the server address of your Checkmarx One environment.");
                }

                String tenantUrl = baseUri.endsWith("/") ? baseUri + "auth/realms/" + tenant
                        : baseUri + "/auth/realms/" + tenant;

                if (!checkUrlExists(tenantUrl, true)) {
                    return new ValidationResult(false, String.format("Tenant \"%s\" not found. Please check your tenant name.", tenant));
                }

                return new ValidationResult(true, "");

            } catch (Exception e) {
                LOGGER.warn("Validation failed", e);
                return new ValidationResult(false, "Could not connect to server. Please check your Base URI.");
            }
        });
    }

    private static boolean checkUrlExists(String urlStr, boolean isTenantCheck) {
        try {
            URL url = new URI(urlStr).toURL();  // used URI instead of deprecated constructor
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (isTenantCheck && (responseCode == 404 || responseCode == 405)) {
                return false;
            }
            return responseCode < 400;

        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            LOGGER.warn("Failed to reach URL: " + urlStr, e);
            return false;
        }
    }

    public static boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            return uri.isAbsolute() && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static class ValidationResult {
        public final boolean isValid;
        public final String error;

        public ValidationResult(boolean isValid, String error) {
            this.isValid = isValid;
            this.error = error;
        }
    }
}
