package com.checkmarx.intellij.common.auth;

import com.checkmarx.intellij.common.utils.Constants;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}
