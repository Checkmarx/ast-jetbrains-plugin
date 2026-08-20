
package com.checkmarx.intellij.cxdevassist.test.unit.settings;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.cxdevassist.settings.CxDevAssistSettingsComponent;
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
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CxDevAssistSettingsComponentTest {

    private GlobalSettingsState mockState;
    private GlobalSettingsSensitiveState mockSensitiveState;
    private CxDevAssistSettingsComponent component;

    // ---- Reflection helpers ----

    private static CxDevAssistSettingsComponent newInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (CxDevAssistSettingsComponent) unsafe.allocateInstance(CxDevAssistSettingsComponent.class);
    }

    private static void setStaticField(Class<?> clazz, String name, Object value) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private MessageBus injectMessageBus() throws Exception {
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        setField(component, "messageBus", mockBus);
        return mockBus;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    // ---- Test lifecycle ----

    @BeforeEach
    void setUp() throws Exception {
        mockState = mock(GlobalSettingsState.class);
        mockSensitiveState = mock(GlobalSettingsSensitiveState.class);
        component = newInstanceWithoutConstructor();
        setStaticField(CxDevAssistSettingsComponent.class, "globalSettingsState", mockState);
        setStaticField(CxDevAssistSettingsComponent.class, "globalSettingsSensitiveState", mockSensitiveState);
    }

    @AfterEach
    void tearDown() throws Exception {
        setStaticField(CxDevAssistSettingsComponent.class, "globalSettingsState", null);
        setStaticField(CxDevAssistSettingsComponent.class, "globalSettingsSensitiveState", null);
    }

    // ===== isModified() =====

    @Test
    void isModified_WhenApiKeyMatchesState_ReturnsFalse() throws Exception {
        JBPasswordField field = new JBPasswordField();
        field.setText("my-api-key");
        setField(component, "apiKeyField", field);
        when(mockSensitiveState.getApiKey()).thenReturn("my-api-key");

        assertFalse(component.isModified());
    }

    @Test
    void isModified_WhenApiKeyDiffersFromState_ReturnsTrue() throws Exception {
        JBPasswordField field = new JBPasswordField();
        field.setText("new-key");
        setField(component, "apiKeyField", field);
        when(mockSensitiveState.getApiKey()).thenReturn("old-key");

        assertTrue(component.isModified());
    }

    @Test
    void isModified_WhenFieldEmptyAndStateHasKey_ReturnsTrue() throws Exception {
        JBPasswordField field = new JBPasswordField();
        field.setText("");
        setField(component, "apiKeyField", field);
        when(mockSensitiveState.getApiKey()).thenReturn("some-key");

        assertTrue(component.isModified());
    }

    @Test
    void isModified_WhenBothEmpty_ReturnsFalse() throws Exception {
        JBPasswordField field = new JBPasswordField();
        field.setText("");
        setField(component, "apiKeyField", field);
        when(mockSensitiveState.getApiKey()).thenReturn("");

        assertFalse(component.isModified());
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
    void isValid_WhenAuthenticatedWithExpiredOAuthToken_ReturnsFalse() {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockSensitiveState.getApiKey()).thenReturn("");
        when(mockSensitiveState.getRefreshToken()).thenReturn("some-token");
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
        when(mockSensitiveState.getRefreshToken()).thenReturn("valid-token");
        when(mockState.isApiKeyEnabled()).thenReturn(false);
        when(mockState.getRefreshTokenExpiry()).thenReturn("2099-01-01T00:00:00");
        when(mockSensitiveState.isTokenExpired("2099-01-01T00:00:00")).thenReturn(false);
        when(mockSensitiveState.isValid(mockState)).thenReturn(true);

        assertTrue(component.isValid());
    }

    // ===== apply() =====

    @Test
    void apply_CallsGlobalStateApplyAndPublishesSettingsEvent() throws Exception {
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("key");
        setField(component, "apiKeyField", apiKeyField);

        JTextField additionalField = mock(JTextField.class);
        when(additionalField.getText()).thenReturn("");
        // Use ExpandableTextField mock by injecting a plain JTextField stand-in via reflection
        // CxDevAssistSettingsComponent uses ExpandableTextField — inject a mock that returns text
        com.intellij.ui.components.fields.ExpandableTextField expandable =
                mock(com.intellij.ui.components.fields.ExpandableTextField.class);
        when(expandable.getText()).thenReturn("extra-params");
        setField(component, "additionalParametersField", expandable);

        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.getAdditionalParameters()).thenReturn("extra-params");
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        MessageBus mockBus = injectMessageBus();

        try (MockedConstruction<GlobalSettingsSensitiveState> ignored =
                mockConstruction(GlobalSettingsSensitiveState.class)) {
            assertDoesNotThrow(() -> component.apply());
        }

        verify(mockState).apply(any(GlobalSettingsState.class));
        verify(mockSensitiveState).apply(any(), any());
        verify(mockBus).syncPublisher(SettingsListener.SETTINGS_APPLIED);
    }

    // ===== getMainPanel() =====

    @Test
    void getMainPanel_ReturnsInjectedPanel() throws Exception {
        JPanel panel = new JPanel();
        setField(component, "mainPanel", panel);

        assertSame(panel, component.getMainPanel());
    }

    // ===== shouldShowAssistLink() — private =====

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

    @Test
    void shouldShowAssistLink_WhenNotAuthenticated_ReturnsFalse() throws Exception {
        when(mockState.isAuthenticated()).thenReturn(false);

        boolean result = (boolean) invokePrivate(component, "shouldShowAssistLink", new Class[]{});
        assertFalse(result);
    }

    @Test
    void shouldShowAssistLink_WhenAuthenticatedWithNoLicense_ReturnsFalse() throws Exception {
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);

        boolean result = (boolean) invokePrivate(component, "shouldShowAssistLink", new Class[]{});
        assertFalse(result);
    }

    // ===== setInvalidAuthState() — private =====

    @Test
    void setInvalidAuthState_SetsAuthenticatedFalseAndMessage() throws Exception {
        invokePrivate(component, "setInvalidAuthState", new Class[]{String.class}, "error-msg");

        verify(mockState).setValidationMessage("error-msg");
        verify(mockState).setLastValidationSuccess(false);
        verify(mockState).setAuthenticated(false);
    }

    // ===== isValidateTimeExpired() — private =====

    @Test
    void isValidateTimeExpired_WhenExpiryInPast_ReturnsTrue() throws Exception {
        String pastTime = LocalDateTime.now().minusMinutes(5)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        when(mockState.getValidationExpiry()).thenReturn(pastTime);

        boolean result = (boolean) invokePrivate(component, "isValidateTimeExpired", new Class[]{});
        assertTrue(result);
    }

    @Test
    void isValidateTimeExpired_WhenExpiryInFuture_ReturnsFalse() throws Exception {
        String futureTime = LocalDateTime.now().plusHours(1)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        when(mockState.getValidationExpiry()).thenReturn(futureTime);

        boolean result = (boolean) invokePrivate(component, "isValidateTimeExpired", new Class[]{});
        assertFalse(result);
    }

    @Test
    void isValidateTimeExpired_WhenExpiryBlank_ReturnsFalse() throws Exception {
        when(mockState.getValidationExpiry()).thenReturn("");

        boolean result = (boolean) invokePrivate(component, "isValidateTimeExpired", new Class[]{});
        assertFalse(result);
    }

    // ===== getSensitiveStateFromFields() — private =====

    @Test
    void getSensitiveStateFromFields_ReturnsStateWithApiKey() throws Exception {
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("secret-key");
        setField(component, "apiKeyField", apiKeyField);
        when(mockSensitiveState.getRefreshToken()).thenReturn("rt");

        try (MockedConstruction<GlobalSettingsSensitiveState> ignored =
                     mockConstruction(GlobalSettingsSensitiveState.class)) {
            GlobalSettingsSensitiveState result = (GlobalSettingsSensitiveState)
                    invokePrivate(component, "getSensitiveStateFromFields", new Class[]{});
            assertNotNull(result);
        }
    }

    // ===== getStateFromFields() — private =====

    @Test
    void getStateFromFields_PreservesRealtimeScannerFlags() throws Exception {
        ExpandableTextField expandable = mock(ExpandableTextField.class);
        when(expandable.getText()).thenReturn("extra");
        setField(component, "additionalParametersField", expandable);

        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(true);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isWelcomeShown()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.getUserPrefAscaRealtime()).thenReturn(true);
        when(mockState.getUserPrefOssRealtime()).thenReturn(false);
        when(mockState.getUserPrefSecretDetectionRealtime()).thenReturn(true);
        when(mockState.getUserPrefContainersRealtime()).thenReturn(false);
        when(mockState.getUserPrefIacRealtime()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);

        GlobalSettingsState result = (GlobalSettingsState)
                invokePrivate(component, "getStateFromFields", new Class[]{});

        assertNotNull(result);
        assertTrue(result.isAscaRealtime());
        assertFalse(result.isOssRealtime());
        assertTrue(result.isIacRealtime());
        assertTrue(result.isApiKeyEnabled());
        assertEquals("extra", result.getAdditionalParameters());
    }

    // ===== autoEnableAllRealtimeScanners() — private, uses GlobalSettingsState.getInstance() =====

    @Test
    void autoEnableAllRealtimeScanners_WhenPreferencesSetAndChanged_CallsApply() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(true);
        when(mockSt.applyUserPreferencesToRealtimeSettings()).thenReturn(true);

        MessageBus mockBus = injectMessageBus();
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("key");
        setField(component, "apiKeyField", apiKeyField);
        ExpandableTextField expandable = mock(ExpandableTextField.class);
        when(expandable.getText()).thenReturn("p");
        setField(component, "additionalParametersField", expandable);
        stubAllStateFields(mockState);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            assertDoesNotThrow(() -> invokePrivate(component, "autoEnableAllRealtimeScanners", new Class[]{}));
        }

        verify(mockSt).applyUserPreferencesToRealtimeSettings();
    }

    @Test
    void autoEnableAllRealtimeScanners_WhenPreferencesNotSet_EnablesAllScanners() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(false);
        when(mockSt.isAscaRealtime()).thenReturn(false);
        when(mockSt.isOssRealtime()).thenReturn(false);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(false);
        when(mockSt.isContainersRealtime()).thenReturn(false);
        when(mockSt.isIacRealtime()).thenReturn(false);

        MessageBus mockBus = injectMessageBus();
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("k");
        setField(component, "apiKeyField", apiKeyField);
        ExpandableTextField expandable = mock(ExpandableTextField.class);
        when(expandable.getText()).thenReturn("");
        setField(component, "additionalParametersField", expandable);
        stubAllStateFields(mockState);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            assertDoesNotThrow(() -> invokePrivate(component, "autoEnableAllRealtimeScanners", new Class[]{}));
        }

        verify(mockSt).setAscaRealtime(true);
        verify(mockSt).setOssRealtime(true);
        verify(mockSt).setSecretDetectionRealtime(true);
        verify(mockSt).setContainersRealtime(true);
        verify(mockSt).setIacRealtime(true);
    }

    // ===== disableAllRealtimeScanners() — private =====

    @Test
    void disableAllRealtimeScanners_WhenScannersEnabled_DisablesAll() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(true);
        when(mockSt.isAscaRealtime()).thenReturn(true);
        when(mockSt.isOssRealtime()).thenReturn(true);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(true);
        when(mockSt.isContainersRealtime()).thenReturn(true);
        when(mockSt.isIacRealtime()).thenReturn(true);

        MessageBus mockBus = injectMessageBus();
        JBPasswordField apiKeyField = new JBPasswordField();
        apiKeyField.setText("k");
        setField(component, "apiKeyField", apiKeyField);
        ExpandableTextField expandable = mock(ExpandableTextField.class);
        when(expandable.getText()).thenReturn("");
        setField(component, "additionalParametersField", expandable);
        stubAllStateFields(mockState);
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedConstruction<GlobalSettingsSensitiveState> ignored = mockConstruction(GlobalSettingsSensitiveState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            assertDoesNotThrow(() -> invokePrivate(component, "disableAllRealtimeScanners", new Class[]{}));
        }

        verify(mockSt).setAscaRealtime(false);
        verify(mockSt).setOssRealtime(false);
        verify(mockSt).setSecretDetectionRealtime(false);
        verify(mockSt).setContainersRealtime(false);
        verify(mockSt).setIacRealtime(false);
    }

    @Test
    void disableAllRealtimeScanners_WhenAlreadyDisabled_DoesNotCallApply() throws Exception {
        GlobalSettingsState mockSt = mock(GlobalSettingsState.class);
        when(mockSt.getUserPreferencesSet()).thenReturn(true);
        when(mockSt.isAscaRealtime()).thenReturn(false);
        when(mockSt.isOssRealtime()).thenReturn(false);
        when(mockSt.isSecretDetectionRealtime()).thenReturn(false);
        when(mockSt.isContainersRealtime()).thenReturn(false);
        when(mockSt.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSt);
            assertDoesNotThrow(() -> invokePrivate(component, "disableAllRealtimeScanners", new Class[]{}));
        }

        // No setter calls since all already false
        verify(mockSt, never()).setAscaRealtime(anyBoolean());
        verify(mockSt, never()).setOssRealtime(anyBoolean());
    }

    // ===== setValidationResult() — private (no-arg) =====

    private JBLabel injectValidateResult() throws Exception {
        JBLabel label = new JBLabel();
        setField(component, "validateResult", label);
        return label;
    }

    @Test
    void setValidationResult_WhenInProgress_ShowsGreenMessage() throws Exception {
        JBLabel label = injectValidateResult();
        when(mockState.isValidationInProgress()).thenReturn(true);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(any())).thenReturn("validating...");
            invokePrivate(component, "setValidationResult", new Class[]{});
        }

        assertTrue(label.isVisible());
        assertTrue(label.getText().contains("validating..."));
    }

    @Test
    void setValidationResult_WhenSuccessWithMessage_ShowsGreenMessage() throws Exception {
        JBLabel label = injectValidateResult();
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(true);
        when(mockState.getValidationMessage()).thenReturn("Connected!");

        invokePrivate(component, "setValidationResult", new Class[]{});

        assertTrue(label.isVisible());
        assertTrue(label.getText().contains("Connected!"));
    }

    @Test
    void setValidationResult_WhenErrorWithMessage_ShowsRedMessage() throws Exception {
        JBLabel label = injectValidateResult();
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(false);
        when(mockState.getValidationMessage()).thenReturn("Auth failed");

        invokePrivate(component, "setValidationResult", new Class[]{});

        assertTrue(label.isVisible());
        assertTrue(label.getText().contains("Auth failed"));
    }

    @Test
    void setValidationResult_WhenNoMessage_HidesValidateResult() throws Exception {
        JBLabel label = injectValidateResult();
        label.setVisible(true);
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(false);
        when(mockState.getValidationMessage()).thenReturn("");

        invokePrivate(component, "setValidationResult", new Class[]{});

        assertFalse(label.isVisible());
    }

    // ===== setInputFields() — private =====

    @Test
    void setInputFields_SetsFieldTextsFromState() throws Exception {
        JBPasswordField apiField = new JBPasswordField();
        setField(component, "apiKeyField", apiField);
        ExpandableTextField additionalField = mock(ExpandableTextField.class);
        setField(component, "additionalParametersField", additionalField);
        when(mockState.getAdditionalParameters()).thenReturn("--debug");
        when(mockSensitiveState.getApiKey()).thenReturn("my-key");

        invokePrivate(component, "setInputFields", new Class[]{});

        assertEquals("my-key", new String(apiField.getPassword()));
        verify(additionalField).setText("--debug");
    }

    // ===== updateConnectButtonState() — private =====

    @Test
    void updateConnectButtonState_WhenFieldEmptyAndNotAuth_DisablesConnect() throws Exception {
        JButton connectBtn = new JButton();
        connectBtn.setEnabled(true);
        setField(component, "connectButton", connectBtn);
        JBPasswordField apiField = new JBPasswordField();
        apiField.setText("  "); // whitespace only
        setField(component, "apiKeyField", apiField);
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);

        invokePrivate(component, "updateConnectButtonState", new Class[]{});

        assertFalse(connectBtn.isEnabled());
    }

    @Test
    void updateConnectButtonState_WhenFieldNonEmptyAndNotAuth_EnablesConnect() throws Exception {
        JButton connectBtn = new JButton();
        connectBtn.setEnabled(false);
        setField(component, "connectButton", connectBtn);
        JBPasswordField apiField = new JBPasswordField();
        apiField.setText("my-key");
        setField(component, "apiKeyField", apiField);
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(false);

        invokePrivate(component, "updateConnectButtonState", new Class[]{});

        assertTrue(connectBtn.isEnabled());
    }

    @Test
    void updateConnectButtonState_WhenAuthenticated_DisablesConnect() throws Exception {
        JButton connectBtn = new JButton();
        connectBtn.setEnabled(true);
        setField(component, "connectButton", connectBtn);
        JBPasswordField apiField = new JBPasswordField();
        apiField.setText("my-key");
        setField(component, "apiKeyField", apiField);
        when(mockState.isAuthenticated()).thenReturn(true);

        invokePrivate(component, "updateConnectButtonState", new Class[]{});

        assertFalse(connectBtn.isEnabled());
    }

    // ===== setFieldsEditable() — private =====

    @Test
    void setFieldsEditable_True_EnablesApiKeyField() throws Exception {
        JBPasswordField apiField = new JBPasswordField();
        apiField.setEnabled(false);
        setField(component, "apiKeyField", apiField);

        invokePrivate(component, "setFieldsEditable", new Class[]{boolean.class}, true);

        assertTrue(apiField.isEnabled());
    }

    @Test
    void setFieldsEditable_False_DisablesApiKeyField() throws Exception {
        JBPasswordField apiField = new JBPasswordField();
        apiField.setEnabled(true);
        setField(component, "apiKeyField", apiField);

        invokePrivate(component, "setFieldsEditable", new Class[]{boolean.class}, false);

        assertFalse(apiField.isEnabled());
    }

    // ===== updateAssistLinkVisibility() — private =====

    @Test
    void updateAssistLinkVisibility_WhenAssistLinkNull_DoesNotThrow() {
        // assistLink field is null (allocateInstance leaves it null)
        assertDoesNotThrow(() -> invokePrivate(component, "updateAssistLinkVisibility", new Class[]{}));
    }

    // ===== reset() =====

    @Test
    void reset_WhenValidatingAndNotExpired_LocksUI() throws Exception {
        // Inject all UI components needed
        JBPasswordField apiField = new JBPasswordField();
        apiField.setText("k");
        setField(component, "apiKeyField", apiField);
        ExpandableTextField additionalField = mock(ExpandableTextField.class);
        when(additionalField.getText()).thenReturn("");
        setField(component, "additionalParametersField", additionalField);
        JButton connectBtn = new JButton();
        setField(component, "connectButton", connectBtn);
        JButton logoutBtn = new JButton();
        logoutBtn.setEnabled(true);
        setField(component, "logoutButton", logoutBtn);
        JBLabel validateResultLabel = new JBLabel();
        setField(component, "validateResult", validateResultLabel);

        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isValidationInProgress()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.getValidationExpiry()).thenReturn(""); // not expired
        when(mockSensitiveState.getApiKey()).thenReturn("k");
        when(mockSensitiveState.getRefreshToken()).thenReturn("");

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(any())).thenReturn("in progress");
            component.reset();
        }

        assertFalse(connectBtn.isEnabled());
        assertFalse(logoutBtn.isEnabled());
        assertFalse(apiField.isEnabled()); // setFieldsEditable(false)
    }

    @Test
    void reset_WhenAuthenticated_EnablesLogoutButton() throws Exception {
        JBPasswordField apiField = new JBPasswordField();
        apiField.setText("k");
        setField(component, "apiKeyField", apiField);
        ExpandableTextField additionalField = mock(ExpandableTextField.class);
        when(additionalField.getText()).thenReturn("");
        setField(component, "additionalParametersField", additionalField);
        JButton logoutBtn = new JButton();
        logoutBtn.setEnabled(false);
        setField(component, "logoutButton", logoutBtn);
        JBLabel apiKeyLabel = new JBLabel();
        setField(component, "apiKeyLabel", apiKeyLabel);
        JBLabel validateResultLabel = new JBLabel();
        setField(component, "validateResult", validateResultLabel);

        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);
        when(mockState.isValidationInProgress()).thenReturn(false);
        when(mockState.isLastValidationSuccess()).thenReturn(true);
        when(mockState.getValidationMessage()).thenReturn("OK");
        when(mockSensitiveState.getApiKey()).thenReturn("k");
        when(mockSensitiveState.getRefreshToken()).thenReturn("rt");
        when(mockSensitiveState.isValid(mockState)).thenReturn(true);

        component.reset();

        assertTrue(logoutBtn.isEnabled());
        assertFalse(apiField.isEnabled()); // setFieldsEditable(false)
    }

    // ===== initState() — private =====

    @Test
    void initState_WhenStateNull_FetchesGlobalStateInstance() throws Exception {
        setStaticField(CxDevAssistSettingsComponent.class, "globalSettingsState", null);
        setStaticField(CxDevAssistSettingsComponent.class, "globalSettingsSensitiveState", null);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> sensitiveMock = mockStatic(GlobalSettingsSensitiveState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitiveState);
            invokePrivate(component, "initState", new Class[]{});
        }

        // After initState, the static fields should be populated (verify getInstance was called)
        GlobalSettingsState result = (GlobalSettingsState) getStaticField(CxDevAssistSettingsComponent.class, "globalSettingsState");
        assertNotNull(result);
    }

    private static Object getStaticField(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    /** Stub all state getters needed by getStateFromFields() so apply() doesn't NPE. */
    private void stubAllStateFields(GlobalSettingsState st) {
        when(st.isAuthenticated()).thenReturn(false);
        when(st.isAscaRealtime()).thenReturn(false);
        when(st.isOssRealtime()).thenReturn(false);
        when(st.isSecretDetectionRealtime()).thenReturn(false);
        when(st.isContainersRealtime()).thenReturn(false);
        when(st.isIacRealtime()).thenReturn(false);
        when(st.getContainersTool()).thenReturn("docker");
        when(st.isWelcomeShown()).thenReturn(false);
        when(st.isMcpEnabled()).thenReturn(false);
        when(st.isMcpStatusChecked()).thenReturn(false);
        when(st.getUserPreferencesSet()).thenReturn(false);
        when(st.getUserPrefAscaRealtime()).thenReturn(false);
        when(st.getUserPrefOssRealtime()).thenReturn(false);
        when(st.getUserPrefSecretDetectionRealtime()).thenReturn(false);
        when(st.getUserPrefContainersRealtime()).thenReturn(false);
        when(st.getUserPrefIacRealtime()).thenReturn(false);
        when(st.isDevAssistLicenseEnabled()).thenReturn(false);
        when(st.isOneAssistLicenseEnabled()).thenReturn(false);
    }
}
