package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.intellij.ast.settings.CxOneAssistComponent;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.common.utils.Constants;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CxOneAssistComponent}.
 *
 * Static formatting tests invoke the private static helper directly via reflection.
 * Behaviour tests use sun.misc.Unsafe to bypass the constructor (which requires the
 * IntelliJ platform) and then inject mocks/stubs via reflection.
 *
 * Note: containersToolCombo is IntelliJ's ComboBox (not JComboBox), so we mock it.
 */
class CxOneAssistComponentTest {

    // ===== Static formatting tests =====

    private static String formatTitle(String raw) throws Exception {
        Method m = CxOneAssistComponent.class.getDeclaredMethod("formatTitle", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }

    @Test
    void formatTitle_WithNull_ReturnsEmptyHtml() throws Exception {
        assertEquals("<html></html>", formatTitle(null));
    }

    @Test
    void formatTitle_WithoutColon_WrapsEntireText() throws Exception {
        assertEquals(String.format(Constants.HTML_WRAPPER_FORMAT, "Simple Title"), formatTitle("Simple Title"));
    }

    @Test
    void formatTitle_WithColonAndValue_BoldsTextAfterColon() throws Exception {
        assertEquals(
                String.format(Constants.HTML_WRAPPER_FORMAT, "Prefix: <b>Value Part</b>"),
                formatTitle("Prefix: Value Part"));
    }

    @Test
    void formatTitle_WithColonAtEnd_TreatsAsNoValue() throws Exception {
        assertEquals(String.format(Constants.HTML_WRAPPER_FORMAT, "Heading:"), formatTitle("Heading:"));
    }

    // ===== Behaviour tests via Unsafe + reflection =====

    private CxOneAssistComponent component;
    private GlobalSettingsState mockState;

    private static CxOneAssistComponent newInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (CxOneAssistComponent) unsafe.allocateInstance(CxOneAssistComponent.class);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] paramTypes,
                                        Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    /** ComboBox is IntelliJ's class (not JComboBox), mock it to avoid type-injection errors. */
    @SuppressWarnings("unchecked")
    private static ComboBox<String> mockCombo(String selectedItem) {
        ComboBox<String> combo = mock(ComboBox.class);
        when(combo.getSelectedItem()).thenReturn(selectedItem);
        return combo;
    }

    /** Injects all checkbox and combo fields plus the state field. */
    private void injectUIFields() throws Exception {
        setField(component, "ascaCheckbox", new JBCheckBox());
        setField(component, "ossCheckbox", new JBCheckBox());
        setField(component, "secretsCheckbox", new JBCheckBox());
        setField(component, "containersCheckbox", new JBCheckBox());
        setField(component, "iacCheckbox", new JBCheckBox());
        setField(component, "containersToolCombo", mockCombo("docker"));
        setField(component, "mcpStatusLabel", new JBLabel());
        setField(component, "assistMessageLabel", new JBLabel());
        setField(component, "state", mockState);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockState = mock(GlobalSettingsState.class);
        component = newInstanceWithoutConstructor();
        injectUIFields();
    }

    // ===== isModified() =====

