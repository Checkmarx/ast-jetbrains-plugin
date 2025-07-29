/*
package com.checkmarx.intellij.service;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.helper.OAuthCallbackServer;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

*/
/**
 * AuthServiceTest class responsible to execute unit cases for {@link AuthService}
 *//*

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private Project project;

    @InjectMocks
    @Spy
    private AuthService authService;

    @Mock
    private Consumer<Map<String, Object>> authResult;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<Object> mockResponse;

    @Mock
    private OAuthCallbackServer callbackServer;

    private static final String BASE_URL = "https://ast-master-components.dev.cxast.net";
    private static final String TENANT = "dev_tenant";
    private static final String AUTH_ENDPOINT = BASE_URL + "/auth/realms/" + TENANT + "/protocol/openid-connect/auth";
    private static final String TOKEN_ENDPOINT = BASE_URL + "/auth/realms/" + TENANT + "/protocol/openid-connect/token";
    private static final String AUTH_CODE = "auth-code";
    private static final String CODE_VERIFIER = "verifier";
    private static final String CODE_CHALLENGE = "challenge";
    private static final String REDIRECT_URI = "http://localhost:5000/checkmarx1/callback";
    private static final String MOCK_REFRESH = "eyJhbGciOiJIUzUxMiIsInR5cCIgOiA.iSldUIiwia2lkIiA6ICIxNmFkYTA.yNS02YWY0LTQ3YjgtOTc5Ni1hZj";

    @BeforeEach
    protected void setUp() throws Exception {
        authService = spy(new AuthService(project));
        authService.server = callbackServer;
    }

    */
/**
     * Error Test, code verifier and code challenge null
     *//*

    @Test
    public void testAuthenticate_codeVerifierNull_authError() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(Utils::generateCodeVerifier).thenReturn(null);
            utilsMock.when(() -> Utils.generateCodeChallenge(null)).thenReturn(null);
            doNothing().when(authService).setAuthErrorResult(any(), anyString());
            authService.authenticate(BASE_URL, TENANT, authResult);
            verify(authService).setAuthErrorResult(eq(authResult), eq(Bundle.message(Resource.VALIDATE_ERROR)));
        }
    }

    */
/**
     * Error test when code verifier null to generate a code challenge
     *//*

    @Test
    public void testAuthenticate_codeChallengeException_authError() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(Utils::generateCodeVerifier).thenThrow(new RuntimeException("Code verifier null."));
            doNothing().when(authService).setAuthErrorResult(any(), anyString());
            authService.authenticate(BASE_URL, TENANT, authResult);
            verify(authService).setAuthErrorResult(eq(authResult), eq(Bundle.message(Resource.VALIDATE_ERROR)));
        }
    }

  */
/*  @Test
    public void testAuthenticate_validToken_authSuccess() {
        Map<String, Object> mockTokenDetails = getTokenMap();
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            // Mocking Utils methods to return expected values
            utilsMock.when(Utils::generateCodeVerifier).thenReturn(CODE_VERIFIER);
            utilsMock.when(() -> Utils.generateCodeChallenge(anyString())).thenReturn(CODE_CHALLENGE);

            // Mock OAuthCallbackServer behavior
            when(authService.server.waitForAuthCode()).thenReturn(getAuthCode());
            // Mock behavior for the exchange of code for token
            when(authService.exchangeCodeForToken(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockTokenDetails);
            doNothing().when(authService).setAuthSuccessResult(any(), any());
            authService.authenticate(BASE_URL, TENANT, authResult);
            // Assert: Verify that the result was successfully passed to the callback
            verify(authResult, times(1)).accept(argThat(resultMap -> {
                // Assert the result map has the correct refresh token
                return resultMap.containsKey(Constants.AuthConstants.REFRESH_TOKEN)
                        && TOKEN.equals(resultMap.get(Constants.AuthConstants.REFRESH_TOKEN));
            }));
            // Verify if the saveToken method was called with the correct refresh token
            verify(authService, times(1)).saveToken(TOKEN);
        }
    }*//*


    */
/**
     * Error test, if port not available
     *//*

    @Test
    void testProcessAuthentication_portUnavailable_authError() {
        doReturn(0).when(authService).findAvailablePort();
        doNothing().when(authService).setAuthErrorResult(any(), anyString());
        authService.processAuthentication(CODE_VERIFIER, CODE_CHALLENGE, BASE_URL, TENANT, authResult);
        verify(authService).setAuthErrorResult(eq(authResult), eq(Bundle.message(Resource.ERROR_PORT_NOT_AVAILABLE)));
    }

    */
