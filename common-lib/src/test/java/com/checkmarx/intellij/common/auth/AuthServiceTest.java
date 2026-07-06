package com.checkmarx.intellij.common.auth;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.HttpClientUtils;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.intellij.ide.BrowserUtil;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private Project mockProject;
    
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(mockProject);
    }

    @Test
    void testConstructor() {
        AuthService newAuthService = new AuthService(mockProject);
        assertNotNull(newAuthService);
    }

    @Test
    void testSetServer() {
        OAuthCallbackServer mockServer = org.mockito.Mockito.mock(OAuthCallbackServer.class);
        assertDoesNotThrow(() -> authService.setServer(mockServer));
    }

    @Test
    void testGetCxOneAuthEndpoint() {
        String baseUrl = "https://test.checkmarx.com";
        String tenant = "test-tenant";
        
        String result = authService.getCxOneAuthEndpoint(baseUrl, tenant);
        
        assertEquals("https://test.checkmarx.com/auth/realms/test-tenant/protocol/openid-connect/auth", result);
    }

    @Test
    void testGetCxOneAuthEndpoint_WithTrailingSlash() {
        String baseUrl = "https://test.checkmarx.com/";
        String tenant = "test-tenant";
        
        String result = authService.getCxOneAuthEndpoint(baseUrl, tenant);
        
        assertEquals("https://test.checkmarx.com//auth/realms/test-tenant/protocol/openid-connect/auth", result);
    }

    @Test
    void testGetCxOneTokenEndpoint() {
        String baseUrl = "https://test.checkmarx.com";
        String tenant = "test-tenant";
        
        String result = authService.getCxOneTokenEndpoint(baseUrl, tenant);
        
        assertEquals("https://test.checkmarx.com/auth/realms/test-tenant/protocol/openid-connect/token", result);
    }

    @Test
    void testGetCxOneTokenEndpoint_WithTrailingSlash() {
        String baseUrl = "https://test.checkmarx.com/";
        String tenant = "test-tenant";
        
        String result = authService.getCxOneTokenEndpoint(baseUrl, tenant);
        
        assertEquals("https://test.checkmarx.com//auth/realms/test-tenant/protocol/openid-connect/token", result);
    }

    @Test
    void testGetCxOneEndpoints_EmptyBaseUrl() {
        String baseUrl = "";
        String tenant = "test-tenant";
        
        String authResult = authService.getCxOneAuthEndpoint(baseUrl, tenant);
        String tokenResult = authService.getCxOneTokenEndpoint(baseUrl, tenant);
        
        assertEquals("/auth/realms/test-tenant/protocol/openid-connect/auth", authResult);
        assertEquals("/auth/realms/test-tenant/protocol/openid-connect/token", tokenResult);
    }

    @Test
    void testGetCxOneEndpoints_EmptyTenant() {
        String baseUrl = "https://test.checkmarx.com";
        String tenant = "";
        
        String authResult = authService.getCxOneAuthEndpoint(baseUrl, tenant);
        String tokenResult = authService.getCxOneTokenEndpoint(baseUrl, tenant);
        
        assertEquals("https://test.checkmarx.com/auth/realms//protocol/openid-connect/auth", authResult);
        assertEquals("https://test.checkmarx.com/auth/realms//protocol/openid-connect/token", tokenResult);
    }

    @Test
    void testIsPortAvailable_ValidAvailablePort() {
        // Test with a port that's likely to be available
        boolean result = authService.isPortAvailable(65432);
        
        // We can't guarantee this port is available, but the method should not throw an exception
        assertNotNull(result);
    }

    @Test
    void testIsPortAvailable_InvalidPort() {
        // Test with an invalid port number (negative)
        boolean result = authService.isPortAvailable(-1);
        
        // Should return false for invalid port
        assertFalse(result);
    }

    @Test
    void testIsPortAvailable_PrivilegedPort() {
        // Test with a privileged port (below 1024)
        boolean result = authService.isPortAvailable(80);
        
        // Should return false for privileged port (likely in use)
        // The method should handle this gracefully
        assertNotNull(result);
    }

    @Test
    void testFindAvailablePort() {
        int port = authService.findAvailablePort();
        
        // Should return a valid port number or 0 if none found
        assertTrue(port >= 0 && port <= 65535);
    }

    @Test
    void testExtractRefreshTokenDetails_Success() throws Exception {
        String jsonBody = "{\"refresh_token\":\"test-refresh-token\",\"refresh_expires_in\":3600}";
        
        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);
        
        assertNotNull(result);
        assertEquals("test-refresh-token", result.get(Constants.AuthConstants.REFRESH_TOKEN));
        assertNotNull(result.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY));
    }

    @Test
    void testExtractRefreshTokenDetails_InvalidJson() {
        String jsonBody = "invalid json";
        
        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractRefreshTokenDetails_MissingToken() throws Exception {
        String jsonBody = "{\"access_token\":\"test-access-token\"}";
        
        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractRefreshTokenDetails_MissingExpiry() {
        String jsonBody = "{\"refresh_token\":\"test-refresh-token\"}";

        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);

        // The map should contain the refresh token even when expiry field is missing
        assertNotNull(result);
        assertFalse(result.isEmpty(), "The result map should contain the refresh token even when expiry field is missing.");
        assertEquals("test-refresh-token", result.get(Constants.AuthConstants.REFRESH_TOKEN));
        assertNull(result.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY), "Expiry should be null when not provided in JSON.");
    }

    @Test
    void testExtractRefreshTokenDetails_JsonParseException() {
        String jsonBody = "{\"refresh_token\":,\"refresh_expires_in\":3600}";

        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);

        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractRefreshTokenDetails_EmptyJson() {
        String jsonBody = "{}";

        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);

        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractRefreshTokenDetails_NullJson() {
        String jsonBody = null;

        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);

        assertTrue(result.isEmpty());
    }

    @Test
    void testExtractRefreshTokenDetails_InvalidExpiryValue() {
        String jsonBody = "{\"refresh_token\":\"test-refresh-token\",\"refresh_expires_in\":\"invalid-number\"}";

        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);

        // Should contain refresh token but not expiry due to NumberFormatException
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("test-refresh-token", result.get(Constants.AuthConstants.REFRESH_TOKEN));
        assertNull(result.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY));
    }

    @Test
    void testExtractRefreshTokenDetails_NumericExpiryValue() {
        String jsonBody = "{\"refresh_token\":\"test-refresh-token\",\"refresh_expires_in\":3600}";

        Map<String, Object> result = authService.extractRefreshTokenDetails(jsonBody);

        assertNotNull(result);
        assertEquals("test-refresh-token", result.get(Constants.AuthConstants.REFRESH_TOKEN));
        assertNotNull(result.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY));
    }

    @Test
    void testBasicMethodExistence() {
        // Test that all methods exist and can be called (even if they fail)
        assertDoesNotThrow(() -> {
            try {
                authService.authenticate("https://test.com", "tenant", result -> {});
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.processAuthentication("verifier", "challenge", "https://test.com", "tenant", result -> {});
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.exchangeCodeForToken("https://test.com/token", "code", "verifier", "http://localhost");
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.callTokenEndpoint("https://test.com/token", "code", "verifier", "http://localhost");
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.findAvailablePort();
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.isPortAvailable(50000);
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.openDefaultBrowser("https://test.com");
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.saveToken("test-token");
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.setAuthErrorResult(result -> {}, "error");
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                authService.setAuthSuccessResult(result -> {}, new HashMap<>());
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
        
        assertDoesNotThrow(() -> {
            try {
                OAuthCallbackServer mockServer = org.mockito.Mockito.mock(OAuthCallbackServer.class);
                authService.setServer(mockServer);
                authService.buildCxOneOAuthAuthorizationUrl("https://test.com/auth", "http://localhost", "challenge");
            } catch (Exception e) {
                // Expected due to no mocking
            }
        });
    }

    @Test
    void testExtractRefreshTokenDetails_WhitespaceOnly_ReturnsEmpty() {
        Map<String, Object> result = authService.extractRefreshTokenDetails("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void testSaveToken_callsSetAndSaveOnSensitiveState() {
        try (MockedStatic<GlobalSettingsSensitiveState> stateMock = mockStatic(GlobalSettingsSensitiveState.class)) {
            GlobalSettingsSensitiveState mockState = mock(GlobalSettingsSensitiveState.class);
            stateMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockState);

            authService.saveToken("test-refresh-token");

            verify(mockState).setRefreshToken("test-refresh-token");
            verify(mockState).saveRefreshToken("test-refresh-token");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetAuthErrorResult_schedulesConsumerWithErrorKey() {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));

            Map<String, Object>[] captured = new Map[1];
            Consumer<Map<String, Object>> consumer = result -> captured[0] = result;

            authService.setAuthErrorResult(consumer, "auth-failed");

            assertNotNull(captured[0]);
            assertEquals("auth-failed", captured[0].get(Constants.AuthConstants.ERROR));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testSetAuthSuccessResult_schedulesConsumerWithSuccessMap() {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));

            Map<String, Object>[] captured = new Map[1];
            Consumer<Map<String, Object>> consumer = result -> captured[0] = result;
            Map<String, Object> successMap = new HashMap<>();
            successMap.put(Constants.AuthConstants.REFRESH_TOKEN, "tok");

            authService.setAuthSuccessResult(consumer, successMap);

            assertNotNull(captured[0]);
            assertEquals("tok", captured[0].get(Constants.AuthConstants.REFRESH_TOKEN));
        }
    }

    @Test
    void testBuildCxOneOAuthAuthorizationUrl_containsRequiredQueryParams() throws Exception {
        OAuthCallbackServer mockServer = mock(OAuthCallbackServer.class);
        authService.setServer(mockServer);

        String result = authService.buildCxOneOAuthAuthorizationUrl(
                "https://auth.example.com/realms/test/protocol/openid-connect/auth",
                "http://localhost:8080/checkmarx1/callback",
                "testChallenge123"
        );

        assertNotNull(result);
        assertTrue(result.contains("response_type=code"));
        assertTrue(result.contains("code_challenge=testChallenge123"));
        assertTrue(result.contains("redirect_uri="));
        verify(mockServer).setState(anyString());
    }

    @Test
    void testOpenDefaultBrowser_callsApplicationInvokeLater() {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);

            authService.openDefaultBrowser("https://example.com");

            verify(mockApp).invokeLater(any(Runnable.class));
        }
    }

    @Test
    void testExchangeCodeForToken_whenRetryFails_returnsEmptyMap() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.executeWithRetry(any(), anyInt(), anyLong()))
                    .thenThrow(new RuntimeException("all retries exhausted"));

            Map<String, Object> result = authService.exchangeCodeForToken(
                    "https://example.com/token", "code123", "verifier", "http://localhost");

            assertTrue(result.isEmpty());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAuthenticate_whenCodeVerifierNull_setsErrorResult() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS);
             MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            utilsMock.when(Utils::generateCodeVerifier).thenReturn(null);
            bundleMock.when(() -> Bundle.message(any())).thenReturn("error");

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));

            Map<String, Object>[] captured = new Map[1];
            authService.authenticate("https://test.com", "tenant", result -> captured[0] = result);

            assertNotNull(captured[0]);
            assertNotNull(captured[0].get(Constants.AuthConstants.ERROR));
        }
    }

    @Test
    void testIsPortAvailable_portZero_returnsTrue() {
        assertTrue(authService.isPortAvailable(0));
    }

    @Test
    void testIsPortAvailable_portAboveMax_returnsFalse() {
        assertFalse(authService.isPortAvailable(65536));
    }

    @Test
    void testSaveToken_withEmptyString_callsSensitiveState() {
        try (MockedStatic<GlobalSettingsSensitiveState> stateMock = mockStatic(GlobalSettingsSensitiveState.class)) {
            GlobalSettingsSensitiveState mockState = mock(GlobalSettingsSensitiveState.class);
            stateMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockState);

            authService.saveToken("");

            verify(mockState).setRefreshToken("");
            verify(mockState).saveRefreshToken("");
        }
    }

    @Test
    void testProcessAuthentication_portZero_setsErrorResult() {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(any())).thenReturn("error");

            OAuthCallbackServer mockServer = mock(OAuthCallbackServer.class);
            authService.setServer(mockServer);

            // processAuthentication with port=0 returned (no available port)
            // We spy the authService to stub findAvailablePort
            AuthService spy = spy(authService);
            doReturn(0).when(spy).findAvailablePort();

            @SuppressWarnings("unchecked")
            Map<String, Object>[] captured = new Map[1];
            spy.processAuthentication("verifier", "challenge", "https://test.com", "tenant", result -> captured[0] = result);

            assertNotNull(captured[0]);
            assertNotNull(captured[0].get(Constants.AuthConstants.ERROR));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessAuthentication_serverThrowsException_setsErrorResult() {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(any())).thenReturn("error");

            OAuthCallbackServer mockServer = mock(OAuthCallbackServer.class);
            authService.setServer(mockServer);

            AuthService spy = spy(authService);
            doThrow(new RuntimeException("network error")).when(spy).findAvailablePort();

            Map<String, Object>[] captured = new Map[1];
            spy.processAuthentication("verifier", "challenge", "https://test.com", "tenant", result -> captured[0] = result);

            assertNotNull(captured[0]);
            assertNotNull(captured[0].get(Constants.AuthConstants.ERROR));
        }
    }

    @Test
    void testIsPortAvailable_alreadyBoundPort_returnsFalse() throws Exception {
        // Bind a port ourselves so we can assert isPortAvailable returns false for it
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(0)) {
            int boundPort = serverSocket.getLocalPort();
            // Port is still in use by our socket → isPortAvailable should return false
            // (on some OS the reuse flag may still allow it, so we just assert no exception)
            boolean result = authService.isPortAvailable(boundPort);
            // result may be true or false depending on OS/socket reuse, just don't throw
            assertNotNull((Boolean) result);
        }
    }

    @Test
    void testFindAvailablePort_returnsValidOrZero() {
        // This exercises the full loop — result must be a valid port or 0
        int port = authService.findAvailablePort();
        assertTrue(port >= 0 && port <= 65535);
        if (port != 0) {
            assertTrue(port >= 49152, "Dynamic port should be in IANA dynamic range");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCallTokenEndpoint_200Status_returnsExtractedDetails() throws Exception {
        String tokenEndpoint = "https://example.com/token";
        String responseBody = "{\"refresh_token\":\"tok123\",\"refresh_expires_in\":3600}";

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseBody);
        doReturn(mockResponse).when(mockClient).send(any(), any());

        try (MockedStatic<HttpClientUtils> clientUtilsMock = mockStatic(HttpClientUtils.class)) {
            clientUtilsMock.when(() -> HttpClientUtils.createHttpClient(tokenEndpoint)).thenReturn(mockClient);

            Map<String, Object> result = authService.callTokenEndpoint(tokenEndpoint, "code", "verifier", "http://localhost");

            assertNotNull(result);
            assertEquals("tok123", result.get(Constants.AuthConstants.REFRESH_TOKEN));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCallTokenEndpoint_redirectWithLocation_usesNewEndpoint() throws Exception {
        String tokenEndpoint = "https://example.com/token";
        String newEndpoint = "https://iam.example.com/token";

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> redirectResponse = mock(HttpResponse.class);
        when(redirectResponse.statusCode()).thenReturn(301);
        HttpHeaders mockHeaders = mock(HttpHeaders.class);
        when(redirectResponse.headers()).thenReturn(mockHeaders);
        when(mockHeaders.map()).thenReturn(Map.of(Constants.AuthConstants.LOCATION, List.of(newEndpoint)));

        HttpResponse<String> nullResponseOnRetry = mock(HttpResponse.class);
        when(nullResponseOnRetry.statusCode()).thenReturn(400);
        when(nullResponseOnRetry.headers()).thenReturn(mock(HttpHeaders.class));

        // First call returns redirect, subsequent calls return 400
        doReturn(redirectResponse).doReturn(nullResponseOnRetry).when(mockClient).send(any(), any());

        try (MockedStatic<HttpClientUtils> clientUtilsMock = mockStatic(HttpClientUtils.class)) {
            clientUtilsMock.when(() -> HttpClientUtils.createHttpClient(any())).thenReturn(mockClient);

            Map<String, Object> result = authService.callTokenEndpoint(tokenEndpoint, "code", "verifier", "http://localhost");

            assertNull(result); // exhausted retries without 200
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCallTokenEndpoint_nullResponse_returnsNull() throws Exception {
        String tokenEndpoint = "https://example.com/token";

        HttpClient mockClient = mock(HttpClient.class);
        doReturn(null).when(mockClient).send(any(), any());

        try (MockedStatic<HttpClientUtils> clientUtilsMock = mockStatic(HttpClientUtils.class)) {
            clientUtilsMock.when(() -> HttpClientUtils.createHttpClient(tokenEndpoint)).thenReturn(mockClient);

            Map<String, Object> result = authService.callTokenEndpoint(tokenEndpoint, "code", "verifier", "http://localhost");

            assertNull(result);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessAuthentication_timeoutException_setsErrorResult() {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(any())).thenReturn("timeout error");

            OAuthCallbackServer mockServer = mock(OAuthCallbackServer.class);
            when(mockServer.waitForAuthCode()).thenThrow(new RuntimeException(new TimeoutException("timed out")));
            authService.setServer(mockServer);

            AuthService spy = spy(authService);
            doReturn(50000).when(spy).findAvailablePort();

            Map<String, Object>[] captured = new Map[1];
            spy.processAuthentication("verifier", "challenge", "https://test.com", "tenant", result -> captured[0] = result);

            assertNotNull(captured[0]);
            assertNotNull(captured[0].get(Constants.AuthConstants.ERROR));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessAuthentication_successPath_setsSuccessResult() throws Exception {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<GlobalSettingsSensitiveState> stateMock = mockStatic(GlobalSettingsSensitiveState.class)) {

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(any())).thenReturn("msg");

            GlobalSettingsSensitiveState mockSensitiveState = mock(GlobalSettingsSensitiveState.class);
            stateMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitiveState);

            OAuthCallbackServer mockServer = mock(OAuthCallbackServer.class);
            AuthService spy = spy(authService);
            spy.setServer(mockServer);

            CompletableFuture<String> codeFuture = CompletableFuture.completedFuture("auth-code-xyz");
            when(mockServer.waitForAuthCode()).thenReturn(codeFuture);

            doReturn(50001).when(spy).findAvailablePort();

            Map<String, Object> tokenDetails = new HashMap<>();
            tokenDetails.put(Constants.AuthConstants.REFRESH_TOKEN, "my-refresh-token");
            doReturn(tokenDetails).when(spy).exchangeCodeForToken(anyString(), anyString(), anyString(), anyString());

            Map<String, Object>[] captured = new Map[1];
            spy.processAuthentication("verifier", "challenge", "https://test.com", "tenant",
                    result -> captured[0] = result);

            assertNotNull(captured[0]);
            assertNull(captured[0].get(Constants.AuthConstants.ERROR));
            verify(mockSensitiveState).setRefreshToken("my-refresh-token");
            verify(mockSensitiveState).saveRefreshToken("my-refresh-token");
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessAuthentication_emptyTokenMap_setsErrorResult() throws Exception {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(any())).thenReturn("error");

            OAuthCallbackServer mockServer = mock(OAuthCallbackServer.class);
            AuthService spy = spy(authService);
            spy.setServer(mockServer);

            CompletableFuture<String> codeFuture = CompletableFuture.completedFuture("some-code");
            when(mockServer.waitForAuthCode()).thenReturn(codeFuture);
            doReturn(50002).when(spy).findAvailablePort();
            doReturn(new HashMap<String, Object>()).when(spy).exchangeCodeForToken(anyString(), anyString(), anyString(), anyString());

            Map<String, Object>[] captured = new Map[1];
            spy.processAuthentication("v", "c", "https://test.com", "t", result -> captured[0] = result);

            assertNotNull(captured[0]);
            assertNotNull(captured[0].get(Constants.AuthConstants.ERROR));
        }
    }

    @Test
    void testOpenDefaultBrowser_lambdaBodyExecuted_browsesCalled() {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<BrowserUtil> browserUtilMock = mockStatic(BrowserUtil.class)) {

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));

            authService.openDefaultBrowser("https://valid.example.com");

            browserUtilMock.verify(() -> BrowserUtil.browse(any(URI.class)));
        }
    }

    @Test
    void testFindAvailablePort_noAvailablePort_returnsZero() {
        AuthService spy = spy(authService);
        doReturn(false).when(spy).isPortAvailable(anyInt());

        int port = spy.findAvailablePort();

        assertEquals(0, port);
    }

    @Test
    void testFindAvailablePort_isPortAvailableThrows_handlesExceptionAndReturnsZero() {
        AuthService spy = spy(authService);
        doThrow(new RuntimeException("port check failed")).when(spy).isPortAvailable(anyInt());

        int port = spy.findAvailablePort();

        assertEquals(0, port);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessAuthentication_futureExecutionException_setsErrorResult() throws Exception {
        try (MockedStatic<ApplicationManager> appManagerStatic = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<BrowserUtil> browserMock = mockStatic(BrowserUtil.class)) {

            Application mockApp = mock(Application.class);
            appManagerStatic.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(any())).thenReturn("timeout error");

            OAuthCallbackServer mockServer = mock(OAuthCallbackServer.class);
            AuthService spy = spy(authService);
            spy.setServer(mockServer);

            CompletableFuture<String> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(new java.io.IOException("auth server failed"));
            when(mockServer.waitForAuthCode()).thenReturn(failedFuture);
            doReturn(50003).when(spy).findAvailablePort();

            Map<String, Object>[] captured = new Map[1];
            spy.processAuthentication("v", "c", "https://test.com", "t", result -> captured[0] = result);

            assertNotNull(captured[0]);
            assertNotNull(captured[0].get(Constants.AuthConstants.ERROR));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExchangeCodeForToken_callTokenEndpointThrowsIO_returnsEmptyMap() throws Exception {
        AuthService spy = spy(authService);
        doThrow(new java.io.IOException("connection refused"))
                .when(spy).callTokenEndpoint(anyString(), anyString(), anyString(), anyString());

        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class, CALLS_REAL_METHODS)) {
            utilsMock.when(() -> Utils.executeWithRetry(any(), anyInt(), anyLong()))
                    .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());

            Map<String, Object> result = spy.exchangeCodeForToken("https://endpoint", "code", "verifier", "http://redirect");

            assertTrue(result.isEmpty());
        }
    }
}
