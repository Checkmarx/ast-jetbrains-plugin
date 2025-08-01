package com.checkmarx.intellij.helper;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.diagnostic.Logger;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.Setter;
import org.intellij.lang.annotations.Language;

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

public class OAuthCallbackServer {

    private static final Logger LOGGER = Utils.getLogger(OAuthCallbackServer.class);
    private final ScheduledExecutorService scheduler;
    private final CompletableFuture<String> authCodeFuture;
    private final String callbackURL;
    @Setter
    private String state;
    private HttpServer server;

    /**
     * Construct the constructor with the required parameters
     *
     * @param callbackURL - OAuth callback url
     */
    public OAuthCallbackServer(String callbackURL) {
        this.callbackURL = callbackURL;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.authCodeFuture = new CompletableFuture<>();
    }

    /**
     * Starting lightweight HTTP server on a specific port to catch the redirect.
     * If authentication is not completed in a configured timeout, the server will be automatically stopped.
     *
     * @param timeoutSeconds - Callback server authentication time-out time
     * @param port           - Port number for callback server
     * @throws CxException - if port number is already in use throw exception
     */
    public void start(int timeoutSeconds, int port) throws CxException {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext(callbackURL, new OAuthCallbackHandler());
            server.setExecutor(null);
            server.start();

            // Dynamic timeout support, stops itself and completes the CompletableFuture exceptionally after the timeout.
            scheduler.schedule(() -> {
                try {
                    if (!authCodeFuture.isDone()) {
                        authCodeFuture.completeExceptionally(
                                new TimeoutException("Auth code not received within a authentication time seconds:" + timeoutSeconds));
                        LOGGER.warn(String.format("OAuth: Stopping local server due to authentication timeout :%d seconds ", timeoutSeconds));
                        stop();
                    }
                } catch (Exception exception) {
                    LOGGER.warn(String.format("OAuth: Exception occurred during scheduled timeout handling. Root Cause:%s",
                            exception.getMessage()));
                }
            }, timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception exception) {
            LOGGER.warn(String.format("OAuth: Unable to start the local callback Http Server. Root Cause:%S",
                    exception.getMessage()));
            throw new CxException(500, Bundle.message(Resource.ERROR_PORT_NOT_AVAILABLE));
        }
    }