/**
     * Success test return valid port
     *//*

    @Test
    void testFindAvailablePort_returnsValidPort() {
        int port = authService.findAvailablePort();
        // The result should be in the valid range or 0
        assertTrue((port >= 49152 && port <= 65535) || port == 0);
    }

    @Test
    void testFindAvailablePort_allAttemptsFail_returnsZero() {
        doReturn(false).when(authService).isPortAvailable(anyInt());
        int port = authService.findAvailablePort();
        assertEquals(0, port);
    }

  */
/*  @Test
    void testProcessAuthentication_validToken_authSuccess() throws Exception {

        Map<String, Object> mockTokenMap = getTokenMap();

        // Mock internal/protected method calls
        doReturn(AUTH_ENDPOINT).when(authService).getCxOneAuthEndpoint(BASE_URL, TENANT);
        doReturn(TOKEN_ENDPOINT).when(authService).getCxOneTokenEndpoint(BASE_URL, TENANT);
        doReturn(5000).when(authService).findAvailablePort();
        doReturn(getAuthorizationEndPoint()).when(authService).buildCxOneOAuthAuthorizationUrl(anyString(), anyString(), eq(CODE_CHALLENGE));
        // Mock server behavior
        doReturn(getAuthCode()).when(authService.server).waitForAuthCode();
        doReturn(mockTokenMap).when(authService).exchangeCodeForToken(eq(TOKEN_ENDPOINT), eq(AUTH_CODE), eq(CODE_VERIFIER), eq(REDIRECT_URI));

        // Stub other methods
        doNothing().when(authService).openDefaultBrowser(anyString());
        doNothing().when(authService).saveToken(anyString());
        doNothing().when(authService).setAuthSuccessResult(authResult, mockTokenMap);
        doNothing().when(authService.server).start(anyInt(), eq(5000));
        doNothing().when(authService.server).stop();
        // When
        authService.processAuthentication(CODE_VERIFIER, CODE_CHALLENGE, BASE_URL, TENANT, authResult);
        authService.setAuthSuccessResult(authResult, mockTokenMap);
        verify(authResult, times(1)).accept(argThat(resultMap -> {
            // Assert the result map has the correct refresh token
            return resultMap.containsKey(Constants.AuthConstants.REFRESH_TOKEN)
                    && TOKEN.equals(resultMap.get(Constants.AuthConstants.REFRESH_TOKEN));
        }));
        verify(log).debug(anyString());  // Check that debug logs were generated
        verify(authService.server).start(anyInt(), eq(5000));
        verify(authService.server).stop();
    }*//*


    */
/**
     * Success test, getting valid token
     *
     * @throws Exception Exception
     *//*

    @Test
    void testCallTokenEndpoint_success200() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(getJsonResponse());
        try (MockedStatic<HttpClient> clientStatic = mockStatic(HttpClient.class)) {
            clientStatic.when(HttpClient::newHttpClient).thenReturn(mockHttpClient);
            when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);
            Map<String, Object> result = authService.callTokenEndpoint(TOKEN_ENDPOINT, AUTH_CODE, CODE_VERIFIER, REDIRECT_URI);
            assertNotNull(result);
            assertEquals(MOCK_REFRESH, result.get(Constants.AuthConstants.REFRESH_TOKEN));
        }
    }

    */
/**
     * Success test, getting token after redirect response
     *
     * @throws Exception if any issues with uri syntax
     *//*

    @Test
    void testCallTokenEndpoint_redirect308_thenSuccess() throws Exception {
        when(mockResponse.statusCode()).thenReturn(308);
        when(mockResponse.headers()).thenReturn(HttpHeaders.of(
                Map.of(Constants.AuthConstants.LOCATION, List.of(REDIRECT_URI)),
                (k, v) -> true
        ));
        HttpResponse<Object> successResponse = mock(HttpResponse.class);
        when(successResponse.statusCode()).thenReturn(200);
        when(successResponse.body()).thenReturn(getJsonResponse());

        try (MockedStatic<HttpClient> clientStatic = mockStatic(HttpClient.class)) {
            clientStatic.when(HttpClient::newHttpClient).thenReturn(mockHttpClient);
            when(mockHttpClient.send(any(), any()))
                    .thenReturn(mockResponse)
                    .thenReturn(successResponse);
            Map<String, Object> result = authService.callTokenEndpoint(TOKEN_ENDPOINT, AUTH_CODE, CODE_VERIFIER, REDIRECT_URI);
            assertEquals(MOCK_REFRESH, result.get(Constants.AuthConstants.REFRESH_TOKEN));
        }
    }

    */
