package com.checkmarx.intellij.service;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.helper.OAuthCallbackServer;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * AuthService class responsible to handle the authentication and authorization mechanism
 * using OAuth PKCE flow
 */
@Slf4j
public class AuthService {

    private static final int MIN_PORT = 49152;
    private static final int MAX_PORT = 65535;
    private static final int MAX_PORT_ATTEMPTS = 10;
    private static final int RETRY_DELAY_MS = 1000;
    private static final int MAX_RETRIES = 3;
    private static final int TIME_OUT_SECONDS = 120;
    private static final String CALLBACK_PATH = "/checkmarx1/callback";
    private final Project project = ProjectManager.getInstance().getDefaultProject();
    private OAuthCallbackServer server;

    /**
     * Authenticate user using OAuth2.0 Authorization Code with PKCE (Proof Key for Code Exchange) grant flow
     *
     * @param cxOneBaseUrl - Checkmarx One base URL provided by the user.
     * @param cxOneTenant  - Checkmarx One-tenant URL provided by the user.
     */
    public void authenticate(final String cxOneBaseUrl, final String cxOneTenant, Consumer<String> authResult) {
        log.info("OAuth: Authentication started with provided base URL:{} and Tenant:{}", cxOneBaseUrl, cxOneTenant);
        try {
            String codeVerifier = Utils.generateCodeVerifier();
            String codeChallenge = Utils.generateCodeChallenge(codeVerifier);

            if (codeVerifier == null || codeChallenge == null) {
                log.error("OAuth: Code Verifier or Code Challenge is null.");
                setAuthResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
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
            log.error("OAuth: Exception occurred while authenticating using oauth2. Root Cause:{}",
                    exception.getMessage());
            setAuthResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
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
    private void processAuthentication(String codeVerifier, String codeChallenge, String cxOneBaseUrl,
                                       String cxOneTenant, Consumer<String> authResult) {
        try {
            String cxOneAuthEndpoint = getCxOneAuthEndpoint(cxOneBaseUrl, cxOneTenant);
            String cxOneTokenEndpoint = getCxOneTokenEndpoint(cxOneBaseUrl, cxOneTenant);
            int port = findAvailablePort();
            if (port == 0) {
                setAuthResult(authResult, Bundle.message(Resource.ERROR_PORT_NOT_AVAILABLE));
                return;
            }
            String redirectUrl = "http://localhost:" + port + CALLBACK_PATH;
            String authorizationUrl = buildCxOneOAuthAuthorizationUrl(cxOneAuthEndpoint, redirectUrl, codeChallenge);

            log.debug("OAuth: OAuth2.0 Authorization URL:{}", authorizationUrl);
            server.start(TIME_OUT_SECONDS, port);

            openDefaultBrowser(authorizationUrl);

            String authCode = server.waitForAuthCode().get(TIME_OUT_SECONDS, TimeUnit.SECONDS);
            String refreshToken = exchangeCodeForToken(cxOneTokenEndpoint, authCode, codeVerifier, redirectUrl);
            if (refreshToken == null || refreshToken.isEmpty()) {
                log.error("OAuth: Not able to get refresh token. Refresh token is null.");
                setAuthResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
                return;
            }
            saveToken(refreshToken); // Save refresh token in storage
            setAuthResult(authResult, Constants.AuthConstants.TOKEN + ":" + refreshToken); //Return token
            log.info("OAuth: Authentication process completed successfully.");
        } catch (ExecutionException | TimeoutException timeoutException) {
            log.error("OAuth: Time out exception occurred. Auth code not received within authentication timeout");
            setAuthResult(authResult, Bundle.message(Resource.ERROR_AUTHENTICATION_TIME_OUT));
        } catch (CxException cxException) {
            log.error("OAuth: Custom exception thrown. Root Cause:{} ", cxException.getMessage());
            setAuthResult(authResult, cxException.getMessage());
        } catch (Exception exception) {
            log.error("OAuth: Exception occurred during authentication process. Root Cause:{} ", exception.getMessage());
            setAuthResult(authResult, Bundle.message(Resource.VALIDATE_ERROR));
        } finally {
            log.debug("OAuth: Finally stopping callback server.");
            server.stop();
        }
    }

    /**
     * Setting an authentication result for consumer
     *
     * @param authResult - Consumer<String> object which will used by UI to get the auth result
     * @param value      - String value to set in consumer
     */
    private void setAuthResult(Consumer<String> authResult, String value) {
        ApplicationManager.getApplication().invokeLater(() -> authResult.accept(value));
    }


    /**
     * Building CxOne authorization endpoint using base url and tenant.
     *
     * @param baseUrl - CxOne base URL
     * @param tenant  - CxOne tenant
     * @return authorization url
     */
    private String getCxOneAuthEndpoint(final String baseUrl, final String tenant) {
        return baseUrl + "/auth/realms/" + tenant + "/protocol/openid-connect/auth";
    }

    /**
     * Building CxOne token endpoint using base url and tenant.
     *
     * @param baseUrl - CxOne base URL
     * @param tenant  - CxOne tenant
     * @return authorization url
     */
    private String getCxOneTokenEndpoint(final String baseUrl, final String tenant) {
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
    private String buildCxOneOAuthAuthorizationUrl(String authEndpoint, String redirectUri,
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
    private int findAvailablePort() {
        SecureRandom random = new SecureRandom();
        int range = MAX_PORT - MIN_PORT + 1;
        for (int attempt = 1; attempt <= MAX_PORT_ATTEMPTS; attempt++) {
            try {
                int port = MIN_PORT + random.nextInt(range);
                if (isPortAvailable(port)) {
                    log.info("OAuth: Found available port:{}, on attempt:{}", port, attempt);
                    return port;
                }
            } catch (Exception exception) {
                log.error("OAuth: Exception occurred while finding available port on attempt:{}. Root Cause:{}", attempt, exception.getMessage());
            }
        }
        log.error("OAuth: No available port found in the dynamic/private range: {} to {}", MIN_PORT, MAX_PORT);
        return 0;
    }

    /**
     * Checks whether a specific port is available.
     *
     * @param port the port to check
     * @return true if the port is available
     */
    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            log.debug("OAuth: Port Number:{} is not available, already in use.", port);
            return false; // Port is already in use
        }
    }

    /**
     * Opening the default browser from the user system with specified URL
     *
     * @param url - URL to open in browser
     */
    private void openDefaultBrowser(final String url) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                BrowserUtil.browse(new URI(url)); // Launch browser for authentication.
            } catch (Exception exception) {
                log.error("OAuth: Exception occurred while opening the browser. Root Cause:{}", exception.getMessage());
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
    public String exchangeCodeForToken(String tokenEndpoint, String code, String codeVerifier, String redirectUri) throws CxException {
        try {
            return Utils.executeWithRetry(() -> {
                try {
                    return callTokenEndpoint(tokenEndpoint, code, codeVerifier, redirectUri);
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e); // wrap checked in unchecked to satisfy Supplier
                }
            }, MAX_RETRIES, RETRY_DELAY_MS);
        } catch (Exception exception) {
            log.error("OAuth: Failed to get OAuth token after retries. Root Cause:{} ", exception.getMessage());
            throw new CxException(500, "Something went wrong, Please try again.");
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
    public String callTokenEndpoint(String tokenEndpoint, String code, String codeVerifier, String redirectUri) throws  IOException, InterruptedException {
        int redirectAttempt = 1;

        String requestBody = "grant_type=authorization_code" +
                "&code=" + code +
                "&redirect_uri=" + redirectUri +
                "&client_id=" + Constants.AuthConstants.OAUTH_IDE_CLIENT_ID +
                "&code_verifier=" + codeVerifier;
        do {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("OAuth: Received token response:{}", response);
            if (response != null) {
                log.info("OAuth: Token response status code:{}", response.statusCode());
                if (response.statusCode() == 200) {
                    return extractRefreshToken(response.body());
                } else if (response.statusCode() == 308) {
                    log.info("OAuth: Received permanent redirect, getting new token endpoint from header location. Attempt:{}", redirectAttempt);
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
    private String extractRefreshToken(String jsonString) {
        try {
            JsonNode rootNode = new ObjectMapper().readTree(jsonString);
            if (rootNode.has(Constants.AuthConstants.REFRESH_TOKEN))
               return rootNode.get(Constants.AuthConstants.REFRESH_TOKEN).asText();
        } catch (JsonProcessingException exception) {
            log.warn("OAuth: Unable to extract refresh token using from the response using JsonNode. Error:{}", exception.getMessage());
            return extractRefreshTokenUsingStream(jsonString);
        }
        log.warn("OAuth: Refresh token not found in the response.");
        return null;
    }

    /**
     * Extracting refresh token from received json response from the token endpoint.
     *
     * @param tokenResponse - token response body
     * @return refresh token
     */
    private String extractRefreshTokenUsingStream(String tokenResponse) {
        try {
            log.warn("OAuth: Retrying to extract token from the response..");
            return Arrays.stream(tokenResponse.split(",\\s*"))
                    .map(String::trim)
                    .filter(s -> s.startsWith("\"refresh_token\"")
                            || s.startsWith(Constants.AuthConstants.REFRESH_TOKEN))
                    .map(s -> {
                        int colonIndex = s.indexOf(':');
                        if (colonIndex == -1) return null;

                        String token = s.substring(colonIndex + 1).trim();
                        if (token.startsWith("\"") && token.endsWith("\"")) {
                            token = token.substring(1, token.length() - 1);
                        }
                        return token;
                    }).filter(token -> token != null && !token.isEmpty()).findFirst().orElse(null);
        } catch (Exception exception) {
            log.error("OAuth: Exception occurred while extracting refresh token using java. Root Cause:{}", exception.getMessage());
            return null;
        }
    }

    /**
     * Store refresh token in secure storage
     *
     * @param refreshToken - Received refresh token from the response
     */
    private void saveToken(final String refreshToken) {
        log.info("OAuth: Saving token in secure storage");
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();
        sensitiveState.setRefreshToken(refreshToken);
        sensitiveState.saveRefreshToken(refreshToken);
        log.info("OAuth: Token saved successfully.");
    }
}
