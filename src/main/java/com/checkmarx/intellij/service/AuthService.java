package com.checkmarx.intellij.service;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.helper.OAuthCallbackServer;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AuthService class responsible to handle the authentication and authorization mechanism
 * Handle OAuth-based authentication
 */
@Slf4j
public class AuthService {

    private static final int MIN_PORT = 49152;
    private static final int MAX_PORT = 65535;
    private static final int MAX_RETRIES = 3;
    private final Project project = ProjectManager.getInstance().getDefaultProject();
    private OAuthCallbackServer server;

    /**
     * Authenticate user using OAuth2.0 Authorization Code with PKCE (Proof Key for Code Exchange) grant flow
     *
     * @param cxOneBaseUrl - Checkmarx One base URL provided by the user.
     * @param cxOneTenant  - Checkmarx One tenant URL provided by the user.
     * @return boolean value tells whether authentication success or not
     */
    public boolean authenticate(final String cxOneBaseUrl, final String cxOneTenant) {
        try {
            log.info("OAuth: Authentication started with provided base URL:{} and Tenant:{}", cxOneBaseUrl, cxOneTenant);
            String codeVerifier = Utils.generateCodeVerifier();
            String codeChallenge = Utils.generateCodeChallenge(codeVerifier);

            if (codeVerifier == null || codeChallenge == null) {
                log.error("OAuth: Code Verifier or Code Challenge is null.");
                return false;
            }
            int port = findAvailablePort();
            String redirectUrl = "http://localhost:" + port + Constants.AuthConstants.CALLBACK_PATH;
            String cxOneAuthEndpoint = getCxOneAuthEndpoint(cxOneBaseUrl, cxOneTenant);
            String cxOneTokenEndpoint = getCxOneTokenEndpoint(cxOneBaseUrl, cxOneTenant);
            log.debug("OAuth: CxOne Auth Endpoint:{} and CxOne Token Endpoint:{}", cxOneAuthEndpoint, cxOneTokenEndpoint);

            boolean confirmed = openConfirmation(cxOneAuthEndpoint);
            if (!confirmed) {
                log.warn("OAuth: Authentication is cancelled by user.");
                Utils.showNotification(Constants.AuthConstants.AUTH_FAILED_TITLE, "Authentication is cancelled.",
                        NotificationType.WARNING, project);
                return false;
            }
            server = new OAuthCallbackServer(Constants.AuthConstants.CALLBACK_PATH); // initialize the server object
            String authURL = buildCxOneOAuthAuthorizationUrl(cxOneAuthEndpoint, redirectUrl, codeChallenge);
            log.debug("OAuth: OAuth2.0 Authorization URL:{}", authURL);
            return processAuthentication(port, codeVerifier, authURL, cxOneTokenEndpoint, redirectUrl);
        } catch (Exception exception) {
            log.error("OAuth: Exception occurred while authenticating using oauth2. Root Cause:{}",
                    exception.getMessage());
            return false;
        }
    }

