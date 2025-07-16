package com.checkmarx.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class CheckmarxValidator {

    private static final Logger LOGGER = Logger.getInstance(CheckmarxValidator.class);

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
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (isTenantCheck && (responseCode == 404 || responseCode == 405)) {
                return false;
            }
            return responseCode < 400;

        } catch (IOException e) {
            LOGGER.warn("Failed to reach URL: " + urlStr, e);
            return false;
        }
    }

    private static boolean isValidUrl(String url) {
        try {
            new URL(url);
            return true;
        } catch (Exception e) {
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