/**
     * Retries test, getting token with retries
     *
     * @throws Exception
     *//*

    @Test
    void testCallTokenEndpoint_allRetriesFail_returnsNull() throws Exception {
        when(mockResponse.statusCode()).thenReturn(500);
        try (MockedStatic<HttpClient> clientStatic = mockStatic(HttpClient.class)) {
            clientStatic.when(HttpClient::newHttpClient).thenReturn(mockHttpClient);
            when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);
            Map<String, Object> result = authService.callTokenEndpoint(TOKEN_ENDPOINT, AUTH_CODE, CODE_VERIFIER, REDIRECT_URI);
            assertNull(result);
        }
    }
*/
/*
    @Test
    void testExchangeCodeForToken_failsAfterRetry_throwsCxException() {
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {
            utilsMock.when(() -> Utils.executeWithRetry(isNull(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("Something went wrong, Please try again."));
            Exception exception = assertThrows(Exception.class, () ->
                    authService.exchangeCodeForToken(anyString(), anyString(), anyString(), anyString())
            );
            assertEquals("Something went wrong, Please try again.", exception.getMessage());
            verify(authService).setAuthErrorResult(eq(authResult), eq(Bundle.message(Resource.VALIDATE_ERROR)));
        }
    }*//*


  */
/*  @Test
    void testExecuteWithRetry_throwsExceptionAfterRetries() {
        int maxRetries = 3;
        long delayMillis = 10;
        try (MockedStatic<Utils> utilsMock = mockStatic(Utils.class)) {

            Supplier<String> failingSupplier = () -> {
                throw new RuntimeException("Simulated failure");
            };

            utilsMock.when(() -> Utils.executeWithRetry(failingSupplier, maxRetries, delayMillis))
                    .thenThrow(new RuntimeException("Something went wrong, Please try again."));

            Exception exception = assertThrows(Exception.class, () ->
                    authService.exchangeCodeForToken(anyString(), anyString(), anyString(), anyString())
            );

            assertEquals("Simulated failure", exception.getMessage());
        }
    }*//*


    */
/**
     * Success test, extracting token from the response
     *//*

    @Test
    void testExtractRefreshTokenDetails_validJson_success() {
        Map<String, Object> result = authService.extractRefreshTokenDetails(getJsonResponse());
        assertEquals(MOCK_REFRESH, result.get(Constants.AuthConstants.REFRESH_TOKEN));
        assertNotNull(result.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY));
    }

    */
/**
     * Success test, saving token details
     *//*

    @Test
    void testSaveToken_callsGlobalSettings() {
        GlobalSettingsSensitiveState mockState = mock(GlobalSettingsSensitiveState.class);
        try (MockedStatic<GlobalSettingsSensitiveState> mockedStatic = mockStatic(GlobalSettingsSensitiveState.class)) {
            mockedStatic.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockState);
            doNothing().when(mockState).setRefreshToken(MOCK_REFRESH);
            doNothing().when(mockState).saveRefreshToken(MOCK_REFRESH);

            authService.saveToken(MOCK_REFRESH);

            verify(mockState).setRefreshToken(MOCK_REFRESH);
            verify(mockState).saveRefreshToken(MOCK_REFRESH);
        }
    }

    private Map<String, Object> getTokenMap() {
        Map<String, Object> tokenMap = new HashMap<>();
        tokenMap.put(Constants.AuthConstants.REFRESH_TOKEN, MOCK_REFRESH);
        tokenMap.put(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY, "604800");
        return tokenMap;
    }

    private CompletableFuture<String> getAuthCode() {
        CompletableFuture<String> authCodeFuture = new CompletableFuture<>();
        authCodeFuture.complete(AUTH_CODE);
        return authCodeFuture;
    }

    private String getJsonResponse() {
        return "{\"refresh_expires_in\":604800,\"refresh_token\":\"" + MOCK_REFRESH + "\"}";
    }

    private String getAuthorizationEndPoint() {
        return AUTH_ENDPOINT + "?response_type=code&client_id=ide-integration" +
                "&redirect_uri=http%3A%2F%2Flocalhost%3A5000%2Fcheckmarx1%2Fcallback&scope=openid+offline_access" +
                "&state=d19e1546-f2c2-4da6-8d0d-6b92b7ec7717&code_challenge=" + CODE_CHALLENGE + "&code_challenge_method=S256";
    }
}

*/
