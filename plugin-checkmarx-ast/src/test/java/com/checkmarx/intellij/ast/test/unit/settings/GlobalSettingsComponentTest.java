package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.intellij.ast.settings.GlobalSettingsComponent;
import com.checkmarx.intellij.common.components.CxLinkLabel;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.common.utils.InputValidator;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import javax.swing.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for {@link GlobalSettingsComponent}.
 *
 * Uses sun.misc.Unsafe to bypass the constructor (which requires live IntelliJ services),
 * then injects mock dependencies and Swing UI stubs via reflection.
 *
 * Key patterns:
 * - ExpandableTextField is mocked (constructor calls ApplicationManager)
 * - GlobalSettingsSensitiveState construction is intercepted with mockConstruction
 * - messageBus is injected as a mock for tests that call apply()
 */
class GlobalSettingsComponentTest {

    private GlobalSettingsState mockState;
    private GlobalSettingsSensitiveState mockSensitiveState;
    private GlobalSettingsComponent component;

    // ---- Reflection helpers ----

    private static GlobalSettingsComponent newInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (GlobalSettingsComponent) unsafe.allocateInstance(GlobalSettingsComponent.class);
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes,
                                        Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Object getFieldValue(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    /** Returns a mock ExpandableTextField with getText() stubbed to the given value. */
    private static ExpandableTextField mockTextField(String text) {
        ExpandableTextField field = mock(ExpandableTextField.class);
        when(field.getText()).thenReturn(text);
        return field;
    }

    /**
     * Injects all fields required by apply() so it can run without NPE.
     * Also returns a try-with-resources handle that intercepts new GlobalSettingsSensitiveState().
     */
    private MockedConstruction<GlobalSettingsSensitiveState> injectApplyDependencies() throws Exception {
        setField(component, "additionalParametersField", mockTextField(""));
        setField(component, "apiKeyRadio", new JRadioButton());
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        setField(component, "validateResult", new JBLabel());
        setField(component, "apiKeyField", new JBPasswordField());

        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        setField(component, "messageBus", mockBus);

        return mockConstruction(GlobalSettingsSensitiveState.class);
    }

    // ---- Test lifecycle ----

    @BeforeEach
    void setUp() throws Exception {
        mockState = mock(GlobalSettingsState.class);
        mockSensitiveState = mock(GlobalSettingsSensitiveState.class);
        component = newInstanceWithoutConstructor();
        setStaticField(GlobalSettingsComponent.class, "SETTINGS_STATE", mockState);
        setStaticField(GlobalSettingsComponent.class, "SENSITIVE_SETTINGS_STATE", mockSensitiveState);
    }

    @AfterEach
    void tearDown() throws Exception {
        setStaticField(GlobalSettingsComponent.class, "SETTINGS_STATE", null);
        setStaticField(GlobalSettingsComponent.class, "SENSITIVE_SETTINGS_STATE", null);
    }

    // ===== isValid() =====

    @Test
    void isValid_WhenNotAuthenticated_ReturnsFalse() {
        when(mockState.isAuthenticated()).thenReturn(false);
        assertFalse(component.isValid());
    }

    @Test
    void isValid_WhenAuthenticatedBothCredsEmpty_SetsInvalidStateAndReturnsFalse() {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn("");
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        assertFalse(component.isValid());

        verify(mockState).setAuthenticated(false);
        verify(mockState).setLastValidationSuccess(false);
        verify(mockState).setValidationMessage("");
    }

    @Test
    void isValid_WhenAuthenticatedNullCreds_SetsInvalidStateAndReturnsFalse() {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn(null);
        when(mockSensitiveState.getRefreshToken()).thenReturn(null);

        assertFalse(component.isValid());

        verify(mockState).setAuthenticated(false);
    }

    @Test
    void isValid_WhenAuthenticatedWithExpiredOAuthToken_ReturnsFalse() {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn("");
        when(mockSensitiveState.getRefreshToken()).thenReturn("refresh-token");
        when(mockState.isApiKeyEnabled()).thenReturn(false);
        when(mockState.getRefreshTokenExpiry()).thenReturn("2020-01-01T00:00:00");
        when(mockSensitiveState.isTokenExpired("2020-01-01T00:00:00")).thenReturn(true);

        assertFalse(component.isValid());

        verify(mockState).setAuthenticated(false);
    }

    @Test
    void isValid_WhenAuthenticatedWithValidApiKey_ReturnsTrue() {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn("my-api-key");
        when(mockSensitiveState.getRefreshToken()).thenReturn("");
        when(mockSensitiveState.isValid(mockState)).thenReturn(true);

        assertTrue(component.isValid());
    }

    @Test
    void isValid_WhenAuthenticatedOAuthNotExpired_ReturnsTrue() {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn("");
        when(mockSensitiveState.getRefreshToken()).thenReturn("refresh-token");
        when(mockState.isApiKeyEnabled()).thenReturn(false);
        when(mockState.getRefreshTokenExpiry()).thenReturn("2099-01-01T00:00:00");
        when(mockSensitiveState.isTokenExpired("2099-01-01T00:00:00")).thenReturn(false);
        when(mockSensitiveState.isValid(mockState)).thenReturn(true);

        assertTrue(component.isValid());
    }

    // ===== isValidateTimeExpired() =====

    @Test
    void isValidateTimeExpired_WhenExpiryIsBlank_ReturnsFalse() throws Exception {
        when(mockState.getValidationExpiry()).thenReturn("");
        boolean result = (boolean) invokePrivate(component, "isValidateTimeExpired", new Class[]{});
        assertFalse(result);
    }

    @Test
    void isValidateTimeExpired_WhenExpiryIsNull_ReturnsFalse() throws Exception {
        when(mockState.getValidationExpiry()).thenReturn(null);
        boolean result = (boolean) invokePrivate(component, "isValidateTimeExpired", new Class[]{});
        assertFalse(result);
    }

    @Test
    void isValidateTimeExpired_WhenExpiryIsInPast_ReturnsTrue() throws Exception {
        String past = LocalDateTime.now().minusHours(1).toString();
        when(mockState.getValidationExpiry()).thenReturn(past);
        boolean result = (boolean) invokePrivate(component, "isValidateTimeExpired", new Class[]{});
        assertTrue(result);
    }

    @Test
    void isValidateTimeExpired_WhenExpiryIsInFuture_ReturnsFalse() throws Exception {
        String future = LocalDateTime.now().plusHours(1).toString();
        when(mockState.getValidationExpiry()).thenReturn(future);
        boolean result = (boolean) invokePrivate(component, "isValidateTimeExpired", new Class[]{});
        assertFalse(result);
    }

    // ===== validateBaseUrl() =====

    private void injectUrlValidationFields(String urlText) throws Exception {
        setField(component, "baseUrlField", new JTextField(urlText));
        setField(component, "connectButton", new JButton());
        setField(component, "validateResult", new JBLabel());
    }

    @Test
    void validateBaseUrl_WithEmptyUrl_ReturnsFalseAndDisablesConnect() throws Exception {
        injectUrlValidationFields("");
        boolean result = (boolean) invokePrivate(component, "validateBaseUrl", new Class[]{});
        assertFalse(result);
        assertFalse(((JButton) getFieldValue(component, "connectButton")).isEnabled());
    }

    @Test
    void validateBaseUrl_WithDoubleSlashSuffix_ReturnsFalseAndDisablesConnect() throws Exception {
        injectUrlValidationFields("https://example.com//");
        boolean result = (boolean) invokePrivate(component, "validateBaseUrl", new Class[]{});
        assertFalse(result);
        assertFalse(((JButton) getFieldValue(component, "connectButton")).isEnabled());
    }

    @Test
    void validateBaseUrl_WithInvalidUrlFormat_ReturnsFalseAndDisablesConnect() throws Exception {
        injectUrlValidationFields("not-a-url");
        try (MockedStatic<InputValidator> iv = mockStatic(InputValidator.class)) {
            iv.when(() -> InputValidator.isValidUrl("not-a-url")).thenReturn(false);
            boolean result = (boolean) invokePrivate(component, "validateBaseUrl", new Class[]{});
            assertFalse(result);
        }
    }

    @Test
    void validateBaseUrl_WithValidUrl_ReturnsTrue() throws Exception {
        injectUrlValidationFields("https://checkmarx.one");
        try (MockedStatic<InputValidator> iv = mockStatic(InputValidator.class)) {
            iv.when(() -> InputValidator.isValidUrl("https://checkmarx.one")).thenReturn(true);
            boolean result = (boolean) invokePrivate(component, "validateBaseUrl", new Class[]{});
            assertTrue(result);
        }
    }

    // ===== isModified() =====

    @Test
    void isModified_WhenAdditionalParamsDiffer_ReturnsTrue() throws Exception {
        setField(component, "additionalParametersField", mockTextField("--extra-param"));
        when(mockState.getAdditionalParameters()).thenReturn("");
        assertTrue(component.isModified());
    }

    @Test
    void isModified_WhenApiKeyRadioStateDiffers_ReturnsTrue() throws Exception {
        setField(component, "additionalParametersField", mockTextField("same"));
        JRadioButton apiKeyRadio = new JRadioButton();
        apiKeyRadio.setSelected(true);
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("key123");
        setField(component, "apiKeyRadio", apiKeyRadio);
        setField(component, "apiKeyField", apiKeyField);

        when(mockState.getAdditionalParameters()).thenReturn("same");
        when(mockState.isApiKeyEnabled()).thenReturn(false); // differs
        when(mockSensitiveState.getApiKey()).thenReturn("key123");

        assertTrue(component.isModified());
    }

    @Test
    void isModified_WhenApiKeyPasswordDiffers_ReturnsTrue() throws Exception {
        setField(component, "additionalParametersField", mockTextField("params"));
        JRadioButton apiKeyRadio = new JRadioButton();
        apiKeyRadio.setSelected(true);
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("new-key");
        setField(component, "apiKeyRadio", apiKeyRadio);
        setField(component, "apiKeyField", apiKeyField);

        when(mockState.getAdditionalParameters()).thenReturn("params");
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn("old-key");

        assertTrue(component.isModified());
    }

    @Test
    void isModified_WhenNothingChanged_ReturnsFalse() throws Exception {
        setField(component, "additionalParametersField", mockTextField("params"));
        JRadioButton apiKeyRadio = new JRadioButton();
        apiKeyRadio.setSelected(true);
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("my-key");
        setField(component, "apiKeyRadio", apiKeyRadio);
        setField(component, "apiKeyField", apiKeyField);

        when(mockState.getAdditionalParameters()).thenReturn("params");
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn("my-key");

        assertFalse(component.isModified());
    }

    // ===== updateConnectButtonState() =====

    @Test
    void updateConnectButtonState_OAuthWithValidInputs_EnablesButton() throws Exception {
        JRadioButton oauthRadio = new JRadioButton();
        oauthRadio.setSelected(true);
        JRadioButton apiKeyRadio = new JRadioButton();
        JTextField baseUrlField = new JTextField("https://checkmarx.one");
        JTextField tenantField = new JTextField("my-tenant");
        JBPasswordField apiKeyField = new JBPasswordField();
        JButton connectButton = new JButton();

        setField(component, "oauthRadio", oauthRadio);
        setField(component, "apiKeyRadio", apiKeyRadio);
        setField(component, "baseUrlField", baseUrlField);
        setField(component, "tenantField", tenantField);
        setField(component, "apiKeyField", apiKeyField);
        setField(component, "connectButton", connectButton);

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);

        try (MockedStatic<InputValidator> iv = mockStatic(InputValidator.class)) {
            iv.when(() -> InputValidator.isValidUrl("https://checkmarx.one")).thenReturn(true);
            invokePrivate(component, "updateConnectButtonState", new Class[]{});
            assertTrue(connectButton.isEnabled());
        }
    }