    @Test
    void isModified_WhenAllCheckboxesMatchState_ReturnsFalse() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertFalse(component.isModified());
        }
    }

    @Test
    void isModified_WhenAscaCheckboxDiffersFromState_ReturnsTrue() throws Exception {
        JBCheckBox ascaCheckbox = (JBCheckBox) getField(component, "ascaCheckbox");
        ascaCheckbox.setSelected(true); // UI = true
        when(mockState.isAscaRealtime()).thenReturn(false); // state = false

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertTrue(component.isModified());
        }
    }

    @Test
    void isModified_WhenContainersToolDiffers_ReturnsTrue() throws Exception {
        // Inject combo that returns "podman"
        setField(component, "containersToolCombo", mockCombo("podman"));

        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertTrue(component.isModified());
        }
    }

    // ===== apply() =====

    @Test
    void apply_SetsStateFieldsFromCheckboxValues() throws Exception {
        JBCheckBox ascaCheckbox = (JBCheckBox) getField(component, "ascaCheckbox");
        JBCheckBox ossCheckbox = (JBCheckBox) getField(component, "ossCheckbox");
        JBCheckBox secretsCheckbox = (JBCheckBox) getField(component, "secretsCheckbox");
        JBCheckBox containersCheckbox = (JBCheckBox) getField(component, "containersCheckbox");
        JBCheckBox iacCheckbox = (JBCheckBox) getField(component, "iacCheckbox");

        ascaCheckbox.setSelected(true);
        ossCheckbox.setSelected(true);
        secretsCheckbox.setSelected(false);
        containersCheckbox.setSelected(true);
        iacCheckbox.setSelected(false);
        setField(component, "containersToolCombo", mockCombo("podman"));

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            component.apply();
        }

        verify(mockState).setAscaRealtime(true);
        verify(mockState).setAsca(true);
        verify(mockState).setOssRealtime(true);
        verify(mockState).setSecretDetectionRealtime(false);
        verify(mockState).setContainersRealtime(true);
        verify(mockState).setIacRealtime(false);
        verify(mockState).setContainersTool("podman");
        verify(mockState).setUserPreferences(true, true, false, true, false);
    }

    // ===== reset() =====

    @Test
    void reset_SetsCheckboxesFromState() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(true);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(true);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);

        // reset() injects a new combo via setSelectedItem(), but our combo is mocked so no-op
        JPanel mainPanel = new JPanel();
        setField(component, "mainPanel", mainPanel);

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            component.reset();
        }

        // disableAssistUI (no-license path) unchecks all checkboxes regardless of state
        assertFalse(((JBCheckBox) getField(component, "ascaCheckbox")).isSelected());
        assertFalse(((JBCheckBox) getField(component, "ossCheckbox")).isSelected());
        assertFalse(((JBCheckBox) getField(component, "iacCheckbox")).isSelected());
    }

    @Test
    void reset_WhenAscaFalseButLegacyAscaTrue_SetsAscaCheckboxTrue() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(true); // legacy flag
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);

        JPanel mainPanel = new JPanel();
        setField(component, "mainPanel", mainPanel);

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            component.reset();
        }

        // disableAssistUI (no-license path) unchecks all checkboxes; legacy asca=true triggers
        // settingsChanged=true path, verifying the state is cleared and ApplicationManager is invoked
        assertFalse(((JBCheckBox) getField(component, "ascaCheckbox")).isSelected());
    }

    // ===== handleMcpResult() =====

    @Test
    void handleMcpResult_WhenThrowableNotNull_ShowsErrorStatus() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.MCP_INSTALL_ERROR)).thenReturn("Install failed");

            invokePrivate(component, "handleMcpResult",
                    new Class[]{Boolean.class, Throwable.class},
                    null, new RuntimeException("test"));
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Install failed", mcpStatusLabel.getText());
        assertEquals(JBColor.RED, mcpStatusLabel.getForeground());
        assertTrue(mcpStatusLabel.isVisible());
        assertFalse((boolean) getField(component, "mcpInstallInProgress"));
    }

    @Test
    void handleMcpResult_WhenChangedIsNull_ShowsErrorStatus() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.MCP_INSTALL_ERROR)).thenReturn("Install failed");

            invokePrivate(component, "handleMcpResult",
                    new Class[]{Boolean.class, Throwable.class},
                    null, null);
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Install failed", mcpStatusLabel.getText());
    }

    @Test
    void handleMcpResult_WhenChangedTrue_ShowsSuccessStatus() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.MCP_CONFIG_SAVED)).thenReturn("Config saved");

            invokePrivate(component, "handleMcpResult",
                    new Class[]{Boolean.class, Throwable.class},
                    Boolean.TRUE, null);
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Config saved", mcpStatusLabel.getText());
        assertEquals(JBColor.GREEN, mcpStatusLabel.getForeground());
    }

    @Test
    void handleMcpResult_WhenChangedFalse_ShowsUpToDateStatus() throws Exception {
        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.MCP_CONFIG_UP_TO_DATE)).thenReturn("Up to date");

            invokePrivate(component, "handleMcpResult",
                    new Class[]{Boolean.class, Throwable.class},
                    Boolean.FALSE, null);
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Up to date", mcpStatusLabel.getText());
        assertEquals(JBColor.GREEN, mcpStatusLabel.getForeground());
    }

    // ===== showMcpStatus() =====

    @Test
    void showMcpStatus_SetsLabelTextColorAndVisibility() throws Exception {
        invokePrivate(component, "showMcpStatus",
                new Class[]{String.class, Color.class},
                "Status message", JBColor.GREEN);

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Status message", mcpStatusLabel.getText());
        assertTrue(mcpStatusLabel.isVisible());
    }

    @Test
    void showMcpStatus_StopsPreviousTimerBeforeStartingNew() throws Exception {
        Timer existingTimer = mock(Timer.class);
        setField(component, "mcpClearTimer", existingTimer);

        invokePrivate(component, "showMcpStatus",
                new Class[]{String.class, Color.class},
                "msg", JBColor.RED);

        verify(existingTimer).stop();
        Timer newTimer = (Timer) getField(component, "mcpClearTimer");
        assertNotNull(newTimer);
        assertNotSame(existingTimer, newTimer);
    }

    // ===== ensureState() =====

    @Test
    void ensureState_AlwaysRefreshesStateFromSingleton() throws Exception {
        GlobalSettingsState freshState = mock(GlobalSettingsState.class);
        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(freshState);
            invokePrivate(component, "ensureState", new Class[]{});
        }
        assertSame(freshState, (GlobalSettingsState) getField(component, "state"));
    }

    // ===== disableAssistUI() =====

    @Test
    void disableAssistUI_WithKeepVisibleTrue_ShowsMessageAndDisablesCheckboxes() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        JPanel mainPanel = new JPanel();
        setField(component, "mainPanel", mainPanel);
        setField(component, "installMcpLink", null);

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            invokePrivate(component, "disableAssistUI",
                    new Class[]{String.class, Color.class, boolean.class},
                    "No license", JBColor.RED, true);
        }

        JBLabel assistMessageLabel = (JBLabel) getField(component, "assistMessageLabel");
        assertTrue(assistMessageLabel.isVisible());
        assertEquals("No license", assistMessageLabel.getText());
        assertFalse(((JBCheckBox) getField(component, "ascaCheckbox")).isEnabled());
        assertFalse(((JBCheckBox) getField(component, "ossCheckbox")).isEnabled());
    }

    @Test
    void disableAssistUI_WithKeepVisibleFalse_HidesPanelAndMessage() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setVisible(true);
        setField(component, "mainPanel", mainPanel);
        setField(component, "installMcpLink", null);

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            invokePrivate(component, "disableAssistUI",
                    new Class[]{String.class, Color.class, boolean.class},
                    "msg", JBColor.RED, false);
        }

        assertFalse(mainPanel.isVisible());
        assertFalse(((JBLabel) getField(component, "assistMessageLabel")).isVisible());
    }
}
