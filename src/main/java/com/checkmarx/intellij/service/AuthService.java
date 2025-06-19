package com.checkmarx.intellij.service;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.utils.URIBuilder;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * AuthService class responsible to handle the authentication mechanism
 * Handle OAuth-based authentication
 */
@Slf4j
public class AuthService {

    // Dynamic/private port range (IANA range)
    private static final int MIN_PORT = 49152;
    private static final int MAX_PORT = 65535;

    @Nullable
    HttpServer server;

    /**
     * Authenticate user using OAuth2.0 Authorization Code with PKCE (Proof Key for Code Exchange) grant flow
     *
     * @param cxOneBaseUrl - Checkmarx One base URL provided by the user.
     */
    private void authenticate(final String cxOneBaseUrl, final String tenant) {
        try {
            String codeVerifier = Utils.generateCodeVerifier();
            String codeChallenge = Utils.generateCodeChallenge(codeVerifier);

            int port = findAvailablePort();
            String callbackUrl = "http://localhost:" + port + "/checkmarx1/callback";

            String cxOneAuthEndpoint = buildCxOneAuthEndpoint(cxOneBaseUrl, tenant);

            String authURL = buildAuthorizationUrl(cxOneAuthEndpoint, callbackUrl, codeChallenge);
            log.info("Authentication: OAuth2.0 Authorization URL:{}", authURL);

            startLocalCallbackServer(codeVerifier, callbackUrl);

            // Launch in browser
            java.awt.Desktop.getDesktop().browse(new URI(authURL));
        } catch (Exception exception) {
            log.error("Authentication: Exception occurred while authenticating using oauth2. Root Cause:{}",
                    exception.getMessage());
        }

    }

    private String buildAuthorizationUrl(String baseUrl, String redirectUri,
                                         String codeChallenge) throws URISyntaxException {

        URIBuilder uriBuilder = new URIBuilder(baseUrl);
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

    private String buildCxOneAuthEndpoint(final String baseUrl, final String tenant) {
        return baseUrl + "/auth/realms/" + tenant + "/protocol/openid-connect/auth";
    }

    private String buildCxOneTokenEndpoint(final String baseUrl, final String tenant) {
        return baseUrl + "/auth/realms/" + tenant + "/protocol/openid-connect/token";
    }

    /**
     * lightweight HTTP server (e.g., on port 8080) inside your plugin to catch the redirect.
     */
    private void startLocalCallbackServer(String codeVerifier, String redirectUri) {
        try {
            int port = findAvailablePort();
            String callbackUrl = "http://localhost:" + port + "/checkmarx1/callback";
            log.info("Starting local auth server on: {}", callbackUrl);
            server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/checkmarx1/callback", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String code = Arrays.stream(query.split("&"))
                        .filter(p -> p.startsWith("code="))
                        .map(p -> p.split("=")[1])
                        .findFirst().orElse(null);
                String response = "Authorization successful. You can close this window.";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                // Proceed to token exchange
                exchangeAuthorizationCodeForTokens(code, codeVerifier, redirectUri);
            });
            server.start();
        } catch (IOException ioException) {
            // Handle port allocation failure
            log.error("Failed to start local server: {}", ioException.getMessage());
        }
    }

    public CompletableFuture<Void> closeServerAsync() {
        if (server != null) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            // Assume server has an async close() method or simulate with a thread
            new Thread(() -> {
                try {
                    server.stop(1); // blocking call
                    server = null;
                    future.complete(null);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            }).start();
            return future;
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Exchange Authorization Code for Tokens
     *
     * @param code
     * @param codeVerifier
     * @param redirectUri
     * @throws IOException
     */

    public void exchangeAuthorizationCodeForTokens(String code, String codeVerifier, String redirectUri) throws IOException {
        URL url = new URL("https://your-auth-server.com/oauth/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = "grant_type=authorization_code"
                + "&code=" + URLEncoder.encode(code, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8")
                + "&client_id=" + URLEncoder.encode(Constants.AuthConstants.IDE_CLIENT_ID, "UTF-8")
                + "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8");

        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes());
        os.flush();
        os.close();

        // Read response
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();

        // Parse and store token
        System.out.println("Tokens: " + content.toString());
    }


    /**
     * Finds the first available port in the dynamic/private port range.
     *
     * @return an available port number
     * @throws IOException if no available port is found
     */
    public static int findAvailablePort() throws IOException {
        for (int port = MIN_PORT; port <= MAX_PORT; port++) {
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new IOException("No available port found in the dynamic/private range (49152–65535)");
    }

    /**
     * Checks whether a specific port is available.
     *
     * @param port the port to check
     * @return true if the port is available
     */
    private static boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false; // Port is already in use
        }
    }

     class OAuthCallbackServer {

        private HttpServer server;
        private final int port;
        private final String path;
        private final Consumer<String> onCodeReceived;

        /**
         * @param port The port to listen on (e.g. 8080)
         * @param path The callback path (e.g. "/callback")
         * @param onCodeReceived Callback handler when the "code" query param is received
         */
        public OAuthCallbackServer(int port, String path, Consumer<String> onCodeReceived) {
            this.port = port;
            this.path = path;
            this.onCodeReceived = onCodeReceived;
        }

        public void start() throws IOException {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(path, new CallbackHandler());
            server.setExecutor(null); // default executor
            server.start();
            System.out.println("OAuthCallbackServer started at http://localhost:" + port + path);
        }

        public void stop() {
            if (server != null) {
                server.stop(0);
                System.out.println("OAuthCallbackServer stopped.");
            }
        }

        private class CallbackHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String query = exchange.getRequestURI().getQuery();
                String code = Arrays.stream(query.split("&"))
                        .filter(p -> p.startsWith("code="))
                        .map(p -> p.split("=")[1])
                        .findFirst().orElse(null);

                String response;
                if (code != null) {
                    response = "Authorization successful. You can close this window.";
                    onCodeReceived.accept(code);
                } else {
                    response = "Authorization failed or canceled.";
                }

                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();

                stop(); // Stop after handling the first request
            }
        }
    }

}