    /**
     * Stopping lightweight local HTTP server after completing the authentication
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            LOGGER.info("OAuth:Server stopped successfully.");
        }
        if (!scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
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
        public void handle(HttpExchange exchange) {
            URI uri = exchange.getRequestURI();
            String query = uri.getQuery();

            if (query != null && query.contains(Constants.AuthConstants.STATE)) {
                if (query.contains(Constants.AuthConstants.CODE)) {
                    String code = validateStateAndGetCode(query);
                    String htmlString = loadAuthSuccessHtml();
                    sendResponse(exchange, htmlString, 200);
                    authCodeFuture.complete(code);
                } else {
                    String error = extractParam(query, "error");
                    LOGGER.warn(String.format("OAuth: Received error from authorization endpoint. Error:%s", error));
                    String htmlString = loadAuthErrorHtml(error);
                    sendResponse(exchange, htmlString, 400);
                    authCodeFuture.completeExceptionally(new RuntimeException("OAuth2: Received Error: " + error));
                }
            }
            stop(); // Always stop server after one request
        }

        /**
         * Validating received state parameter and getting auth code from the response
         *
         * @param query - response query string from authorization endpoint
         * @return auth code
         */
        String validateStateAndGetCode(String query) {
            final String paramState = extractParam(query, Constants.AuthConstants.STATE);
            if (!state.equals(paramState)) {
                LOGGER.warn("OAuth: Received state parameter is invalid.");
                throw new IllegalStateException("Invalid authentication");
            }
            return extractParam(query, Constants.AuthConstants.CODE);
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
                LOGGER.warn(String.format("OAuth: Exception occurred while sending response to user. Root Cause:%s",
                        exception.getMessage()));
            }
        }
    }

    /**
     * Load HTML page and send in the response as a success message
     *
     * @return html string
     */
    private String loadAuthSuccessHtml() {
        String responseContent = Utils.getFileContentFromResource(Constants.AuthConstants.AUTH_SUCCESS_HTML_FILE_PATH);
        if (responseContent != null && !responseContent.isBlank()) {
            return responseContent;
        }
        return successHtmlResponse();
    }

    /**
     * Load HTML page and send in the response as a error message
     *
     * @return html string
     */
    private String loadAuthErrorHtml(String error) {
        String responseContent = Utils.getFileContentFromResource(Constants.AuthConstants.AUTH_ERROR_HTML_FILE_PATH);
        if (responseContent != null && !responseContent.isBlank()) {
            return responseContent.replace("ERROR_MESSAGE", error);
        }
        return errorHtmlResponse(error);
    }

    /**
     * Backup method to build auth success html page
     *
     * @return html string
     */
    @Language("HTML")
    private String successHtmlResponse() {
        @Language("HTML") String str = "";
        str += " <!DOCTYPE html>";
        str += "      <html lang=\"en\">";
        str += "      <head>";
        str += "          <meta charset=\"UTF-8\">";
        str += "          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">";
        str += "          <title>Login Success - Checkmarx</title>";
        str += "          <style>";
        str += "              body {";
        str += "                  font-family: Arial, sans-serif;";
        str += "                  background-color: rgba(0, 0, 0, 0.5);";
        str += "                  margin: 0;";
        str += "                  display: flex;";
        str += "                  justify-content: center;";
        str += "                  align-items: center;";
        str += "                  min-height: 100vh;";
        str += "              }";
        str += "              .modal {";
        str += "                  background: white;";
        str += "                  padding: 2rem;";
        str += "                  border-radius: 8px;";
        str += "                  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);";
        str += "                  width: 90%;";
        str += "                  max-width: 500px;";
        str += "                  text-align: center;";
        str += "              }";
        str += "              .close-button {";
        str += "                  float: right;";
        str += "                  font-size: 24px;";
        str += "                  color: #666;";
        str += "                  cursor: pointer;";
        str += "                  border: none;";
        str += "                  background: none;";
        str += "                  padding: 0;";
        str += "                  margin: -1rem -1rem 0 0;";
        str += "              }";
        str += "              h1 {";
        str += "                  color: #333;";
        str += "                  font-size: 24px;";
        str += "                  margin: 1rem 0;";
        str += "              }";
        str += "              .icon-container {";
        str += "                  margin: 2rem 0;";
        str += "              }";
        str += "              .icon {";
        str += "                  display: flex;";
        str += "                  justify-content: center;";
        str += "                  align-items: center;";
        str += "                  gap: 10px;";
        str += "              }";
        str += "              .folder {";
        str += "                  color: #6B4EFF;";
        str += "                  font-size: 48px;";
        str += "              }";
        str += "              .file {";
        str += "                  color: #6B4EFF;";
        str += "                  font-size: 48px;";
        str += "              }";
        str += "              .message {";
        str += "                  color: #666;";
        str += "                  margin: 1rem 0 2rem 0;";
        str += "              }";
        str += "              .success-note {";
        str += "                  color: #4F5CD1;";
        str += "                  font-size: 16px;";
        str += "                  margin: 2rem 0;";
        str += "              }";
        str += "              .wave-line {";
        str += "                  color: #6B4EFF;";
        str += "                  font-size: 24px;";
        str += "                  margin: 0 10px;";
        str += "              }";
        str += "          </style>";
        str += "      </head>";
        str += "      <body>";
        str += "          <div class=\"modal\">";
        str += "              <h1>You're All Set with Checkmarx!</h1>";
        str += "              <div class=\"icon-container\">";
        str += "                  <div class=\"icon\">";
        str += "                      <span class=\"folder\">📁</span>";
        str += "                      <span class=\"wave-line\">〰️〰️〰️</span>";
        str += "                      <span class=\"file\">📄</span>";
        str += "                  </div>";
        str += "              </div>";
        str += "              <p class=\"message\">You're Connected to Checkmarx!</p>";
        str += "              <p class=\"message\">You can close this window</p>";
        str += "          </div>";
        str += "      </body>";
        str += "      </html>";
        return str;
    }

    /**
     * Backup method to build auth failure html page
     *
     * @return html string
     */
    @Language("HTML")
    private String errorHtmlResponse(String error) {
        @Language("HTML") String str = "";
        str += "<!DOCTYPE html>";
        str += "      <html lang=\"en\">";
        str += "      <head>";
        str += "          <meta charset=\"UTF-8\">";
        str += "          <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">";
        str += "          <title>Login Failed - Checkmarx</title>";
        str += "          <style>";
        str += "              body {";
        str += "                  font-family: Arial, sans-serif;";
        str += "                  background-color: rgba(0, 0, 0, 0.5);";
        str += "                  margin: 0;";
        str += "                  display: flex;";
        str += "                  justify-content: center;";
        str += "                  align-items: center;";
        str += "                  min-height: 100vh;";
        str += "              }";
        str += "              .modal {";
        str += "                  background: white;";
        str += "                  padding: 2rem;";
        str += "                  border-radius: 8px;";
        str += "                  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);";
        str += "                  width: 90%;";
        str += "                  max-width: 500px;";
        str += "                  text-align: center;";
        str += "              }";
        str += "              .close-button {";
        str += "                  float: right;";
        str += "                  font-size: 24px;";
        str += "                  color: #666;";
        str += "                  cursor: pointer;";
        str += "                  border: none;";
        str += "                  background: none;";
        str += "                  padding: 0;";
        str += "                  margin: -1rem -1rem 0 0;";
        str += "              }";
        str += "              h1 {";
        str += "                  color: #333;";
        str += "                  font-size: 24px;";
        str += "                  margin: 1rem 0;";
        str += "              }";
        str += "              .icon-container {";
        str += "                  margin: 2rem 0;";
        str += "              }";
        str += "              .error-icon {";
        str += "                  font-size: 48px;";
        str += "                  color: #FF4D4F;";
        str += "              }";
        str += "              .message {";
        str += "                  color: #666;";
        str += "                  margin: 1rem 0 2rem 0;";
        str += "              }";
        str += "              .close-btn {";
        str += "                  background-color: #4F5CD1;";
        str += "                  color: white;";
        str += "                  border: none;";
        str += "                  padding: 12px 40px;";
        str += "                  border-radius: 4px;";
        str += "                  font-size: 16px;";
        str += "                  cursor: pointer;";
        str += "                  transition: background-color 0.3s;";
        str += "              }";
        str += "              .close-btn:hover {";
        str += "                  background-color: #3F4BB1;";
        str += "              }";
        str += "          </style>";
        str += "      </head>";
        str += "      <body>";
        str += "          <div class=\"modal\">";
        str += "              <h1>Authentication Failed</h1>";
        str += "              <div class=\"icon-container\">";
        str += "                  <span class=\"error-icon\">❌</span>";
        str += "              </div>";
        str += "              <p class=\"message\">" + error + "</p>";
        str += "          </div>";
        str += "      </body>";
        str += "      </html>";
        return str;
    }
}
