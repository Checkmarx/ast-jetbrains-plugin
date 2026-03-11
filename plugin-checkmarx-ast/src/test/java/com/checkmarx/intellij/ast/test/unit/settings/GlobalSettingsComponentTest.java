package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.intellij.ast.settings.GlobalSettingsComponent;
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
}
