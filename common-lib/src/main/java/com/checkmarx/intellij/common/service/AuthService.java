package com.checkmarx.intellij.common.service;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.common.helper.OAuthCallbackServer;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.HttpClientUtils;
import com.checkmarx.intellij.common.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * AuthService class responsible to handle the authentication and authorization mechanism
 * using OAuth PKCE flow
 */
public class AuthService {

    private static final Logger LOGGER = Utils.getLogger(AuthService.class);
    private static final int MIN_PORT = 49152;
    private static final int MAX_PORT = 65535;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int MAX_RETRIES = 3;
    private static final String CALLBACK_PATH = "/checkmarx1/callback";

    protected final Project project;
    protected OAuthCallbackServer server;

    public AuthService(@NotNull Project project) {
        this.project = project;
    }

    /**
     * Authenticate user using OAuth2.0 Authorization Code with PKCE (Proof Key for Code Exchange) grant flow
     *
     * @param cxOneBaseUrl - Checkmarx One base URL provided by the user.
     * @param cxOneTenant  - Checkmarx One-tenant URL provided by the user.
     */
    public void authenticate(final String cxOneBaseUrl, final String cxOneTenant, Consumer<Map<String, Object>> authResult) {
        try {
            LOGGER.info(String.format("OAuth: Authentication started with base URL:%s and tenant:%s", cxOneBaseUrl, cxOneTenant));
            String codeVerifier = Utils.generateCodeVerifier();
            String codeChallenge = Utils.generateCodeChallenge(codeVerifier);

            if (codeVerifier == null || codeChallenge == null) {
                LOGGER.warn("OAuth: Unable to generate code Verifier or code challenge.");
                setAuthErrorResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
                return;
            }
            //Starts a background task with progress indicator without blocking the UI
            new Task.Backgroundable(project, Bundle.message(Resource.CONNECTING_TO_CHECKMARX), false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    indicator.setText(Bundle.message(Resource.WAITING_FOR_AUTHENTICATION));
                    server = new OAuthCallbackServer(CALLBACK_PATH); // initialize the server object
                    processAuthentication(codeVerifier, codeChallenge, cxOneBaseUrl, cxOneTenant, authResult);
                }
            }.queue();
        } catch (Exception exception) {
            LOGGER.warn(String.format("OAuth: Exception occurred while authenticating using oauth2. Root Cause:%s",
                    exception.getMessage()));
            setAuthErrorResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
        }
    }

    /**
     * Start processing the authentication using OAuth flow.
     * This method will be run progress indicator in the background and start the callback server.
     * Once authentication is completed or failed, progress indicator will be closed
     * and success or error notification will be shown to the user.
     *
     * @param codeVerifier  - Original code verifier for PKCE
     * @param codeChallenge - Generated code challenge from code verifier using SHA-256
     * @param cxOneBaseUrl  - Checkmarx One base URL provided by the user.
     * @param cxOneTenant   - Checkmarx One-tenant URL provided by the user.
     */
    protected void processAuthentication(String codeVerifier, String codeChallenge, String cxOneBaseUrl,
                                         String cxOneTenant, Consumer<Map<String, Object>> authResult) {
        try {
            String cxOneAuthEndpoint = getCxOneAuthEndpoint(cxOneBaseUrl, cxOneTenant);
            String cxOneTokenEndpoint = getCxOneTokenEndpoint(cxOneBaseUrl, cxOneTenant);
            int port = findAvailablePort();
            if (port == 0) {
                setAuthErrorResult(authResult, Bundle.message(Resource.ERROR_PORT_NOT_AVAILABLE));
                return;
            }
            String redirectUrl = "http://localhost:" + port + CALLBACK_PATH;
            String authorizationUrl = buildCxOneOAuthAuthorizationUrl(cxOneAuthEndpoint, redirectUrl, codeChallenge);

            server.start(Constants.AuthConstants.TIME_OUT_SECONDS, port);
            openDefaultBrowser(authorizationUrl);

            String authCode = server.waitForAuthCode().get(Constants.AuthConstants.TIME_OUT_SECONDS, TimeUnit.SECONDS);
            Map<String, Object> refreshTokenDetails = exchangeCodeForToken(cxOneTokenEndpoint, authCode, codeVerifier, redirectUrl);
            if (refreshTokenDetails == null || refreshTokenDetails.isEmpty()) {
                LOGGER.warn("OAuth: Not able to get refresh token. Refresh token is null.");
                setAuthErrorResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
                return;
            }
            saveToken(refreshTokenDetails.get(Constants.AuthConstants.REFRESH_TOKEN).toString()); // Save refresh token in storage
            setAuthSuccessResult(authResult, refreshTokenDetails); //Return token
            LOGGER.info("OAuth: Authentication completed successfully.");
        } catch (ExecutionException | TimeoutException timeoutException) {
            LOGGER.warn("OAuth: Time out exception occurred. Auth code not received within authentication timeout");
            setAuthErrorResult(authResult, Bundle.message(Resource.ERROR_AUTHENTICATION_TIME_OUT));
        } catch (CxException cxException) {
            LOGGER.warn(String.format("OAuth: Custom exception thrown. Root Cause:%s ", cxException.getMessage()));
            setAuthErrorResult(authResult, cxException.getMessage());
        } catch (Exception exception) {
            LOGGER.warn(String.format("OAuth: Exception occurred during authentication process. Root Cause:%s ", exception.getMessage()));
            setAuthErrorResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
        } finally {
            LOGGER.warn("OAuth: Finally stopping callback server.");
            server.stop();
        }
    }

    /**
     * Setting an authentication error result for consumer
     *
     * @param authResult  - Consumer<Map<String,String>> object which will used by UI to get the auth result
     * @param resultValue - String value to set in consumer
     */
    protected void setAuthErrorResult(Consumer<Map<String, Object>> authResult, String resultValue) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.AuthConstants.ERROR, resultValue);
        ApplicationManager.getApplication().invokeLater(() -> authResult.accept(resultMap));
    }

    /**
     * Setting an authentication success result for consumer
     *
     * @param authResult - Consumer<Map<String,String>> object which will used by UI to get the auth result
     * @param resultMap  - Map<String, String> object with success token details
     */
    protected void setAuthSuccessResult(Consumer<Map<String, Object>> authResult, Map<String, Object> resultMap) {
        ApplicationManager.getApplication().invokeLater(() -> authResult.accept(resultMap));
    }

    /**
     * Building CxOne authorization endpoint using base url and tenant.
     *
     * @param baseUrl - CxOne base URL
     * @param tenant  - CxOne tenant
     * @return authorization url
     */
    protected String getCxOneAuthEndpoint(final String baseUrl, final String tenant) {
        return baseUrl + "/auth/realms/" + tenant + "/protocol/openid-connect/auth";
    }

    /**
     * Building CxOne token endpoint using base url and tenant.
     *
     * @param baseUrl - CxOne base URL
     * @param tenant  - CxOne tenant
     * @return authorization url
     */
    protected String getCxOneTokenEndpoint(final String baseUrl, final String tenant) {
        return baseUrl + "/auth/realms/" + tenant + "/protocol/openid-connect/token";
    }

    /**
     * Building Checkmarx One OAuth authorization URL with all required parameters as per a standard
     *
     * @param authEndpoint  - CxOne authorization endpoint
     * @param redirectUri   - local server redirects url to listen to response from oauth server
     * @param codeChallenge - Generated code challenge using SHA-256 for PKCE
     * @return CxOne OAuth authorization URL with all required parameters
     */
    protected String buildCxOneOAuthAuthorizationUrl(String authEndpoint, String redirectUri,
                                                     String codeChallenge) throws URISyntaxException {
        String state = UUID.randomUUID().toString();
        URIBuilder uriBuilder = new URIBuilder(authEndpoint);
        uriBuilder.addParameter("response_type", Constants.AuthConstants.CODE);
        uriBuilder.addParameter("client_id", Constants.AuthConstants.OAUTH_IDE_CLIENT_ID);
        uriBuilder.addParameter("redirect_uri", redirectUri);
        uriBuilder.addParameter("scope", Constants.AuthConstants.SCOPE);
        uriBuilder.addParameter("state", state);
        uriBuilder.addParameter("code_challenge", codeChallenge);
        uriBuilder.addParameter("code_challenge_method", Constants.AuthConstants.CODE_CHALLENGE_METHOD);

        server.setState(state);

        URI authUri = uriBuilder.build();
        return authUri.toString();
    }

    /**
     * This method will get the random port within dynamic/private port range using secure random,
     * and check whether the port is available or not.
     *
     * @return an available port number if found else 0
     */
    protected int findAvailablePort() {
        SecureRandom random = new SecureRandom();
        int range = MAX_PORT - MIN_PORT + 1;
        for (int attempt = 1; attempt <= MAX_PORT_ATTEMPTS; attempt++) {
            try {
                int port = MIN_PORT + random.nextInt(range);
                if (isPortAvailable(port)) {
                    return port;
                }
            } catch (Exception exception) {
                LOGGER.warn(String.format("OAuth: Exception occurred while finding available port on attempt:%d. Root Cause:%s", attempt, exception.getMessage()));
            }
        }
        LOGGER.warn("OAuth: No available port found in the dynamic/private range.");
        return 0;
    }

    /**
     * Checks whether a specific port is available.
     *
     * @param port the port to check
     * @return true if the port is available
     */
    protected boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            LOGGER.warn(String.format("OAuth: Port Number:%d is not available, already in use.", port));
            return false; // Port is already in use
        }
    }

    /**
     * Opening the default browser from the user system with specified URL
     *
     * @param url - URL to open in browser
     */
    protected void openDefaultBrowser(final String url) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                BrowserUtil.browse(new URI(url)); // Launch browser for authentication.
            } catch (Exception exception) {
                LOGGER.warn(String.format("OAuth: Exception occurred while opening the browser. Root Cause:%s", exception.getMessage()));
            }
        });
    }

    /**
     * Exchange Authorization Code for Tokens,
     * This method will be used to call the token endpoint to get the OAuth token
     * by using the auth code received from the authorization endpoint.
     * <p>
     * If any error occurs while getting token, it will retry for a specified max retry attempt
     *
     * @param code         - auth code received from authorization
     * @param codeVerifier - original code verifier for PKCE
     * @param redirectUri  - redirect uri, which was used for authorization code
     * @return string refresh token
     */
    protected Map<String, Object> exchangeCodeForToken(String tokenEndpoint, String code, String codeVerifier, String redirectUri) {
        try {
            return Utils.executeWithRetry(() -> {
                try {
                    return callTokenEndpoint(tokenEndpoint, code, codeVerifier, redirectUri);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e); // wrap checked in unchecked to satisfy Supplier
                }
            }, MAX_RETRIES, RETRY_DELAY_MS);
        } catch (Exception exception) {
            LOGGER.warn(String.format("OAuth: Failed to get OAuth token after retries. Root Cause:%s ",
                    exception.getMessage()));
            return Collections.emptyMap();
        }
    }


    /**
     * This method will be used to call the token endpoint to get the OAuth token
     * by using the auth code received from the authorization endpoint.
     *
     * @param code         - auth code received from authorization
     * @param codeVerifier - original code verifier for PKCE
     * @param redirectUri  - redirect uri, which was used for authorization code
     * @return string refresh token
     * @throws IOException - if error occurred during exchanging auth code for token
     */
    public Map<String, Object> callTokenEndpoint(String tokenEndpoint, String code, String codeVerifier, String redirectUri) throws IOException, InterruptedException {
        int redirectAttempt = 1;

        String requestBody = "grant_type=authorization_code" +
                "&code=" + code +
                "&redirect_uri=" + redirectUri +
                "&client_id=" + Constants.AuthConstants.OAUTH_IDE_CLIENT_ID +
                "&code_verifier=" + codeVerifier;
        HttpClient client = HttpClientUtils.createHttpClient(tokenEndpoint);
        do {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response != null) {
                LOGGER.info(String.format("OAuth: Received response statusCode:%s", response.statusCode()));
                if (response.statusCode() == 200) {
                    return extractRefreshTokenDetails(response.body());
                } else if (response.statusCode() > 300 && response.statusCode() < 400) {
                    LOGGER.warn(String.format("OAuth: Received permanent redirect, getting new token endpoint from header location. Attempt:%d", redirectAttempt));
                    /*
                     * If user provided only base url then, CxOne send permanent redirect response.
                     * As token base url should be iam, we need to get new token endpoint from the
                     * received response header and use this new endpoint to get the token.
                     */
                    if (response.headers() != null && (response.headers().map() != null && !response.headers().map().isEmpty())
                            && response.headers().map().containsKey(Constants.AuthConstants.LOCATION)) {
                        tokenEndpoint = response.headers().map().get(Constants.AuthConstants.LOCATION).get(0);
                    }
                }
            }
            redirectAttempt++;
        } while (redirectAttempt <= MAX_RETRIES);
        return null;
    }

    /**
     * Parse JSON response string into JSON object and getting refresh token
     *
     * @param jsonString - token response body as json string
     * @return String - refresh token
     */
    protected Map<String, Object> extractRefreshTokenDetails(String jsonString) {
        Map<String, Object> tokenDetails = new HashMap<>();
        try {
            JsonNode rootNode = new ObjectMapper().readTree(jsonString);
            if (rootNode.has(Constants.AuthConstants.REFRESH_TOKEN)) {
                tokenDetails.put(Constants.AuthConstants.REFRESH_TOKEN, rootNode.get(Constants.AuthConstants.REFRESH_TOKEN).asText());
                String expirySeconds = rootNode.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY).asText();
                tokenDetails.put(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY, getRefreshTokenExpiry(Long.parseLong(expirySeconds)));
            }
        } catch (JsonProcessingException exception) {
            LOGGER.warn(String.format("OAuth: Unable to extract refresh token using from the response using JsonNode. Error:%s",
                    exception.getMessage()));
        }
        return tokenDetails;
    }

    /**
     * Adding duration in seconds to the current date
     *
     * @param seconds - seconds to be added in current date time
     * @return updated LocalDateTime
     */
    private String getRefreshTokenExpiry(Long seconds) {
        return Utils.convertToLocalDateTime(seconds, ZoneId.systemDefault()).toString();
    }

    /**
     * Store refresh token in secure storage
     *
     * @param refreshToken - Received refresh token from the response
     */
    protected void saveToken(final String refreshToken) {
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();
        sensitiveState.setRefreshToken(refreshToken);
        sensitiveState.saveRefreshToken(refreshToken);
    }
}