    /**
     * Start processing the authentication using OAuth flow.
     * This method will be run progress indicator in the background and start the callback server.
     * Once authentication is completed or failed, progress indicator will be closed
     * and success or error notification will be shown to the user.
     *
     * @param port          - Port number for callback server (Http Server)
     * @param codeVerifier  - Original code verifier for PKCE
     * @param authURL       - CxOne Authorization (OAuth) endpoint with required parameters
     * @param tokenEndpoint - CxOne Token endpoint with required parameters
     * @param redirectUrl   - Callback URL where auth code will be received
     * @return true if auth success otherwise false
     */
    private boolean processAuthentication(int port, String codeVerifier, String authURL, String tokenEndpoint, String redirectUrl) {
        AtomicBoolean isAuthenticated = new AtomicBoolean(false);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Authenticating with checkmarx one...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Waiting for authentication in browser...");
                try {
                    server.start(Constants.AuthConstants.TIME_OUT_SECONDS, port);
                    // Launch in browser
                    java.awt.Desktop.getDesktop().browse(new URI(authURL));
                    CompletableFuture<String> future = server.waitForAuthCode();
                    String code = future.get(Constants.AuthConstants.TIME_OUT_SECONDS, TimeUnit.SECONDS);
                    exchangeCodeForToken(tokenEndpoint, code, codeVerifier, redirectUrl);
                    log.info("OAuth: Authentication process completed successfully.");
                    showSuccessMessage();
                    isAuthenticated.set(true);
                } catch (TimeoutException timeoutException) {
                    showErrorMessage("Authentication timed out, Please try again.");
                } catch (Exception exception) {
                    log.error("OAuth: Exception occurred while authentication process. Root Cause:{} ", exception.getMessage());
                    showErrorMessage("Authentication Failed: " + exception.getMessage());
                } finally {
                    server.stop();
                }
            }
        });
        log.info("OAuth: Authentication process success status:{}.", isAuthenticated.get());
        return isAuthenticated.get();
    }

    /**
     * Open the confirmation dialog for the user before redirect to browser for authentication.
     *
     * @param authUrl - CxOne auth endpoint without required parameters
     * @return true if user agrees otherwise false
     */
    private boolean openConfirmation(String authUrl) {
        String message = "Checkmarx plugin wants to open your default browser for login. \n \n" + authUrl;
        return Utils.openConfirmation(message,
                "OAuth Authentication Required",
                "Continue",
                "Cancel");
    }

    /**
     * Display notification on notification area on successful authentication
     */
    public void showSuccessMessage() {
        Utils.showNotification("Authentication Success.",
                Bundle.message(Resource.SUCCESS_CONNECTED_TO_CHECKMARX),
                NotificationType.INFORMATION,
                project);
    }

    /**
     * Display notification on notification area on failure authentication
     */
    public void showErrorMessage(String errorMsg) {
        Utils.showNotification("Authentication Failed.",
                errorMsg,
                NotificationType.ERROR,
                project);
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
        uriBuilder.addParameter("response_type", Constants.AuthConstants.RESP_TYPE_CODE);
        uriBuilder.addParameter("client_id", Constants.AuthConstants.IDE_CLIENT_ID);
        uriBuilder.addParameter("redirect_uri", encodeUrl(redirectUri));
        uriBuilder.addParameter("scope", Constants.AuthConstants.SCOPE);
        uriBuilder.addParameter("state", state);
        uriBuilder.addParameter("code_challenge", codeChallenge);
        uriBuilder.addParameter("code_challenge_method", Constants.AuthConstants.CODE_CHALLENGE_METHOD);

        server.setState(state);

        URI authUri = uriBuilder.build();
        return authUri.toString();
    }

    /**
     * Encoding url with url encoder
     *
     * @param url - original url
     * @return encoded url
     */
    private String encodeUrl(String url) {
        return URLEncoder.encode(url, StandardCharsets.UTF_8);
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
     * Finds the first available port in the dynamic/private port range.
     * This method will find the available random port within a range
     *
     * @return an available port number
     * @throws IOException if no available port is found
     */
    public int findAvailablePort() throws IOException {
        SecureRandom random = new SecureRandom();
        int range = MAX_PORT - MIN_PORT + 1;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            log.debug("OAuth: Attempt:{} to find an available port.", attempt);
            Set<Integer> triedPorts = new HashSet<>();
            while (triedPorts.size() < range) {
                int port = MIN_PORT + random.nextInt(range);

                if (triedPorts.contains(port)) continue;
                triedPorts.add(port);

                if (isPortAvailable(port)) {
                    log.info("OAuth: Found available port:{} on attempt:{} ", port, attempt);
                    return port;
                }
            }
            log.warn("OAuth: No available port found in attempt:{} Retrying after delay..", attempt);
            try {
                Thread.sleep(Constants.AuthConstants.RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                log.debug("OAuth: Exception occurred while delaying after attempt:{}", attempt);
            }
        }
        log.error("OAuth: No available port found in the dynamic/private range: {} to {}", MIN_PORT, MAX_PORT);
        throw new IOException("A required port in the range " + MIN_PORT + "–" + MAX_PORT + " is currently in use. Please try again shortly.");
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
     * Exchange Authorization Code for Tokens,
     * This method will be used to call the token endpoint to get the OAuth token
     * by using the auth code received from the authorization endpoint.
     *
     * @param code         - auth code received from authorization
     * @param codeVerifier - original code verifier for PKCE
     * @param redirectUri  - redirect uri, which was used for authorization code
     * @throws CxException - if error occurred during exchanging auth code for token
     */
    public void exchangeCodeForToken(String tokenEndpoint, String code, String codeVerifier, String redirectUri) throws CxException {
        try {
            String requestBody = "grant_type=authorization_code" +
                    "&code=" + code +
                    "&redirect_uri=" + redirectUri +
                    "&client_id=" + Constants.AuthConstants.IDE_CLIENT_ID +
                    "&code_verifier=" + codeVerifier;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenEndpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("OAuth: Received token response:{}", response);
            log.info("OAuth: Token response status code:{}", response.statusCode());

            if (response.statusCode() == 200) {
                saveToken(getRefreshToken(response.body()));
                return;
            }
            throw new CxException(500, "Something went wrong, Please try again.");
        } catch (IOException | InterruptedException e) {
            throw new CxException(500, "Unable to connect with CxOne platform for authentication ");
        } catch (Exception exception) {
            throw new CxException(500, exception.getMessage());
        }
    }

    /**
     * Parse json response string into JSON object and getting refresh token
     *
     * @param jsonString - token response body as json string
     * @return String - refresh token
     */
    private String getRefreshToken(String jsonString) {
        try {
            JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
            JSONObject jsonObject = (JSONObject) parser.parse(jsonString);
            return jsonObject.getAsString("refresh_token");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Store refresh token in secure storage
     * @param refreshToken - Received refresh token from the response
     */
    private void saveToken(final String refreshToken) {
        log.debug("OAuth: Saving token in secure storage");
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();
        sensitiveState.setApiKey(refreshToken);
        sensitiveState.apply(sensitiveState);
    }
}
