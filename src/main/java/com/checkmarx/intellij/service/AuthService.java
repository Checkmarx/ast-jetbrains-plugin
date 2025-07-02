package com.checkmarx.intellij.service;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.helper.OAuthCallbackServer;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

            String authURL = buildCxOneOAuthAuthorizationUrl(cxOneAuthEndpoint, redirectUrl, codeChallenge);
            log.debug("OAuth: OAuth2.0 Authorization URL:{}", authURL);

            boolean confirmed = openConfirmation(cxOneAuthEndpoint);
            if (!confirmed) {
                log.warn("OAuth: Authentication is cancelled by user.");
                Utils.showAuthNotification(Constants.AuthConstants.AUTH_FAILED_TITLE, "Authentication is cancelled.",
                        NotificationType.WARNING, project);
                return false;
            }
            String tokenEndpoint = getCxOneTokenEndpoint(cxOneBaseUrl, cxOneTenant);
            log.debug("OAuth: OAuth2.0 Token URL:{}", authURL);
            return processAuthentication(port, codeVerifier, authURL, tokenEndpoint, redirectUrl);
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
     * @param port - Port number for callback server (Http Server)
     * @param codeVerifier - Original code verifier for PKCE
     * @param authURL - CxOne Authorization (OAuth) endpoint with required parameters
     * @param tokenEndpoint - CxOne Token endpoint with required parameters
     * @param redirectUrl - Callback URL where auth code will be received
     * @return true if auth success otherwise false
     */
    private boolean processAuthentication(int port, String codeVerifier, String authURL, String tokenEndpoint, String redirectUrl) {
        final boolean[] isAuthenticated = {false};
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Authenticating with checkmarx one...", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText("Waiting for authentication in browser...");
                try {
                    server = new OAuthCallbackServer(Constants.AuthConstants.CALLBACK_PATH);
                    server.start(Constants.AuthConstants.TIME_OUT_SECONDS, port);
                    // Launch in browser
                    java.awt.Desktop.getDesktop().browse(new URI(authURL));
                    CompletableFuture<String> future = server.waitForAuthCode();
                    String code = future.get(Constants.AuthConstants.TIME_OUT_SECONDS, TimeUnit.SECONDS);
                    exchangeCodeForToken(tokenEndpoint, code, codeVerifier, redirectUrl);
                    log.info("OAuth: Authentication process completed successfully.");
                    showSuccessMessage();
                    isAuthenticated[0] = true;
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
        return isAuthenticated[0];
    }

    /**
     * Open the confirmation dialog for the user before redirect to browser for authentication.
     * @param authUrl - CxOne auth endpoint without required parameters
     * @return true if user agrees otherwise false
     */
    private boolean openConfirmation(String authUrl) {
        return Messages.showYesNoDialog(
                "Checkmarx plugin wants to open your default browser for login. \n \n" + authUrl,
                "OAuth Authentication Required",
                "Open",   // Yes button text
                "Cancel",    // No button text
                Messages.getQuestionIcon()
        ) == Messages.YES;
    }

    /**
     * Display notification on notification area on successful authentication
     */
    public void showSuccessMessage() {
        Utils.showAuthNotification("Authentication Success.",
                "You have successfully connected to the Checkmarx One server.",
                NotificationType.INFORMATION,
                project);
    }

    /**
     * Display notification on notification area on failure authentication
     */
    public void showErrorMessage(String errorMsg) {
        Utils.showAuthNotification("Authentication Failed.",
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
        URIBuilder uriBuilder = new URIBuilder(authEndpoint);
        uriBuilder.addParameter("response_type", Constants.AuthConstants.RESP_TYPE_CODE);
        uriBuilder.addParameter("client_id", Constants.AuthConstants.IDE_CLIENT_ID);
        uriBuilder.addParameter("redirect_uri", encodeUrl(redirectUri));
        uriBuilder.addParameter("scope", Constants.AuthConstants.SCOPE);
        uriBuilder.addParameter("state", UUID.randomUUID().toString());
        uriBuilder.addParameter("code_challenge", codeChallenge);
        uriBuilder.addParameter("code_challenge_method", Constants.AuthConstants.CODE_CHALLENGE_METHOD);

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
     * Exchange Authorization Code for Tokens
     *
     * @param code
     * @param codeVerifier
     * @param redirectUri
     * @throws IOException
     */

    public void exchangeCodeForToken(String tokenEndpoint, String code, String codeVerifier, String redirectUri) throws IOException, MalformedURLException, ProtocolException {
        String data = "grant_type=authorization_code" +
                "&code=" + code +
                "&redirect_uri=" + redirectUri +
                "&client_id=" + Constants.AuthConstants.IDE_CLIENT_ID +
                "&code_verifier=" + codeVerifier;

        URL url = new URL(tokenEndpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        InputStream is = conn.getResponseCode() == 200 ? conn.getInputStream() : conn.getErrorStream();
        String response = new BufferedReader(new InputStreamReader(is))
                .lines().reduce("", (acc, line) -> acc + line + "\n");
        saveToken(getRefreshToken(response));
    }

    // Parse and store access/refresh token securely
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

    private void saveToken(final String refreshToken) {
        GlobalSettingsSensitiveState sensitiveState = GlobalSettingsSensitiveState.getInstance();
        sensitiveState.setApiKey(refreshToken);
    }
}
