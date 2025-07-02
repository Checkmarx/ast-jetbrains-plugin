package com.checkmarx.intellij.helper;

import com.checkmarx.intellij.Utils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.*;

/**
 * OAuthCallbackServer class responsible to perform operation related to Http Server
 * This class will create the local http server and listen for OAuth redirection (call-back)
 * Http server will stop once OAuth flow is completed
 * <p>
 * {@link HttpServer}
 */
@Slf4j
public class OAuthCallbackServer {

    private HttpServer server;
    private ScheduledExecutorService scheduler;
    private final CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
    private final String callbackURL;

    /**
     * Construct the constructor with the required parameters
     * @param callbackURL - OAuth callback url
     */
    public OAuthCallbackServer(String callbackURL) {
        this.callbackURL = callbackURL;
    }

    /**
     * Starting lightweight HTTP server on a specific port to catch the redirect.
     * If authentication is not completed in a configured timeout, the server will be automatically stopped.
     *
     * @param timeoutSeconds - Callback server authentication time-out time
     * @param port           - Port number for callback server
     * @throws IOException - if port number is already in use throw exception
     */
    public void start(int timeoutSeconds, int port) throws IOException {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(callbackURL, new OAuthCallbackHandler());
            server.setExecutor(null);
            server.start();

            log.info("OAuth: Callback server started successfully. http://localhost:{}/{}", port, callbackURL);

            // Dynamic timeout support
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.schedule(() -> {
                if (!authCodeFuture.isDone()) {
                    authCodeFuture.completeExceptionally(
                            new TimeoutException("Authentication timed out after " + timeoutSeconds + " seconds."));
                    log.error("OAuth: Stopping local server due to authentication time out:{} seconds ", timeoutSeconds);
                    stop();
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        }catch (Exception exception) {
            log.error("OAuth: Unable to start the local callback Https Server. Root Cause:{}", exception.getMessage());
            throw new IOException("A required port:"+port+" is currently in use. Please try again shortly.");
        }
    }

    /**
     * Stopping lightweight local HTTP server after completing the authentication
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        log.info("OAuth: Callback server stopped successfully.");
    }

    /**
     * Asynchronously wait for the code without blocking the main thread.
     *
     * @return CompletableFuture<String> - auth code
     */
    public CompletableFuture<String> waitForAuthCode() {
        return authCodeFuture;
    }

    /**
     * Http server (callback) handler to handle the redirection and read the auth code from the response
     */
    private class OAuthCallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String query = uri.getQuery();

            if (query != null) {
                if (query.contains("code=")) {
                    String code = extractParam(query, "code");
                    log.info("OAuth: Auth code received successfully.");
                    log.debug("OAuth: Received auth code:{}", code);
                    String htmlString = Utils.loadAuthSuccessHtml("auth/auth-success.html");
                    sendResponse(exchange, htmlString, 200);
                    authCodeFuture.complete(code);
                } else {
                    String error = extractParam(query, "error");
                    String htmlString = Utils.loadAuthErrorHtml("auth/auth-error.html");
                    htmlString = htmlString.replace("ERROR_MESSAGE", error);
                    sendResponse(exchange, htmlString, 400);
                    authCodeFuture.completeExceptionally(new RuntimeException("OAuth2: Received Error: " + error));
                }
            }
            stop(); // Always stop server after one request
        }

        /**
         * Extracting the specific URL parameters.
         *
         * @param query - complete received uri from OAuth
         * @param key   - parameter name to extract
         * @return value of parameter
         */
        private String extractParam(String query, String key) {
            return Arrays.stream(query.split("&"))
                    .filter(param -> param.startsWith(key + "="))
                    .map(param -> param.substring((key + "=").length()))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Send HTML response to the user browser
         *
         * @param httpExchange HttpExchange object which helps to submit the response
         * @param respString   - Html response String
         * @param statusCode   - Response status code
         */
        private void sendResponse(HttpExchange httpExchange, String respString, int statusCode) {
            try {
                httpExchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                httpExchange.sendResponseHeaders(statusCode, respString.getBytes(StandardCharsets.UTF_8).length);
                OutputStream os = httpExchange.getResponseBody();
                os.write(respString.getBytes());
                os.close();
            } catch (Exception exception) {
                log.error("OAuth: Exception occurred while sending response to user. Root Cause:{}", exception.getMessage());
            }
        }
    }
}