    @Test
    void updateConnectButtonState_WhenAlreadyAuthenticated_DisablesButton() throws Exception {
        JRadioButton oauthRadio = new JRadioButton();
        oauthRadio.setSelected(true);
        JTextField baseUrlField = new JTextField("https://checkmarx.one");
        JTextField tenantField = new JTextField("tenant");
        JButton connectButton = new JButton();

        setField(component, "oauthRadio", oauthRadio);
        setField(component, "apiKeyRadio", new JRadioButton());
        setField(component, "baseUrlField", baseUrlField);
        setField(component, "tenantField", tenantField);
        setField(component, "apiKeyField", new JBPasswordField());
        setField(component, "connectButton", connectButton);

        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isValidationInProgress()).thenReturn(false);

        try (MockedStatic<InputValidator> iv = mockStatic(InputValidator.class)) {
            iv.when(() -> InputValidator.isValidUrl(any())).thenReturn(true);
            invokePrivate(component, "updateConnectButtonState", new Class[]{});
            assertFalse(connectButton.isEnabled());
        }
    }

    @Test
    void updateConnectButtonState_WhenValidationInProgress_DisablesButton() throws Exception {
        JRadioButton oauthRadio = new JRadioButton();
        oauthRadio.setSelected(true);
        JButton connectButton = new JButton();

        setField(component, "oauthRadio", oauthRadio);
        setField(component, "apiKeyRadio", new JRadioButton());
        setField(component, "baseUrlField", new JTextField("https://checkmarx.one"));
        setField(component, "tenantField", new JTextField("tenant"));
        setField(component, "apiKeyField", new JBPasswordField());
        setField(component, "connectButton", connectButton);

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(true);

        try (MockedStatic<InputValidator> iv = mockStatic(InputValidator.class)) {
            iv.when(() -> InputValidator.isValidUrl(any())).thenReturn(true);
            invokePrivate(component, "updateConnectButtonState", new Class[]{});
            assertFalse(connectButton.isEnabled());
        }
    }

    @Test
    void updateConnectButtonState_ApiKeyRadioWithNonEmptyKey_EnablesButton() throws Exception {
        JRadioButton oauthRadio = new JRadioButton();
        JRadioButton apiKeyRadio = new JRadioButton();
        apiKeyRadio.setSelected(true);
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("secret-key");
        JButton connectButton = new JButton();

        setField(component, "oauthRadio", oauthRadio);
        setField(component, "apiKeyRadio", apiKeyRadio);
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        setField(component, "apiKeyField", apiKeyField);
        setField(component, "connectButton", connectButton);

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);

        invokePrivate(component, "updateConnectButtonState", new Class[]{});
        assertTrue(connectButton.isEnabled());
    }

    @Test
    void updateConnectButtonState_OAuthMissingTenant_DisablesButton() throws Exception {
        JRadioButton oauthRadio = new JRadioButton();
        oauthRadio.setSelected(true);
        JButton connectButton = new JButton();

        setField(component, "oauthRadio", oauthRadio);
        setField(component, "apiKeyRadio", new JRadioButton());
        setField(component, "baseUrlField", new JTextField("https://checkmarx.one"));
        setField(component, "tenantField", new JTextField("")); // empty
        setField(component, "apiKeyField", new JBPasswordField());
        setField(component, "connectButton", connectButton);

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);

        try (MockedStatic<InputValidator> iv = mockStatic(InputValidator.class)) {
            iv.when(() -> InputValidator.isValidUrl("https://checkmarx.one")).thenReturn(true);
            invokePrivate(component, "updateConnectButtonState", new Class[]{});
            assertFalse(connectButton.isEnabled());
        }
    }

    // ===== getStateFromFields() =====

    @Test
    void getStateFromFields_PreservesAllStateFieldsFromCurrentState() throws Exception {
        setField(component, "additionalParametersField", mockTextField("--timeout 10"));
        JRadioButton apiKeyRadio = new JRadioButton();
        apiKeyRadio.setSelected(true);
        setField(component, "apiKeyRadio", apiKeyRadio);

        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(true);
        when(mockState.getContainersTool()).thenReturn("podman");
        when(mockState.isWelcomeShown()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);

        GlobalSettingsState result = (GlobalSettingsState) invokePrivate(
                component, "getStateFromFields", new Class[]{});

        assertEquals("--timeout 10", result.getAdditionalParameters());
        assertTrue(result.isApiKeyEnabled());
        assertTrue(result.isAscaRealtime());
        assertFalse(result.isOssRealtime());
        assertTrue(result.isSecretDetectionRealtime());
        assertFalse(result.isContainersRealtime());
        assertTrue(result.isIacRealtime());
        assertEquals("podman", result.getContainersTool());
        assertTrue(result.isMcpEnabled());
        assertTrue(result.isMcpStatusChecked());
        assertTrue(result.getUserPreferencesSet());
        assertTrue(result.isDevAssistLicenseEnabled());
        assertFalse(result.isOneAssistLicenseEnabled());
    }

    @Test
    void getStateFromFields_WhenSettingsStateNull_ReturnsStateWithOnlyUIFields() throws Exception {
        setStaticField(GlobalSettingsComponent.class, "SETTINGS_STATE", null);
        setField(component, "additionalParametersField", mockTextField("--param"));
        setField(component, "apiKeyRadio", new JRadioButton());

        GlobalSettingsState result = (GlobalSettingsState) invokePrivate(
                component, "getStateFromFields", new Class[]{});

        assertEquals("--param", result.getAdditionalParameters());
        assertFalse(result.isApiKeyEnabled());
    }

    // ===== getSensitiveStateFromFields() =====

    @Test
    void getSensitiveStateFromFields_CopiesApiKeyFromPasswordFieldAndRefreshTokenFromState() throws Exception {
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("test-api-key");
        setField(component, "apiKeyField", apiKeyField);
        when(mockSensitiveState.getRefreshToken()).thenReturn("my-refresh-token");

        try (MockedConstruction<GlobalSettingsSensitiveState> construction =
                     mockConstruction(GlobalSettingsSensitiveState.class)) {

            invokePrivate(component, "getSensitiveStateFromFields", new Class[]{});

            assertEquals(1, construction.constructed().size());
            GlobalSettingsSensitiveState built = construction.constructed().get(0);
            verify(built).setApiKey("test-api-key");
            verify(built).setRefreshToken("my-refresh-token");
        }
    }

    // ===== autoEnableAllRealtimeScanners() =====

    @Test
    void autoEnableAllRealtimeScanners_WhenUserPreferencesSet_RestoresPreferences() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(true);
        when(mockSt.applyUserPreferencesToRealtimeSettings()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = injectApplyDependencies()) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            invokePrivate(component, "autoEnableAllRealtimeScanners", new Class[]{});
            verify(mockSt).applyUserPreferencesToRealtimeSettings();
        }
    }

    @Test
    void autoEnableAllRealtimeScanners_WhenNoPreferences_EnablesAllScanners() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(false);
        when(mockSt.isAscaRealtime()).thenReturn(false);
        when(mockSt.isOssRealtime()).thenReturn(false);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(false);
        when(mockSt.isContainersRealtime()).thenReturn(false);
        when(mockSt.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = injectApplyDependencies()) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            invokePrivate(component, "autoEnableAllRealtimeScanners", new Class[]{});

            verify(mockSt).setAscaRealtime(true);
            verify(mockSt).setOssRealtime(true);
            verify(mockSt).setSecretDetectionRealtime(true);
            verify(mockSt).setContainersRealtime(true);
            verify(mockSt).setIacRealtime(true);
            verify(mockSt).saveCurrentSettingsAsUserPreferences();
        }
    }

    @Test
    void autoEnableAllRealtimeScanners_WhenAllAlreadyEnabled_DoesNotEnableOrSavePreferences() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(false);
        when(mockSt.isAscaRealtime()).thenReturn(true);
        when(mockSt.isOssRealtime()).thenReturn(true);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(true);
        when(mockSt.isContainersRealtime()).thenReturn(true);
        when(mockSt.isIacRealtime()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            invokePrivate(component, "autoEnableAllRealtimeScanners", new Class[]{});
            verify(mockSt, never()).setAscaRealtime(anyBoolean());
            verify(mockSt, never()).saveCurrentSettingsAsUserPreferences();
        }
    }

    // ===== disableAllRealtimeScanners() =====

    @Test
    void disableAllRealtimeScanners_WhenScannersEnabled_DisablesAllAndPreservesPreferences() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(false);
        when(mockSt.isAscaRealtime()).thenReturn(true);
        when(mockSt.isOssRealtime()).thenReturn(true);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(false);
        when(mockSt.isContainersRealtime()).thenReturn(true);
        when(mockSt.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = injectApplyDependencies()) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            invokePrivate(component, "disableAllRealtimeScanners", new Class[]{});

            verify(mockSt).saveCurrentSettingsAsUserPreferences();
            verify(mockSt).setAscaRealtime(false);
            verify(mockSt).setOssRealtime(false);
            verify(mockSt).setContainersRealtime(false);
        }
    }

    @Test
    void disableAllRealtimeScanners_WhenAlreadyDisabled_SkipsPreservationAndApply() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(true); // already set
        when(mockSt.isAscaRealtime()).thenReturn(false);
        when(mockSt.isOssRealtime()).thenReturn(false);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(false);
        when(mockSt.isContainersRealtime()).thenReturn(false);
        when(mockSt.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            invokePrivate(component, "disableAllRealtimeScanners", new Class[]{});
            verify(mockSt, never()).saveCurrentSettingsAsUserPreferences();
            verify(mockSt, never()).setAscaRealtime(anyBoolean());
        }
    }

    @Test
    void disableAllRealtimeScanners_WhenSecretAndIacEnabled_DisablesThemAndCallsApply() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(true);
        when(mockSt.isAscaRealtime()).thenReturn(false);
        when(mockSt.isOssRealtime()).thenReturn(false);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(true);
        when(mockSt.isContainersRealtime()).thenReturn(false);
        when(mockSt.isIacRealtime()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = injectApplyDependencies()) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            invokePrivate(component, "disableAllRealtimeScanners", new Class[]{});

            verify(mockSt).setSecretDetectionRealtime(false);
            verify(mockSt).setIacRealtime(false);
            verify(mockSt, never()).saveCurrentSettingsAsUserPreferences();
        }
    }

    // ===== setValidationResult(String, JBColor) =====

    @Test
    void setValidationResult_WithMessage_SetsLabelTextWithHtmlAndColor() throws Exception {
        JBLabel validateResult = new JBLabel();
        setField(component, "validateResult", validateResult);

        invokePrivate(component, "setValidationResult",
                new Class[]{String.class, JBColor.class},
                "Connection verified", JBColor.GREEN);

        assertEquals("<html>Connection verified</html>", validateResult.getText());
    }

    // ===== apply() =====

    @Test
    void apply_WhenCalled_PersistsStateAndPublishesSettings() throws Exception {
        try (MockedConstruction<GlobalSettingsSensitiveState> construction = injectApplyDependencies()) {
            when(mockState.isAuthenticated()).thenReturn(true);
            when(mockState.isValidationInProgress()).thenReturn(false);
            when(mockState.getRefreshTokenExpiry()).thenReturn("2099-01-01T00:00:00");
            when(mockState.getValidationExpiry()).thenReturn("2099-01-01T00:00:00");
            when(mockState.getValidationMessage()).thenReturn("Connected");
            when(mockState.isLastValidationSuccess()).thenReturn(true);
            when(mockState.getBaseUrl()).thenReturn("https://checkmarx.one");
            when(mockState.getTenant()).thenReturn("my-tenant");
            when(mockState.isAscaRealtime()).thenReturn(true);
            when(mockState.isOssRealtime()).thenReturn(false);
            when(mockState.isSecretDetectionRealtime()).thenReturn(false);
            when(mockState.isContainersRealtime()).thenReturn(false);
            when(mockState.isIacRealtime()).thenReturn(false);
            when(mockState.getContainersTool()).thenReturn("docker");
            when(mockState.isMcpEnabled()).thenReturn(false);
            when(mockState.isMcpStatusChecked()).thenReturn(false);
            when(mockState.getUserPreferencesSet()).thenReturn(false);
            when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
            when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
            when(mockState.isWelcomeShown()).thenReturn(false);
            when(mockSensitiveState.getRefreshToken()).thenReturn("refresh-token");

            assertDoesNotThrow(() -> component.apply());

            verify(mockState).apply(any(GlobalSettingsState.class));
        }
    }

    @Test
    void apply_WhenBaseUrlFieldHasValue_UsesFieldValueNotState() throws Exception {
        try (MockedConstruction<GlobalSettingsSensitiveState> construction = injectApplyDependencies()) {
            JTextField baseUrl = new JTextField("https://new-url.com");
            JTextField tenant = new JTextField("new-tenant");
            setField(component, "baseUrlField", baseUrl);
            setField(component, "tenantField", tenant);

            when(mockState.isAuthenticated()).thenReturn(false);
            when(mockState.isValidationInProgress()).thenReturn(false);
            when(mockState.getRefreshTokenExpiry()).thenReturn("");
            when(mockState.getValidationExpiry()).thenReturn("");
            when(mockState.getValidationMessage()).thenReturn("");
            when(mockState.isLastValidationSuccess()).thenReturn(false);
            when(mockState.getBaseUrl()).thenReturn("https://old-url.com");
            when(mockState.getTenant()).thenReturn("old-tenant");
            when(mockState.isAscaRealtime()).thenReturn(false);
            when(mockState.isOssRealtime()).thenReturn(false);
            when(mockState.isSecretDetectionRealtime()).thenReturn(false);
            when(mockState.isContainersRealtime()).thenReturn(false);
            when(mockState.isIacRealtime()).thenReturn(false);
            when(mockState.getContainersTool()).thenReturn("docker");
            when(mockState.isMcpEnabled()).thenReturn(false);
            when(mockState.isMcpStatusChecked()).thenReturn(false);
            when(mockState.getUserPreferencesSet()).thenReturn(false);
            when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
            when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
            when(mockState.isWelcomeShown()).thenReturn(false);
            when(mockSensitiveState.getRefreshToken()).thenReturn("");

            assertDoesNotThrow(() -> component.apply());

            verify(mockState).apply(argThat(s ->
                    "https://new-url.com".equals(s.getBaseUrl()) && "new-tenant".equals(s.getTenant())));
        }
    }

    // ===== getMainPanel() =====

    @Test
    void getMainPanel_ReturnsNullWhenConstructorNotCalled() {
        assertNull(component.getMainPanel());
    }

    // ===== updateConnectButtonState() — API key radio with empty password =====

    @Test
    void updateConnectButtonState_ApiKeyRadioWithEmptyKey_DisablesButton() throws Exception {
        JRadioButton oauthRadio = new JRadioButton();
        JRadioButton apiKeyRadio = new JRadioButton();
        apiKeyRadio.setSelected(true);
        JBPasswordField apiKeyField = new JBPasswordField();  // empty
        JButton connectButton = new JButton();

        setField(component, "oauthRadio", oauthRadio);
        setField(component, "apiKeyRadio", apiKeyRadio);
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        setField(component, "apiKeyField", apiKeyField);
        setField(component, "connectButton", connectButton);

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);

        invokePrivate(component, "updateConnectButtonState", new Class[]{});
        assertFalse(connectButton.isEnabled());
    }

    // ===== setValidationResult() — zero-arg private method =====

    @Test
    void setValidationResult_ZeroArg_WhenValidationInProgress_ShowsInProgressLabel() throws Exception {
        when(mockState.isValidationInProgress()).thenReturn(true);
        JBLabel validateResult = new JBLabel();
        setField(component, "validateResult", validateResult);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.VALIDATE_IN_PROGRESS)).thenReturn("Validating...");
            invokePrivate(component, "setValidationResult", new Class[]{});
        }

        assertTrue(validateResult.isVisible());
        assertEquals("<html>Validating...</html>", validateResult.getText());
    }

    @Test
    void setValidationResult_ZeroArg_WhenSuccessMessage_ShowsGreenLabel() throws Exception {
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(true);
        when(mockState.getValidationMessage()).thenReturn("Connected successfully");
        JBLabel validateResult = new JBLabel();
        setField(component, "validateResult", validateResult);

        invokePrivate(component, "setValidationResult", new Class[]{});

        assertTrue(validateResult.isVisible());
        assertEquals("<html>Connected successfully</html>", validateResult.getText());
    }

    @Test
    void setValidationResult_ZeroArg_WhenErrorMessage_ShowsRedLabel() throws Exception {
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(false);
        when(mockState.getValidationMessage()).thenReturn("Authentication failed");
        JBLabel validateResult = new JBLabel();
        setField(component, "validateResult", validateResult);

        invokePrivate(component, "setValidationResult", new Class[]{});

        assertTrue(validateResult.isVisible());
        assertEquals("<html>Authentication failed</html>", validateResult.getText());
    }

    @Test
    void setValidationResult_ZeroArg_WhenBlankMessage_HidesLabel() throws Exception {
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(false);
        when(mockState.getValidationMessage()).thenReturn("");
        JBLabel validateResult = new JBLabel();
        validateResult.setVisible(true);
        setField(component, "validateResult", validateResult);

        invokePrivate(component, "setValidationResult", new Class[]{});

        assertFalse(validateResult.isVisible());
    }

    // ===== isModified() — additional branches =====

    @Test
    void isModified_WhenNullStatesAreReinitialized_DoesNotThrow() throws Exception {
        setStaticField(GlobalSettingsComponent.class, "SETTINGS_STATE", null);
        setStaticField(GlobalSettingsComponent.class, "SENSITIVE_SETTINGS_STATE", null);

        // re-inject fresh mocks after clearing
        GlobalSettingsState freshState = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState freshSensitive = mock(GlobalSettingsSensitiveState.class);

        try (MockedStatic<GlobalSettingsState> gsMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssMock = mockStatic(GlobalSettingsSensitiveState.class)) {
            gsMock.when(GlobalSettingsState::getInstance).thenReturn(freshState);
            gssMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(freshSensitive);

            JRadioButton apiKeyRadio = new JRadioButton();
            apiKeyRadio.setSelected(false);
            JBPasswordField apiKeyField = new JBPasswordField();
            apiKeyField.setText("");
            setField(component, "additionalParametersField", mockTextField(""));
            setField(component, "apiKeyRadio", apiKeyRadio);
            setField(component, "apiKeyField", apiKeyField);

            when(freshState.getAdditionalParameters()).thenReturn("");
            when(freshState.isApiKeyEnabled()).thenReturn(false);
            when(freshSensitive.getApiKey()).thenReturn("");

            assertFalse(component.isModified());
        }
    }

    // ===== getMainPanel() — non-null after injection =====

    @Test
    void getMainPanel_WhenPanelInjected_ReturnsInjectedPanel() throws Exception {
        JPanel panel = new JPanel();
        setField(component, "mainPanel", panel);
        assertSame(panel, component.getMainPanel());
    }

    // ===== shouldShowAssistLink() =====

    @Test
    void shouldShowAssistLink_WhenNotAuthenticated_ReturnsFalse() throws Exception {
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
        boolean result = (boolean) invokePrivate(component, "shouldShowAssistLink", new Class[]{});
        assertFalse(result);
    }

    @Test
    void shouldShowAssistLink_WhenAuthenticatedNoLicense_ReturnsFalse() throws Exception {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        boolean result = (boolean) invokePrivate(component, "shouldShowAssistLink", new Class[]{});
        assertFalse(result);
    }

    @Test
    void shouldShowAssistLink_WhenAuthenticatedWithOneAssistLicense_ReturnsTrue() throws Exception {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        boolean result = (boolean) invokePrivate(component, "shouldShowAssistLink", new Class[]{});
        assertTrue(result);
    }

    @Test
    void shouldShowAssistLink_WhenAuthenticatedWithDevAssistLicense_ReturnsTrue() throws Exception {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
        boolean result = (boolean) invokePrivate(component, "shouldShowAssistLink", new Class[]{});
        assertTrue(result);
    }

    // ===== setInvalidAuthState() =====

    @Test
    void setInvalidAuthState_SetsValidationMessageAndAuthFalse() throws Exception {
        invokePrivate(component, "setInvalidAuthState",
                new Class[]{String.class}, "Session expired");
        verify(mockState).setValidationMessage("Session expired");
        verify(mockState).setLastValidationSuccess(false);
        verify(mockState).setAuthenticated(false);
    }

    // ===== updateAssistLinkVisibility() =====

    @Test
    void updateAssistLinkVisibility_WhenAssistLinkNull_DoesNotThrow() throws Exception {
        setField(component, "assistLink", null);
        assertDoesNotThrow(() ->
                invokePrivate(component, "updateAssistLinkVisibility", new Class[]{}));
    }

    @Test
    void updateAssistLinkVisibility_WhenAssistLinkNotNull_AndShouldShow_ShowsAndEnablesLink() throws Exception {
        CxLinkLabel mockLink = mock(CxLinkLabel.class);
        JPanel mainPanel = new JPanel();
        setField(component, "assistLink", mockLink);
        setField(component, "mainPanel", mainPanel);

        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);

        invokePrivate(component, "updateAssistLinkVisibility", new Class[]{});

        verify(mockLink).setVisible(true);
        verify(mockLink).setEnabled(true);
    }

    @Test
    void updateAssistLinkVisibility_WhenAssistLinkNotNull_AndShouldNotShow_HidesLink() throws Exception {
        CxLinkLabel mockLink = mock(CxLinkLabel.class);
        JPanel mainPanel = new JPanel();
        setField(component, "assistLink", mockLink);
        setField(component, "mainPanel", mainPanel);

        when(mockState.isAuthenticated()).thenReturn(false);

        invokePrivate(component, "updateAssistLinkVisibility", new Class[]{});

        verify(mockLink).setVisible(false);
        verify(mockLink).setEnabled(false);
    }

    // ===== autoEnableAllRealtimeScanners() — userPreferences set but not applied =====

    @Test
    void autoEnableAllRealtimeScanners_WhenPreferencesSetButNotChanged_JustReturns() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(true);
        when(mockSt.applyUserPreferencesToRealtimeSettings()).thenReturn(false); // no change

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            invokePrivate(component, "autoEnableAllRealtimeScanners", new Class[]{});
        }

        // Should return early without enabling scanners
        verify(mockSt, never()).setAscaRealtime(anyBoolean());
    }

    // ===== setInputFields() — private =====

    @Test
    void setInputFields_SetsAllFieldTextsFromState() throws Exception {
        ExpandableTextField additionalField = mock(ExpandableTextField.class);
        setField(component, "additionalParametersField", additionalField);
        JBPasswordField apiField = new JBPasswordField();
        setField(component, "apiKeyField", apiField);
        JTextField baseUrl = new JTextField();
        setField(component, "baseUrlField", baseUrl);
        JTextField tenant = new JTextField();
        setField(component, "tenantField", tenant);

        when(mockState.getAdditionalParameters()).thenReturn("--debug");
        when(mockState.getBaseUrl()).thenReturn("https://example.com");
        when(mockState.getTenant()).thenReturn("my-tenant");
        when(mockSensitiveState.getApiKey()).thenReturn("my-key");

        invokePrivate(component, "setInputFields", new Class[]{});

        verify(additionalField).setText("--debug");
        assertEquals("my-key", new String(apiField.getPassword()));
        assertEquals("https://example.com", baseUrl.getText());
        assertEquals("my-tenant", tenant.getText());
    }

    // ===== setFieldsEditable() — private =====

    private void injectRadioAndLabels(JRadioButton oauthR, JRadioButton apiKeyR) throws Exception {
        setField(component, "oauthRadio", oauthR);
        setField(component, "apiKeyRadio", apiKeyR);
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        JBPasswordField apiField = new JBPasswordField();
        setField(component, "apiKeyField", apiField);
        setField(component, "oauthLabel", new JBLabel());
        setField(component, "baseUrlLabel", new JBLabel());
        setField(component, "tenantLabel", new JBLabel());
    }

    @Test
    void setFieldsEditable_WhenTrue_EnablesRadioButtons() throws Exception {
        JRadioButton oauthR = new JRadioButton();
        oauthR.setSelected(true);
        JRadioButton apiKeyR = new JRadioButton();
        injectRadioAndLabels(oauthR, apiKeyR);

        invokePrivate(component, "setFieldsEditable", new Class[]{boolean.class}, true);

        assertTrue(oauthR.isEnabled());
        assertTrue(apiKeyR.isEnabled());
    }

    @Test
    void setFieldsEditable_WhenFalse_DisablesRadioButtons() throws Exception {
        JRadioButton oauthR = new JRadioButton();
        JRadioButton apiKeyR = new JRadioButton();
        oauthR.setEnabled(true);
        apiKeyR.setEnabled(true);
        injectRadioAndLabels(oauthR, apiKeyR);

        invokePrivate(component, "setFieldsEditable", new Class[]{boolean.class}, false);

        assertFalse(oauthR.isEnabled());
        assertFalse(apiKeyR.isEnabled());
    }

    // ===== getValidationExpiry() — private =====

    @Test
    void getValidationExpiry_ReturnsNonBlankFutureTimestamp() throws Exception {
        String result = (String) invokePrivate(component, "getValidationExpiry", new Class[]{});
        assertNotNull(result);
        assertFalse(result.isBlank());
        // Should be a parseable LocalDateTime
        assertDoesNotThrow(() -> java.time.LocalDateTime.parse(result));
    }

    // ===== updateFieldLabels() — private =====

    @Test
    void updateFieldLabels_WhenOAuthSelected_SetsRequiredMarks() throws Exception {
        JRadioButton oauthR = new JRadioButton();
        oauthR.setSelected(true);
        JRadioButton apiKeyR = new JRadioButton();
        JBLabel baseUrlLabel = new JBLabel();
        JBLabel tenantLabel = new JBLabel();
        setField(component, "oauthRadio", oauthR);
        setField(component, "apiKeyRadio", apiKeyR);
        setField(component, "baseUrlLabel", baseUrlLabel);
        setField(component, "tenantLabel", tenantLabel);

        invokePrivate(component, "updateFieldLabels", new Class[]{});

        assertTrue(baseUrlLabel.getText().contains("Checkmarx One Base URL"));
        assertTrue(tenantLabel.getText().contains("Tenant Name"));
    }

    @Test
    void updateFieldLabels_WhenApiKeySelected_SetsApiKeyRequired() throws Exception {
        JRadioButton oauthR = new JRadioButton();
        oauthR.setSelected(false);
        JRadioButton apiKeyR = new JRadioButton();
        JBLabel baseUrlLabel = new JBLabel();
        JBLabel tenantLabel = new JBLabel();
        setField(component, "oauthRadio", oauthR);
        setField(component, "apiKeyRadio", apiKeyR);
        setField(component, "baseUrlLabel", baseUrlLabel);
        setField(component, "tenantLabel", tenantLabel);

        invokePrivate(component, "updateFieldLabels", new Class[]{});

        assertTrue(baseUrlLabel.getText().contains("Checkmarx One Base URL"));
    }

    // ===== reset() =====

    private void injectAllResetDependencies() throws Exception {
        // All UI components needed by reset() and its callees
        ExpandableTextField additionalField = mock(ExpandableTextField.class);
        when(additionalField.getText()).thenReturn("");
        setField(component, "additionalParametersField", additionalField);

        JBPasswordField apiField = new JBPasswordField();
        apiField.setText("k");
        setField(component, "apiKeyField", apiField);
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());

        JRadioButton oauthR = new JRadioButton();
        JRadioButton apiKeyR = new JRadioButton();
        setField(component, "oauthRadio", oauthR);
        setField(component, "apiKeyRadio", apiKeyR);
        setField(component, "oauthLabel", new JBLabel());
        setField(component, "baseUrlLabel", new JBLabel());
        setField(component, "tenantLabel", new JBLabel());

        JButton connectBtn = new JButton();
        setField(component, "connectButton", connectBtn);
        JButton logoutBtn = new JButton();
        setField(component, "logoutButton", logoutBtn);
        setField(component, "validateResult", new JBLabel());
    }

    @Test
    void reset_WhenValidatingAndNotExpired_LocksConnectAndLogoutButtons() throws Exception {
        injectAllResetDependencies();

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.getValidationExpiry()).thenReturn(""); // not expired
        when(mockState.getAdditionalParameters()).thenReturn("");
        when(mockSensitiveState.getApiKey()).thenReturn("k");

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(any())).thenReturn("in progress");
            component.reset();
        }

        JButton connectBtn = (JButton) getFieldValue(component, "connectButton");
        JButton logoutBtn = (JButton) getFieldValue(component, "logoutButton");
        assertFalse(connectBtn.isEnabled());
        assertFalse(logoutBtn.isEnabled());
    }

    @Test
    void reset_WhenAuthenticated_EnablesLogoutButton() throws Exception {
        injectAllResetDependencies();

        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(true);
        when(mockState.getValidationMessage()).thenReturn("OK");
        when(mockState.getAdditionalParameters()).thenReturn("");
        when(mockState.getBaseUrl()).thenReturn("https://cx.com");
        when(mockState.getTenant()).thenReturn("t");
        when(mockSensitiveState.getApiKey()).thenReturn("k");
        when(mockSensitiveState.getRefreshToken()).thenReturn("rt");
        when(mockSensitiveState.isValid(mockState)).thenReturn(true);

        component.reset();

        JButton logoutBtn = (JButton) getFieldValue(component, "logoutButton");
        assertTrue(logoutBtn.isEnabled());
        JRadioButton apiKeyR = (JRadioButton) getFieldValue(component, "apiKeyRadio");
        assertTrue(apiKeyR.isSelected());
    }

    // ===== setLogoutState() — private =====

    @Test
    void setLogoutState_ClearsAuthLicenseFlagsAndEnablesConnect() throws Exception {
        injectAllResetDependencies();
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        setField(component, "messageBus", mockBus);

        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isWelcomeShown()).thenReturn(false);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");
        when(mockState.isValidationInProgress()).thenReturn(false);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored =
                     mockConstruction(GlobalSettingsSensitiveState.class)) {
            bundleMock.when(() -> Bundle.message(any())).thenReturn("Logged out");
            invokePrivate(component, "setLogoutState", new Class[]{});
        }

        verify(mockState).setAuthenticated(false);
        verify(mockState).setDevAssistLicenseEnabled(false);
        verify(mockState).setOneAssistLicenseEnabled(false);
        verify(mockState).setLastValidationSuccess(true);
        JButton logoutBtn = (JButton) getFieldValue(component, "logoutButton");
        assertFalse(logoutBtn.isEnabled());
    }

    // ===== setSessionExpired() — private =====

    @Test
    void setSessionExpired_ClearsAuthAndMcpFlags() throws Exception {
        injectAllResetDependencies();
        // Additional fields needed by apply()
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        setField(component, "messageBus", mockBus);

        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isWelcomeShown()).thenReturn(false);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");
        when(mockState.isValidationInProgress()).thenReturn(false);

        try (MockedConstruction<GlobalSettingsSensitiveState> ignored =
                mockConstruction(GlobalSettingsSensitiveState.class)) {
            invokePrivate(component, "setSessionExpired", new Class[]{});
        }

        verify(mockState).setAuthenticated(false);
        verify(mockState).setMcpEnabled(false);
        verify(mockState).setMcpStatusChecked(false);
        verify(mockState).setDevAssistLicenseEnabled(false);
        verify(mockState).setOneAssistLicenseEnabled(false);
    }

    // ===== reset() — branch 2: not authenticated AND not validating =====

    @Test
    void reset_WhenNotAuthenticatedAndNotValidating_TriggersSessionExpiredPath() throws Exception {
        injectAllResetDependencies();
        setField(component, "assistLink", null);

        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        setField(component, "messageBus", mockBus);

        // isValid() → false (not authenticated)
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockSensitiveState.getApiKey()).thenReturn("");
        when(mockSensitiveState.getRefreshToken()).thenReturn("");
        // isValidationInProgress = false → falls into second branch of reset()
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.getValidationExpiry()).thenReturn("");
        when(mockState.getAdditionalParameters()).thenReturn("");
        when(mockState.getValidationMessage()).thenReturn("");
        when(mockState.isLastValidationSuccess()).thenReturn(false);

        // Fields needed by setSessionExpired() → apply()
        when(mockState.getRefreshTokenExpiry()).thenReturn("");
        when(mockState.getBaseUrl()).thenReturn("");
        when(mockState.getTenant()).thenReturn("");
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isWelcomeShown()).thenReturn(false);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored =
                     mockConstruction(GlobalSettingsSensitiveState.class)) {
            bundleMock.when(() -> Bundle.message(any())).thenReturn("msg");
            component.reset();
        }

        // setSessionExpired() sets these
        verify(mockState).setMcpEnabled(false);
        verify(mockState).setMcpStatusChecked(false);
        verify(mockState).setDevAssistLicenseEnabled(false);
        verify(mockState).setOneAssistLicenseEnabled(false);
        // reset() branch 2 sets this before calling setSessionExpired
        verify(mockState).setValidationInProgress(false);
    }

    // ===== fetchAndStoreLicenseStatus() =====

    @Test
    void fetchAndStoreLicenseStatus_WhenApiReturnsFlags_PersistsLicenseStatus() throws Exception {
        setField(component, "additionalParametersField", mockTextField(""));
        setField(component, "apiKeyRadio", new JRadioButton());
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        setField(component, "apiKeyField", new JBPasswordField());

        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isWelcomeShown()).thenReturn(false);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        java.util.Map<String, String> tenantMap = new java.util.HashMap<>();
        tenantMap.put(com.checkmarx.intellij.common.commands.TenantSetting.KEY_DEV_ASSIST, "true");
        tenantMap.put(com.checkmarx.intellij.common.commands.TenantSetting.KEY_ONE_ASSIST, "false");

        try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                     mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored =
                     mockConstruction(GlobalSettingsSensitiveState.class)) {
            tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.getTenantSettingsMap(any(), any()))
                  .thenReturn(tenantMap);
            invokePrivate(component, "fetchAndStoreLicenseStatus", new Class[]{});
        }

        verify(mockState, atLeastOnce()).setDevAssistLicenseEnabled(true);
        verify(mockState, atLeastOnce()).setOneAssistLicenseEnabled(false);
    }

    @Test
    void fetchAndStoreLicenseStatus_WhenApiThrows_ClearsFlagsAndDoesNotPropagate() throws Exception {
        setField(component, "additionalParametersField", mockTextField(""));
        setField(component, "apiKeyRadio", new JRadioButton());
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        setField(component, "apiKeyField", new JBPasswordField());

        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isWelcomeShown()).thenReturn(false);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                     mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored =
                     mockConstruction(GlobalSettingsSensitiveState.class)) {
            tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.getTenantSettingsMap(any(), any()))
                  .thenThrow(new RuntimeException("network error"));
            assertDoesNotThrow(() -> invokePrivate(component, "fetchAndStoreLicenseStatus", new Class[]{}));
        }

        // Flags should be cleared before the API call (fail-safe)
        verify(mockState).setDevAssistLicenseEnabled(false);
        verify(mockState).setOneAssistLicenseEnabled(false);
    }

    // ===== closeSettingsDialog() =====

    @Test
    void closeSettingsDialog_WhenMainPanelHasNoAncestor_DoesNotThrow() throws Exception {
        setField(component, "mainPanel", new JPanel());
        assertDoesNotThrow(() -> invokePrivate(component, "closeSettingsDialog", new Class[]{}));
    }

    // ===== addSectionHeader() / validatePanel() =====

    @Test
    void validatePanel_WhenLayoutIsNotMigLayout_ThrowsIllegalArgumentException() throws Exception {
        JPanel panel = new JPanel(new java.awt.FlowLayout());
        setField(component, "mainPanel", panel);
        Exception ex = assertThrows(Exception.class,
                () -> invokePrivate(component, "validatePanel", new Class[]{}));
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(cause instanceof IllegalArgumentException,
                "Expected IllegalArgumentException but got: " + cause.getClass());
    }

    @Test
    void validatePanel_WhenLayoutIsMigLayout_DoesNotThrow() throws Exception {
        JPanel panel = new JPanel(new net.miginfocom.swing.MigLayout());
        setField(component, "mainPanel", panel);
        assertDoesNotThrow(() -> invokePrivate(component, "validatePanel", new Class[]{}));
    }

    // ===== isModified() — SETTINGS_STATE null re-initialisation =====

    @Test
    void reset_WhenSettingsStateIsNull_ReinitializesFromSingleton() throws Exception {
        setStaticField(GlobalSettingsComponent.class, "SETTINGS_STATE", null);
        setStaticField(GlobalSettingsComponent.class, "SENSITIVE_SETTINGS_STATE", null);

        GlobalSettingsState freshState = mock(GlobalSettingsState.class);
        GlobalSettingsSensitiveState freshSensitive = mock(GlobalSettingsSensitiveState.class);

        try (MockedStatic<GlobalSettingsState> gsMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> gssMock = mockStatic(GlobalSettingsSensitiveState.class)) {
            gsMock.when(GlobalSettingsState::getInstance).thenReturn(freshState);
            gssMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(freshSensitive);

            // verify isModified() re-initialises — use minimal stubs
            JRadioButton radio = new JRadioButton();
            JBPasswordField pwd = new JBPasswordField();
            setField(component, "additionalParametersField", mockTextField(""));
            setField(component, "apiKeyRadio", radio);
            setField(component, "apiKeyField", pwd);
            when(freshState.getAdditionalParameters()).thenReturn("");
            when(freshState.isApiKeyEnabled()).thenReturn(false);
            when(freshSensitive.getApiKey()).thenReturn("");

            assertFalse(component.isModified());
        }

        // Verify the static fields are repopulated
        assertNotNull(getFieldValue(component, "SETTINGS_STATE"));
    }

    // ===== completeAuthenticationSetup() =====

    /**
     * Helper: injects apply() dependencies into component and sets up mockState stubs for
     * completeAuthenticationSetup(). Returns a fresh GlobalSettingsState mock that will be
     * returned by GlobalSettingsState.getInstance() (used by autoEnable/disableAllRealtimeScanners).
     *
     * @param mcpPreviouslyChecked   value returned by SETTINGS_STATE.isMcpStatusChecked()
     * @param previousMcpEnabled     value returned by SETTINGS_STATE.isMcpEnabled()
     * @param userPrefsSet           value returned by getInstance().getUserPreferencesSet()
     */
    private GlobalSettingsState setupCompleteAuthDependencies(boolean mcpPreviouslyChecked,
                                                               boolean previousMcpEnabled,
                                                               boolean userPrefsSet) throws Exception {
        setField(component, "additionalParametersField", mockTextField(""));
        setField(component, "apiKeyRadio", new JRadioButton());
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        setField(component, "validateResult", new JBLabel());
        setField(component, "apiKeyField", new JBPasswordField());
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        setField(component, "messageBus", mockBus);

        when(mockState.isMcpStatusChecked()).thenReturn(mcpPreviouslyChecked);
        when(mockState.isMcpEnabled()).thenReturn(previousMcpEnabled);
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.getAdditionalParameters()).thenReturn("");
        when(mockState.getBaseUrl()).thenReturn("");
        when(mockState.getTenant()).thenReturn("");
        when(mockState.getRefreshTokenExpiry()).thenReturn("");
        when(mockState.getValidationExpiry()).thenReturn("");
        when(mockState.getValidationMessage()).thenReturn("");
        when(mockState.isLastValidationSuccess()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isWelcomeShown()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(userPrefsSet);
        when(mockSt.isAscaRealtime()).thenReturn(false);
        when(mockSt.isOssRealtime()).thenReturn(false);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(false);
        when(mockSt.isContainersRealtime()).thenReturn(false);
        when(mockSt.isIacRealtime()).thenReturn(false);
        return mockSt;
    }

    @Test
    void completeAuthenticationSetup_WhenFirstCheckAndMcpDisabled_SetsStatusChecked() throws Exception {
        GlobalSettingsState mockSt = setupCompleteAuthDependencies(false, false, true);
        com.checkmarx.intellij.ast.service.StateService mockStateService =
                mock(com.checkmarx.intellij.ast.service.StateService.class);

        try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                     mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class);
             MockedStatic<GlobalSettingsState> gsMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.ast.service.StateService> ssMock =
                     mockStatic(com.checkmarx.intellij.ast.service.StateService.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class);
             MockedConstruction<com.checkmarx.intellij.ast.ui.WelcomeDialog> wdMock =
                     mockConstruction(com.checkmarx.intellij.ast.ui.WelcomeDialog.class)) {

            tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(any(), any()))
                  .thenReturn(false);
            gsMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            ssMock.when(com.checkmarx.intellij.ast.service.StateService::getInstance).thenReturn(mockStateService);
            doNothing().when(mockStateService).pruneStaleCustomStates();

            invokePrivate(component, "completeAuthenticationSetup", new Class[]{String.class}, "api-key");
        }

        verify(mockState).setMcpEnabled(false);
        verify(mockState).setMcpStatusChecked(true);
    }

    @Test
    void completeAuthenticationSetup_WhenFirstCheckAndMcpEnabled_EnablesAllScanners() throws Exception {
        GlobalSettingsState mockSt = setupCompleteAuthDependencies(false, false, false);
        com.checkmarx.intellij.ast.service.StateService mockStateService =
                mock(com.checkmarx.intellij.ast.service.StateService.class);

        try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                     mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class);
             MockedStatic<GlobalSettingsState> gsMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.ast.service.StateService> ssMock =
                     mockStatic(com.checkmarx.intellij.ast.service.StateService.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class);
             MockedConstruction<com.checkmarx.intellij.ast.ui.WelcomeDialog> wdMock =
                     mockConstruction(com.checkmarx.intellij.ast.ui.WelcomeDialog.class)) {

            tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(any(), any()))
                  .thenReturn(true);
            gsMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            ssMock.when(com.checkmarx.intellij.ast.service.StateService::getInstance).thenReturn(mockStateService);
            doNothing().when(mockStateService).pruneStaleCustomStates();
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.installForCopilot(any()))
                   .thenReturn(Boolean.FALSE);

            invokePrivate(component, "completeAuthenticationSetup", new Class[]{String.class}, "api-key");
        }

        verify(mockState).setMcpEnabled(true);
        verify(mockState).setMcpStatusChecked(true);
        verify(mockSt).setAscaRealtime(true);
        verify(mockSt).setOssRealtime(true);
    }

    @Test
    void completeAuthenticationSetup_WhenMcpStatusChangedToDisabled_DisablesScanners() throws Exception {
        GlobalSettingsState mockSt = setupCompleteAuthDependencies(true, true, true);
        com.checkmarx.intellij.ast.service.StateService mockStateService =
                mock(com.checkmarx.intellij.ast.service.StateService.class);

        try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                     mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class);
             MockedStatic<GlobalSettingsState> gsMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.ast.service.StateService> ssMock =
                     mockStatic(com.checkmarx.intellij.ast.service.StateService.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class);
             MockedConstruction<com.checkmarx.intellij.ast.ui.WelcomeDialog> wdMock =
                     mockConstruction(com.checkmarx.intellij.ast.ui.WelcomeDialog.class)) {

            tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(any(), any()))
                  .thenReturn(false);
            gsMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            ssMock.when(com.checkmarx.intellij.ast.service.StateService::getInstance).thenReturn(mockStateService);
            doNothing().when(mockStateService).pruneStaleCustomStates();

            invokePrivate(component, "completeAuthenticationSetup", new Class[]{String.class}, "api-key");
        }

        verify(mockState).setMcpEnabled(false);
        verify(mockState).setMcpStatusChecked(true);
    }

    @Test
    void completeAuthenticationSetup_WhenMcpUnchangedAndEnabled_CallsApply() throws Exception {
        GlobalSettingsState mockSt = setupCompleteAuthDependencies(true, true, false);
        com.checkmarx.intellij.ast.service.StateService mockStateService =
                mock(com.checkmarx.intellij.ast.service.StateService.class);

        try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                     mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class);
             MockedStatic<GlobalSettingsState> gsMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.ast.service.StateService> ssMock =
                     mockStatic(com.checkmarx.intellij.ast.service.StateService.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class);
             MockedConstruction<com.checkmarx.intellij.ast.ui.WelcomeDialog> wdMock =
                     mockConstruction(com.checkmarx.intellij.ast.ui.WelcomeDialog.class)) {

            tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(any(), any()))
                  .thenReturn(true);
            gsMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            ssMock.when(com.checkmarx.intellij.ast.service.StateService::getInstance).thenReturn(mockStateService);
            doNothing().when(mockStateService).pruneStaleCustomStates();
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.installForCopilot(any()))
                   .thenReturn(Boolean.FALSE);

            invokePrivate(component, "completeAuthenticationSetup", new Class[]{String.class}, "api-key");
        }

        verify(mockState).setMcpEnabled(true);
        verify(mockState).setMcpStatusChecked(true);
        verify(mockState).apply(any(GlobalSettingsState.class));
    }

    @Test
    void completeAuthenticationSetup_WhenMcpCheckThrows_ContinuesWithFalseMcp() throws Exception {
        GlobalSettingsState mockSt = setupCompleteAuthDependencies(true, false, false);
        com.checkmarx.intellij.ast.service.StateService mockStateService =
                mock(com.checkmarx.intellij.ast.service.StateService.class);

        try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                     mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class);
             MockedStatic<GlobalSettingsState> gsMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.ast.service.StateService> ssMock =
                     mockStatic(com.checkmarx.intellij.ast.service.StateService.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class);
             MockedConstruction<com.checkmarx.intellij.ast.ui.WelcomeDialog> wdMock =
                     mockConstruction(com.checkmarx.intellij.ast.ui.WelcomeDialog.class)) {

            tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(any(), any()))
                  .thenThrow(new RuntimeException("network error"));
            gsMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            ssMock.when(com.checkmarx.intellij.ast.service.StateService::getInstance).thenReturn(mockStateService);
            doNothing().when(mockStateService).pruneStaleCustomStates();

            assertDoesNotThrow(() ->
                    invokePrivate(component, "completeAuthenticationSetup", new Class[]{String.class}, "api-key"));
        }

        verify(mockState).setMcpEnabled(false);
        verify(mockState).setMcpStatusChecked(true);
    }

    // ===== showWelcomeDialog() =====

    @Test
    void showWelcomeDialog_WhenCalled_CreatesWelcomeDialog() throws Exception {
        try (MockedConstruction<com.checkmarx.intellij.ast.ui.WelcomeDialog> wdMock =
                     mockConstruction(com.checkmarx.intellij.ast.ui.WelcomeDialog.class)) {
            invokePrivate(component, "showWelcomeDialog", new Class[]{boolean.class}, true);
            assertEquals(1, wdMock.constructed().size());
        }
    }

    @Test
    void showWelcomeDialog_WhenShowThrows_DoesNotPropagate() throws Exception {
        try (MockedConstruction<com.checkmarx.intellij.ast.ui.WelcomeDialog> wdMock =
                     mockConstruction(com.checkmarx.intellij.ast.ui.WelcomeDialog.class,
                             (mock, context) -> doThrow(new RuntimeException("UI error")).when(mock).show())) {
            assertDoesNotThrow(() ->
                    invokePrivate(component, "showWelcomeDialog", new Class[]{boolean.class}, false));
        }
    }

    // ===== handleOAuthFailure() =====

    @Test
    void handleOAuthFailure_SetsAuthFalseAndValidationState() throws Exception {
        setField(component, "validateResult", new JBLabel());
        setField(component, "connectButton", new JButton());
        JRadioButton oauthR = new JRadioButton();
        JRadioButton apiKeyR = new JRadioButton();
        setField(component, "oauthRadio", oauthR);
        setField(component, "apiKeyRadio", apiKeyR);
        setField(component, "baseUrlField", new JTextField());
        setField(component, "tenantField", new JTextField());
        setField(component, "apiKeyField", new JBPasswordField());
        setField(component, "oauthLabel", new JBLabel());
        setField(component, "baseUrlLabel", new JBLabel());
        setField(component, "tenantLabel", new JBLabel());
        setField(component, "assistLink", null);

        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        setField(component, "messageBus", mockBus);

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.getAdditionalParameters()).thenReturn("");
        when(mockState.getBaseUrl()).thenReturn("");
        when(mockState.getTenant()).thenReturn("");
        when(mockState.getRefreshTokenExpiry()).thenReturn("");
        when(mockState.getValidationExpiry()).thenReturn("");
        when(mockState.getValidationMessage()).thenReturn("");
        when(mockState.isLastValidationSuccess()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isWelcomeShown()).thenReturn(false);
        when(mockState.isApiKeyEnabled()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class)) {
            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());

            assertDoesNotThrow(() ->
                    invokePrivate(component, "handleOAuthFailure", new Class[]{String.class}, "auth failed"));
        }
        // body executes via SwingUtilities.invokeLater — just verify no exception propagates
    }

    // ===== notifyLogout() / notifyAuthSuccess() / notifyAuthError() =====

    @Test
    void notifyLogout_InvokesLaterWithInformationNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());
            bundleMock.when(() -> Bundle.message(Resource.LOGOUT_SUCCESS_TITLE)).thenReturn("Logged out");
            bundleMock.when(() -> Bundle.message(Resource.LOGOUT_SUCCESS)).thenReturn("You are logged out");

            assertDoesNotThrow(() -> invokePrivate(component, "notifyLogout", new Class[]{}));
        }

        verify(mockApp).invokeLater(any());
    }

    @Test
    void notifyAuthSuccess_InvokesLaterWithSuccessNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());
            bundleMock.when(() -> Bundle.message(Resource.SUCCESS_AUTHENTICATION_TITLE)).thenReturn("Authenticated");
            bundleMock.when(() -> Bundle.message(Resource.VALIDATE_SUCCESS)).thenReturn("OK");

            assertDoesNotThrow(() -> invokePrivate(component, "notifyAuthSuccess", new Class[]{}));
        }

        verify(mockApp).invokeLater(any());
    }

    @Test
    void notifyAuthError_InvokesLaterWithErrorNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());
            bundleMock.when(() -> Bundle.message(Resource.ERROR_AUTHENTICATION_TITLE)).thenReturn("Auth Error");

            assertDoesNotThrow(() ->
                    invokePrivate(component, "notifyAuthError", new Class[]{String.class}, "invalid credentials"));
        }

        verify(mockApp).invokeLater(any());
    }

    // ===== installMcpAsync() =====

    @Test
    void installMcpAsync_WhenMcpModified_ShowsConfigSavedNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.installForCopilot(any()))
                   .thenReturn(Boolean.TRUE);
            bundleMock.when(() -> Bundle.message(any())).thenReturn("msg");

            assertDoesNotThrow(() ->
                    invokePrivate(component, "installMcpAsync", new Class[]{String.class}, "api-key"));
        }
    }

    @Test
    void installMcpAsync_WhenMcpAlreadyUpToDate_ShowsUpToDateNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.installForCopilot(any()))
                   .thenReturn(Boolean.FALSE);
            bundleMock.when(() -> Bundle.message(any())).thenReturn("msg");

            assertDoesNotThrow(() ->
                    invokePrivate(component, "installMcpAsync", new Class[]{String.class}, "refresh-token"));
        }
    }

    @Test
    void installMcpAsync_WhenMcpThrows_ShowsErrorNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {

            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.installForCopilot(any()))
                   .thenThrow(new RuntimeException("install error"));
            bundleMock.when(() -> Bundle.message(any())).thenReturn("msg");

            assertDoesNotThrow(() ->
                    invokePrivate(component, "installMcpAsync", new Class[]{String.class}, "api-key"));
        }
    }

    // ===== notifyLogout / notifyAuthSuccess / notifyAuthError — lambda body coverage =====

    @Test
    void notifyLogout_WhenInvokeLaterLambdaRuns_CallsShowNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<com.checkmarx.intellij.common.utils.Utils> utilsMock =
                     mockStatic(com.checkmarx.intellij.common.utils.Utils.class)) {

            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication)
                   .thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(Resource.LOGOUT_SUCCESS_TITLE)).thenReturn("Logged out");
            bundleMock.when(() -> Bundle.message(Resource.LOGOUT_SUCCESS)).thenReturn("You are logged out");
            utilsMock.when(() -> com.checkmarx.intellij.common.utils.Utils.showNotification(
                    any(), any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> null);

            invokePrivate(component, "notifyLogout", new Class[]{});

            utilsMock.verify(() -> com.checkmarx.intellij.common.utils.Utils.showNotification(
                    eq("Logged out"), eq("You are logged out"), any(), any(), anyBoolean(), any()));
        }
    }

    @Test
    void notifyAuthSuccess_WhenInvokeLaterLambdaRuns_CallsShowNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<com.checkmarx.intellij.common.utils.Utils> utilsMock =
                     mockStatic(com.checkmarx.intellij.common.utils.Utils.class)) {

            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication)
                   .thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(Resource.SUCCESS_AUTHENTICATION_TITLE)).thenReturn("Authenticated");
            bundleMock.when(() -> Bundle.message(Resource.VALIDATE_SUCCESS)).thenReturn("Connected");
            utilsMock.when(() -> com.checkmarx.intellij.common.utils.Utils.showNotification(
                    any(), any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> null);

            invokePrivate(component, "notifyAuthSuccess", new Class[]{});

            utilsMock.verify(() -> com.checkmarx.intellij.common.utils.Utils.showNotification(
                    eq("Authenticated"), eq("Connected"), any(), any(), anyBoolean(), any()));
        }
    }

    @Test
    void notifyAuthError_WhenInvokeLaterLambdaRuns_CallsShowNotification() throws Exception {
        com.intellij.openapi.application.Application mockApp =
                mock(com.intellij.openapi.application.Application.class);

        try (MockedStatic<com.intellij.openapi.application.ApplicationManager> appMock =
                     mockStatic(com.intellij.openapi.application.ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<com.checkmarx.intellij.common.utils.Utils> utilsMock =
                     mockStatic(com.checkmarx.intellij.common.utils.Utils.class)) {

            appMock.when(com.intellij.openapi.application.ApplicationManager::getApplication)
                   .thenReturn(mockApp);
            doAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class));
            bundleMock.when(() -> Bundle.message(Resource.ERROR_AUTHENTICATION_TITLE)).thenReturn("Auth Error");
            utilsMock.when(() -> com.checkmarx.intellij.common.utils.Utils.showNotification(
                    any(), any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> null);

            invokePrivate(component, "notifyAuthError", new Class[]{String.class}, "bad credentials");

            utilsMock.verify(() -> com.checkmarx.intellij.common.utils.Utils.showNotification(
                    eq("Auth Error"), eq("bad credentials"), any(), any(), anyBoolean(), any()));
        }
    }

    // ===== reset() lambda — focus field branches =====

    @Test
    void reset_WhenAuthenticatedApiKey_SwingLambdaFocusesApiKeyField() throws Exception {
        injectAllResetDependencies();
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(true);
        when(mockState.getValidationMessage()).thenReturn("OK");
        when(mockState.getAdditionalParameters()).thenReturn("");
        when(mockState.getBaseUrl()).thenReturn("");
        when(mockState.getTenant()).thenReturn("");
        when(mockSensitiveState.getApiKey()).thenReturn("k");
        when(mockSensitiveState.getRefreshToken()).thenReturn("rt");
        when(mockSensitiveState.isValid(mockState)).thenReturn(true);

        try (MockedStatic<javax.swing.SwingUtilities> swingMock =
                     mockStatic(javax.swing.SwingUtilities.class)) {
            swingMock.when(() -> javax.swing.SwingUtilities.invokeLater(any(Runnable.class)))
                     .thenAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; });
            component.reset();
        }
    }

    @Test
    void reset_WhenAuthenticatedOAuth_SwingLambdaFocusesBaseUrlField() throws Exception {
        injectAllResetDependencies();
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(true);
        when(mockState.getValidationMessage()).thenReturn("OK");
        when(mockState.getAdditionalParameters()).thenReturn("");
        when(mockState.getBaseUrl()).thenReturn("https://cx.com");
        when(mockState.getTenant()).thenReturn("t");
        when(mockSensitiveState.getApiKey()).thenReturn("");
        when(mockSensitiveState.getRefreshToken()).thenReturn("rt");
        when(mockSensitiveState.isValid(mockState)).thenReturn(true);

        try (MockedStatic<javax.swing.SwingUtilities> swingMock =
                     mockStatic(javax.swing.SwingUtilities.class)) {
            swingMock.when(() -> javax.swing.SwingUtilities.invokeLater(any(Runnable.class)))
                     .thenAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; });
            component.reset();
        }
    }
}
