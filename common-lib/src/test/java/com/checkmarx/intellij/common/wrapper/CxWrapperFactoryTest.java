package com.checkmarx.intellij.common.wrapper;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CxWrapperFactoryTest {

    @Mock
    private Application mockApplication;
    
    @Mock
    private GlobalSettingsState mockGlobalSettingsState;
    
    @Mock
    private GlobalSettingsSensitiveState mockGlobalSettingsSensitiveState;
    
    @Mock
    private CxConfig.CxConfigBuilder mockCxConfigBuilder;
    
    @Mock
    private CxConfig mockCxConfig;
    
    @Mock
    private CxWrapper mockCxWrapper;
    
    private MockedStatic<ApplicationManager> mockedApplicationManager;
    private MockedStatic<GlobalSettingsState> mockedGlobalSettingsState;
    private MockedStatic<GlobalSettingsSensitiveState> mockedGlobalSettingsSensitiveState;
    private MockedStatic<CxConfig> mockedCxConfig;
    private MockedStatic<Utils> mockedUtils;

    @BeforeEach
    void setUp() {
        mockedApplicationManager = mockStatic(ApplicationManager.class);
        mockedGlobalSettingsState = mockStatic(GlobalSettingsState.class);
        mockedGlobalSettingsSensitiveState = mockStatic(GlobalSettingsSensitiveState.class);
        mockedCxConfig = mockStatic(CxConfig.class);
        mockedUtils = mockStatic(Utils.class);

        mockedApplicationManager.when(ApplicationManager::getApplication).thenReturn(mockApplication);
        mockedGlobalSettingsState.when(GlobalSettingsState::getInstance).thenReturn(mockGlobalSettingsState);
        mockedGlobalSettingsSensitiveState.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockGlobalSettingsSensitiveState);
        mockedCxConfig.when(CxConfig::builder).thenReturn(mockCxConfigBuilder);
        
        when(mockCxConfigBuilder.agentName(anyString())).thenReturn(mockCxConfigBuilder);
        when(mockCxConfigBuilder.apiKey(anyString())).thenReturn(mockCxConfigBuilder);
        when(mockCxConfigBuilder.clientId(anyString())).thenReturn(mockCxConfigBuilder);
        when(mockCxConfigBuilder.additionalParameters(anyString())).thenReturn(mockCxConfigBuilder);
        when(mockCxConfigBuilder.build()).thenReturn(mockCxConfig);
    }

    @AfterEach
    void tearDown() {
        if (mockedApplicationManager != null) {
            mockedApplicationManager.close();
        }
        if (mockedGlobalSettingsState != null) {
            mockedGlobalSettingsState.close();
        }
        if (mockedGlobalSettingsSensitiveState != null) {
            mockedGlobalSettingsSensitiveState.close();
        }
        if (mockedCxConfig != null) {
            mockedCxConfig.close();
        }
        if (mockedUtils != null) {
            mockedUtils.close();
        }
    }

    @Test
    void testBuild_WithApiKeyEnabled_ValidCredentials() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("test-api-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("--param=value");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("test-api-key");
        verify(mockCxConfigBuilder).additionalParameters("--param=value");
        verify(mockCxConfigBuilder).build();
        
        verify(mockGlobalSettingsState, never()).getRefreshTokenExpiry();
        verify(mockGlobalSettingsSensitiveState, never()).getRefreshToken();
        // Utils.notifySessionExpired() is called when credentials are expired
    }

    @Test
    void testBuild_WithRefreshTokenEnabled_ValidCredentials() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("test-refresh-token");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("test-refresh-token");
        verify(mockCxConfigBuilder).clientId("ide-integration");
        verify(mockCxConfigBuilder).additionalParameters("");
        verify(mockCxConfigBuilder).build();
        
        verify(mockGlobalSettingsState, times(1)).getRefreshTokenExpiry();
        verify(mockGlobalSettingsSensitiveState, times(1)).getRefreshToken();
        // Utils.notifySessionExpired() is called when credentials are expired
    }

    @Test
    void testBuild_WithRefreshTokenEnabled_ExpiredToken() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("expired-refresh-token");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn("2020-01-01T00:00:00");
        when(mockGlobalSettingsSensitiveState.isTokenExpired(anyString())).thenReturn(true);

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).build();
        // Utils.notifySessionExpired() is called when credentials are expired
        
        verify(mockCxConfigBuilder, never()).apiKey(anyString());
        verify(mockCxConfigBuilder, never()).clientId(anyString());
        verify(mockCxConfigBuilder, never()).additionalParameters(anyString());
    }

    @Test
    void testBuild_WithApiKeyEnabled_NullApiKey() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn(null);
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey(null);
        verify(mockCxConfigBuilder).additionalParameters("");
    }

    @Test
    void testBuild_WithRefreshTokenEnabled_NullRefreshToken() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn(null);
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("--test");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey(null);
        verify(mockCxConfigBuilder).clientId("ide-integration");
        verify(mockCxConfigBuilder).additionalParameters("--test");
    }

    @Test
    void testBuild_WithNullAdditionalParameters() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("test-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn(null);

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("test-key");
        verify(mockCxConfigBuilder).additionalParameters(null);
    }

    @Test
    void testBuild_WithRefreshTokenEnabled_NotExpired() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("valid-refresh-token");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn("2099-12-31T23:59:59");
        when(mockGlobalSettingsSensitiveState.isTokenExpired(anyString())).thenReturn(false);
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("--debug");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("valid-refresh-token");
        verify(mockCxConfigBuilder).clientId("ide-integration");
        verify(mockCxConfigBuilder).additionalParameters("--debug");
        // Utils.notifySessionExpired() is called when credentials are expired
    }

    @Test
    void testBuild_WithRefreshTokenEnabled_NullExpiry() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("refresh-token");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn(null);
        when(mockGlobalSettingsSensitiveState.isTokenExpired(any())).thenReturn(false);
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("refresh-token");
        verify(mockCxConfigBuilder).clientId("ide-integration");
        verify(mockCxConfigBuilder).additionalParameters("");
        // Utils.notifySessionExpired() is called when credentials are expired
    }

    @Test
    void testBuild_WithRefreshTokenEnabled_EmptyExpiry() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("refresh-token");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn("");
        when(mockGlobalSettingsSensitiveState.isTokenExpired(any())).thenReturn(false);
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("refresh-token");
        verify(mockCxConfigBuilder).clientId("ide-integration");
        verify(mockCxConfigBuilder).additionalParameters("");
        // Utils.notifySessionExpired() is called when credentials are expired
    }

    @Test
    void testBuild_WithProvidedStates_ApiKeyEnabled() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("provided-api-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("--custom");

        CxWrapper result = CxWrapperFactory.build(mockGlobalSettingsState, mockGlobalSettingsSensitiveState);

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("provided-api-key");
        verify(mockCxConfigBuilder).additionalParameters("--custom");
        
        verify(mockGlobalSettingsState, never()).getRefreshTokenExpiry();
        verify(mockGlobalSettingsSensitiveState, never()).getRefreshToken();
        // Utils.notifySessionExpired() is called when credentials are expired
    }

    @Test
    void testBuild_WithProvidedStates_RefreshTokenEnabled() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("provided-refresh-token");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn("2099-12-31T23:59:59");
        when(mockGlobalSettingsSensitiveState.isTokenExpired(anyString())).thenReturn(false);
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build(mockGlobalSettingsState, mockGlobalSettingsSensitiveState);

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("provided-refresh-token");
        verify(mockCxConfigBuilder).clientId("ide-integration");
        verify(mockCxConfigBuilder).additionalParameters("");
        
        // Utils.notifySessionExpired() is called when credentials are expired
    }

    @Test
    void testBuild_WithProvidedStates_ExpiredRefreshToken() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("expired-token");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn("2020-01-01T00:00:00");
        when(mockGlobalSettingsSensitiveState.isTokenExpired(anyString())).thenReturn(true);

        CxWrapper result = CxWrapperFactory.build(mockGlobalSettingsState, mockGlobalSettingsSensitiveState);

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        // Utils.notifySessionExpired() is called when credentials are expired
        
        verify(mockCxConfigBuilder, never()).apiKey(anyString());
        verify(mockCxConfigBuilder, never()).clientId(anyString());
        verify(mockCxConfigBuilder, never()).additionalParameters(anyString());
    }

    @Test
    void testBuild_WithEmptyAdditionalParameters() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("test-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("test-key");
        verify(mockCxConfigBuilder).additionalParameters("");
    }

    @Test
    void testBuild_WithComplexAdditionalParameters() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("test-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("--debug --verbose --timeout=300");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("test-key");
        verify(mockCxConfigBuilder).additionalParameters("--debug --verbose --timeout=300");
    }

    @Test
    void testBuild_WithBlankApiKey() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("");
        verify(mockCxConfigBuilder).additionalParameters("");
    }

    @Test
    void testBuild_WithBlankRefreshToken() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn("2099-12-31T23:59:59");
        when(mockGlobalSettingsSensitiveState.isTokenExpired(anyString())).thenReturn(false);
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("");
        verify(mockCxConfigBuilder).clientId("ide-integration");
        verify(mockCxConfigBuilder).additionalParameters("");
    }

    @Test
    void testBuild_VerifyAgentNameAlwaysSet() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("test-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapperFactory.build();

        verify(mockCxConfigBuilder, times(1)).agentName("Jetbrains");
    }

    @Test
    void testBuild_VerifyClientIdOnlySetForRefreshToken() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("test-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapperFactory.build();

        verify(mockCxConfigBuilder, never()).clientId(anyString());

        reset(mockCxConfigBuilder);
        when(mockCxConfigBuilder.agentName(anyString())).thenReturn(mockCxConfigBuilder);
        when(mockCxConfigBuilder.apiKey(anyString())).thenReturn(mockCxConfigBuilder);
        when(mockCxConfigBuilder.additionalParameters(anyString())).thenReturn(mockCxConfigBuilder);
        when(mockCxConfigBuilder.build()).thenReturn(mockCxConfig);

        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(false);
        when(mockGlobalSettingsSensitiveState.getRefreshToken()).thenReturn("refresh-token");
        when(mockGlobalSettingsState.getRefreshTokenExpiry()).thenReturn("2099-12-31T23:59:59");
        when(mockGlobalSettingsSensitiveState.isTokenExpired(anyString())).thenReturn(false);

        CxWrapperFactory.build();

        verify(mockCxConfigBuilder, times(1)).clientId("ide-integration");
    }

    @Test
    void testBuild_EdgeCaseWithExpiredTokenAndApiKey() throws CxException, IOException {
        when(mockGlobalSettingsState.isApiKeyEnabled()).thenReturn(true);
        when(mockGlobalSettingsSensitiveState.getApiKey()).thenReturn("api-key");
        when(mockGlobalSettingsState.getAdditionalParameters()).thenReturn("");

        CxWrapper result = CxWrapperFactory.build();

        assertNotNull(result);
        
        verify(mockCxConfigBuilder).agentName("Jetbrains");
        verify(mockCxConfigBuilder).apiKey("api-key");
        verify(mockCxConfigBuilder).additionalParameters("");
        // Utils.notifySessionExpired() is called when credentials are expired
        
        verify(mockGlobalSettingsState, never()).getRefreshTokenExpiry();
        verify(mockGlobalSettingsSensitiveState, never()).isTokenExpired(anyString());
    }
}
