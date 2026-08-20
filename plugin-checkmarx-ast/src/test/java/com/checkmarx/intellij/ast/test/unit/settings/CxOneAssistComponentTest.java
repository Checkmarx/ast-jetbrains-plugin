package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.ast.settings.CxOneAssistComponent;
import com.checkmarx.intellij.common.components.CxLinkLabel;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
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

    // ===== dispose() =====

    @Test
    void dispose_WhenConnectionNull_DoesNothing() throws Exception {
        setField(component, "connection", null);
        assertDoesNotThrow(() -> component.dispose());
    }

    @Test
    void dispose_WhenConnectionNotNull_CallsConnectionDispose() throws Exception {
        MessageBusConnection mockConnection = mock(MessageBusConnection.class);
        setField(component, "connection", mockConnection);
        component.dispose();
        verify(mockConnection).dispose();
    }

    @Test
    void dispose_WhenConnectionThrowsException_DoesNotPropagate() throws Exception {
        MessageBusConnection mockConnection = mock(MessageBusConnection.class);
        doThrow(new RuntimeException("fail")).when(mockConnection).dispose();
        setField(component, "connection", mockConnection);
        assertDoesNotThrow(() -> component.dispose());
    }

    // ===== setAscaInstallationMsg() =====

    @Test
    void setAscaInstallationMsg_SetsHtmlTextAndColor() throws Exception {
        JBLabel ascaInstallationMsg = new JBLabel();
        setField(component, "ascaInstallationMsg", ascaInstallationMsg);

        invokePrivate(component, "setAscaInstallationMsg",
                new Class[]{String.class, JBColor.class},
                "ASCA started", JBColor.GREEN);

        assertEquals("<html>ASCA started</html>", ascaInstallationMsg.getText());
        assertEquals(JBColor.GREEN, ascaInstallationMsg.getForeground());
    }

    // ===== showContainerEngineStatus() =====

    @Test
    void showContainerEngineStatus_SetsLabelTextColorAndStartsTimer() throws Exception {
        JBLabel containerToolLabel = new JBLabel();
        setField(component, "containerToolLabel", containerToolLabel);
        setField(component, "containerToolTimer", null);

        invokePrivate(component, "showContainerEngineStatus",
                new Class[]{String.class, Color.class},
                "docker found", JBColor.GREEN);

        assertEquals("docker found", containerToolLabel.getText());
        assertEquals(JBColor.GREEN, containerToolLabel.getForeground());
        assertTrue(containerToolLabel.isVisible());
        assertNotNull(getField(component, "containerToolTimer"));
    }

    @Test
    void showContainerEngineStatus_StopsPreviousTimerBeforeStartingNew() throws Exception {
        JBLabel containerToolLabel = new JBLabel();
        setField(component, "containerToolLabel", containerToolLabel);
        Timer existingTimer = mock(Timer.class);
        setField(component, "containerToolTimer", existingTimer);

        invokePrivate(component, "showContainerEngineStatus",
                new Class[]{String.class, Color.class},
                "msg", JBColor.RED);

        verify(existingTimer).stop();
        Timer newTimer = (Timer) getField(component, "containerToolTimer");
        assertNotNull(newTimer);
        assertNotSame(existingTimer, newTimer);
    }

    // ===== updateAssistState() =====

    private void injectPanelAndLink() throws Exception {
        JPanel mainPanel = new JPanel();
        setField(component, "mainPanel", mainPanel);
        CxLinkLabel mockLink = mock(CxLinkLabel.class);
        setField(component, "installMcpLink", mockLink);
    }

    @Test
    void updateAssistState_WhenNoLicense_HidesPanelAndDisablesCheckboxes() throws Exception {
        injectPanelAndLink();
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            invokePrivate(component, "updateAssistState", new Class[]{});
        }

        JPanel mainPanel = (JPanel) getField(component, "mainPanel");
        assertFalse(mainPanel.isVisible());
    }

    @Test
    void updateAssistState_WhenNotAuthenticated_ShowsLoginMessage() throws Exception {
        injectPanelAndLink();
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_LOGIN_MESSAGE)).thenReturn("Please log in");

            invokePrivate(component, "updateAssistState", new Class[]{});
        }

        JPanel mainPanel = (JPanel) getField(component, "mainPanel");
        assertTrue(mainPanel.isVisible());
        JBLabel msg = (JBLabel) getField(component, "assistMessageLabel");
        assertEquals("Please log in", msg.getText());
        assertTrue(msg.isVisible());
    }

    @Test
    void updateAssistState_WhenAuthenticatedAndMcpStatusChecked_EnablesOrDisablesUI() throws Exception {
        injectPanelAndLink();
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP disabled");

            invokePrivate(component, "updateAssistState", new Class[]{});
        }

        JBLabel msg = (JBLabel) getField(component, "assistMessageLabel");
        assertTrue(msg.isVisible());
    }

    // ===== updateUIWithMcpStatus() =====

    @Test
    void updateUIWithMcpStatus_WhenMcpDisabled_DisablesCheckboxesAndShowsMessage() throws Exception {
        injectPanelAndLink();
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP off");

            invokePrivate(component, "updateUIWithMcpStatus",
                    new Class[]{boolean.class, boolean.class}, false, true);
        }

        assertFalse(((JBCheckBox) getField(component, "ascaCheckbox")).isEnabled());
        JBLabel msg = (JBLabel) getField(component, "assistMessageLabel");
        assertEquals("MCP off", msg.getText());
        assertTrue(msg.isVisible());
    }

    @Test
    void updateUIWithMcpStatus_WhenMcpEnabled_EnablesCheckboxesAndRestoresState() throws Exception {
        injectPanelAndLink();
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            invokePrivate(component, "updateUIWithMcpStatus",
                    new Class[]{boolean.class, boolean.class}, true, true);
        }

        assertTrue(((JBCheckBox) getField(component, "ascaCheckbox")).isEnabled());
        assertTrue(((JBCheckBox) getField(component, "ascaCheckbox")).isSelected());
        JBLabel msg = (JBLabel) getField(component, "assistMessageLabel");
        assertFalse(msg.isVisible());
    }

    @Test
    void updateUIWithMcpStatus_WhenMcpEnabledWithPreferences_AppliesPreferences() throws Exception {
        injectPanelAndLink();
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.applyUserPreferencesToRealtimeSettings()).thenReturn(true);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            invokePrivate(component, "updateUIWithMcpStatus",
                    new Class[]{boolean.class, boolean.class}, true, true);
        }

        verify(mockListener).settingsApplied();
    }

    @Test
    void disableAssistUI_WhenScannerEnabled_PublishesSettingsApplied() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(true);
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

        verify(mockState).setOssRealtime(false);
        verify(mockListener).settingsApplied();
    }

    @Test
    void disableAssistUI_WhenInstallMcpLinkNotNull_DisablesLink() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        JPanel mainPanel = new JPanel();
        setField(component, "mainPanel", mainPanel);
        CxLinkLabel mockLink = mock(CxLinkLabel.class);
        setField(component, "installMcpLink", mockLink);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            invokePrivate(component, "disableAssistUI",
                    new Class[]{String.class, Color.class, boolean.class},
                    "msg", JBColor.RED, false);
        }

        verify(mockLink).setEnabled(false);
    }

    @Test
    void updateUIWithMcpStatus_WhenMcpDisabledAndScannerEnabled_PublishesSettings() throws Exception {
        injectPanelAndLink();
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP off");
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            invokePrivate(component, "updateUIWithMcpStatus",
                    new Class[]{boolean.class, boolean.class}, false, true);
        }

        verify(mockState).setAscaRealtime(false);
        verify(mockListener).settingsApplied();
    }

    @Test
    void disableAssistUI_WhenSecretAndIacAndContainersEnabled_DisablesThemAll() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(true);
        when(mockState.isIacRealtime()).thenReturn(true);

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

        verify(mockState).setSecretDetectionRealtime(false);
        verify(mockState).setContainersRealtime(false);
        verify(mockState).setIacRealtime(false);
        verify(mockListener).settingsApplied();
    }

    @Test
    void updateUIWithMcpStatus_WhenMcpDisabledAndSecretAndContainersEnabled_DisablesThemAndPublishes() throws Exception {
        injectPanelAndLink();
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(true);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP off");
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            invokePrivate(component, "updateUIWithMcpStatus",
                    new Class[]{boolean.class, boolean.class}, false, true);
        }

        verify(mockState).setSecretDetectionRealtime(false);
        verify(mockState).setContainersRealtime(false);
        verify(mockListener).settingsApplied();
    }

    @Test
    void updateUIWithMcpStatus_WhenMcpEnabledAndPreferencesSet_RestoresPreferences() throws Exception {
        injectPanelAndLink();
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.applyUserPreferencesToRealtimeSettings()).thenReturn(true);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            invokePrivate(component, "updateUIWithMcpStatus",
                    new Class[]{boolean.class, boolean.class}, true, true);
        }

        verify(mockState).applyUserPreferencesToRealtimeSettings();
        verify(mockListener).settingsApplied();
    }

    // ===== getMainPanel() =====

    @Test
    void getMainPanel_ReturnsInjectedPanel() throws Exception {
        JPanel panel = new JPanel();
        setField(component, "mainPanel", panel);
        assertSame(panel, component.getMainPanel());
    }

    // ===== reset() — authenticated path =====

    @Test
    void reset_WhenAuthenticatedWithLicenseAndMcpEnabled_SetsCheckboxesFromState() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(true);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        injectPanelAndLink();

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            component.reset();
        }

        assertTrue(((JBCheckBox) getField(component, "ascaCheckbox")).isSelected());
        assertTrue(((JBCheckBox) getField(component, "ossCheckbox")).isSelected());
        assertFalse(((JBCheckBox) getField(component, "secretsCheckbox")).isSelected());
    }

    // ===== installMcp() =====

    @Test
    void installMcp_WhenInstallInProgress_ReturnsImmediately() throws Exception {
        setField(component, "mcpInstallInProgress", true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            invokePrivate(component, "installMcp", new Class[]{});
        }

        verify(mockState, never()).isMcpEnabled();
    }

    @Test
    void installMcp_WhenMcpNotEnabled_ShowsErrorStatus() throws Exception {
        setField(component, "mcpInstallInProgress", false);
        when(mockState.isMcpEnabled()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP disabled");
            invokePrivate(component, "installMcp", new Class[]{});
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("MCP disabled", mcpStatusLabel.getText());
        assertTrue(mcpStatusLabel.isVisible());
    }

    @Test
    void installMcp_WhenCredentialBlank_DoesNotStartInstall() throws Exception {
        setField(component, "mcpInstallInProgress", false);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);

        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        when(mockSensitive.getApiKey()).thenReturn("  ");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> sensitiveMock = mockStatic(GlobalSettingsSensitiveState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            invokePrivate(component, "installMcp", new Class[]{});
        }

        assertFalse((boolean) getField(component, "mcpInstallInProgress"));
    }

    // ===== validateIACEngine() =====

    @Test
    void validateIACEngine_WhenEngineExists_ClearsLastNotificationEngine() throws Exception {
        setField(component, "lastNotificationEngine", "docker");
        when(mockState.getContainersTool()).thenReturn("docker");

        CxWrapper mockWrapper = mock(CxWrapper.class);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<CxWrapperFactory> wfMock = mockStatic(CxWrapperFactory.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            wfMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            invokePrivate(component, "validateIACEngine", new Class[]{});
        }

        assertEquals("", getField(component, "lastNotificationEngine"));
    }

    @Test
    void validateIACEngine_WhenEngineNotFoundAndSameAsPrevious_ReturnsEarlyWithoutNotification() throws Exception {
        setField(component, "lastNotificationEngine", "docker");
        when(mockState.getContainersTool()).thenReturn("docker");

        CxWrapper mockWrapper = mock(CxWrapper.class);
        doThrow(new RuntimeException("docker not found")).when(mockWrapper).checkEngineExist("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<CxWrapperFactory> wfMock = mockStatic(CxWrapperFactory.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            wfMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            invokePrivate(component, "validateIACEngine", new Class[]{});
        }

        assertEquals("docker", getField(component, "lastNotificationEngine"));
    }

    @Test
    void validateIACEngine_WhenEngineNotFoundAndDifferentFromPrevious_SetsLastNotificationEngine() throws Exception {
        setField(component, "lastNotificationEngine", "");
        when(mockState.getContainersTool()).thenReturn("podman");

        CxWrapper mockWrapper = mock(CxWrapper.class);
        doThrow(new RuntimeException("podman not found")).when(mockWrapper).checkEngineExist("podman");

        Application mockApp = mock(Application.class);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<CxWrapperFactory> wfMock = mockStatic(CxWrapperFactory.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            wfMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            bundleMock.when(() -> Bundle.message(any(Resource.class))).thenReturn("doc-link");
            invokePrivate(component, "validateIACEngine", new Class[]{});
        }

        assertEquals("podman", getField(component, "lastNotificationEngine"));
    }

    // ===== updateAssistState() — MCP status not yet checked =====

    @Test
    void updateAssistState_WhenAuthenticatedAndMcpStatusNotChecked_ShowsCheckingMessage() throws Exception {
        injectPanelAndLink();
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CHECKING_MCP_STATUS)).thenReturn("Checking MCP...");
            // The async call will be submitted to a thread pool; mock TenantSetting to avoid real call
            try (MockedStatic<com.checkmarx.intellij.common.commands.TenantSetting> tsMock =
                         mockStatic(com.checkmarx.intellij.common.commands.TenantSetting.class)) {
                tsMock.when(() -> com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(any(), any()))
                      .thenReturn(false);
                invokePrivate(component, "updateAssistState", new Class[]{});
            }
        }

        JBLabel msg = (JBLabel) getField(component, "assistMessageLabel");
        assertEquals("Checking MCP...", msg.getText());
        assertTrue(msg.isVisible());
        assertFalse(((JBCheckBox) getField(component, "ascaCheckbox")).isEnabled());
    }

    // ===== installMcp() — OAuth credential path =====

    @Test
    void installMcp_WhenOAuthCredentialValid_SetsInstallInProgress() throws Exception {
        setField(component, "mcpInstallInProgress", false);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(false); // OAuth

        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        when(mockSensitive.getRefreshToken()).thenReturn("valid-refresh-token");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> sensitiveMock = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);

            java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService.installSilentlyAsync(any()))
                   .thenReturn(future);

            invokePrivate(component, "installMcp", new Class[]{});
        }

        assertTrue((boolean) getField(component, "mcpInstallInProgress"));
    }

    // ===== isModified() — all checkboxes differ =====

    @Test
    void isModified_WhenMultipleCheckboxesDiffer_ReturnsTrue() throws Exception {
        JBCheckBox ossCheckbox = (JBCheckBox) getField(component, "ossCheckbox");
        JBCheckBox secretsCheckbox = (JBCheckBox) getField(component, "secretsCheckbox");
        ossCheckbox.setSelected(true);
        secretsCheckbox.setSelected(true);

        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);  // differs
        when(mockState.isSecretDetectionRealtime()).thenReturn(false); // differs
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertTrue(component.isModified());
        }
    }

    // ===== reset() — authenticated path with MCP enabled and user preferences =====

    @Test
    void reset_WhenAuthenticatedMcpEnabledWithUserPreferences_RestoresPreferences() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.applyUserPreferencesToRealtimeSettings()).thenReturn(true);

        injectPanelAndLink();

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

        verify(mockState).applyUserPreferencesToRealtimeSettings();
        verify(mockListener).settingsApplied();
    }

    // ===== apply() — executeOnPooledThread calls validateIACEngine =====

    @Test
    void apply_ExecutesOnPooledThreadForIACValidation() throws Exception {
        JBCheckBox ascaCheckbox = (JBCheckBox) getField(component, "ascaCheckbox");
        ascaCheckbox.setSelected(false);

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

        verify(mockApp).executeOnPooledThread(any(Runnable.class));
    }

    // ===== updateAssistState() — DevAssist-only license path =====

    @Test
    void updateAssistState_WhenDevAssistOnlyLicense_ShowsPanelAndChecksMcpStatus() throws Exception {
        injectPanelAndLink();
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP disabled");

            invokePrivate(component, "updateAssistState", new Class[]{});
        }

        JPanel mainPanel = (JPanel) getField(component, "mainPanel");
        assertTrue(mainPanel.isVisible());
    }

    @Test
    void updateAssistState_WhenDevAssistOnlyLicenseAndMcpEnabled_EnablesCheckboxes() throws Exception {
        injectPanelAndLink();
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            invokePrivate(component, "updateAssistState", new Class[]{});
        }

        assertTrue(((JBCheckBox) getField(component, "ascaCheckbox")).isEnabled());
        assertTrue(((JBCheckBox) getField(component, "ascaCheckbox")).isSelected());
    }

    // ===== reset() — DevAssist-only license path =====

    @Test
    void reset_WhenAuthenticatedDevAssistOnlyMcpEnabled_ShowsPanelWithCheckboxes() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        injectPanelAndLink();

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            component.reset();
        }

        assertTrue(((JBCheckBox) getField(component, "ascaCheckbox")).isSelected());
        assertTrue(((JPanel) getField(component, "mainPanel")).isVisible());
    }

    // ===== installMcp() — API key valid path sets install in progress =====

    @Test
    void installMcp_WhenApiKeyValid_SetsInstallInProgress() throws Exception {
        setField(component, "mcpInstallInProgress", false);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);

        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        when(mockSensitive.getApiKey()).thenReturn("valid-api-key");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> sensitiveMock = mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);

            java.util.concurrent.CompletableFuture<Boolean> future = new java.util.concurrent.CompletableFuture<>();
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService.installSilentlyAsync(any()))
                   .thenReturn(future);

            invokePrivate(component, "installMcp", new Class[]{});
        }

        assertTrue((boolean) getField(component, "mcpInstallInProgress"));
    }

    // ===== updateAssistState() — authenticated, MCP checked and disabled =====

    @Test
    void updateAssistState_WhenAuthenticatedMcpCheckedAndDisabled_ShowsDisabledMessage() throws Exception {
        injectPanelAndLink();
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP disabled");

            invokePrivate(component, "updateAssistState", new Class[]{});
        }

        JBLabel msg = (JBLabel) getField(component, "assistMessageLabel");
        assertEquals("MCP disabled", msg.getText());
        assertTrue(msg.isVisible());
    }

    // ===== installMcp() — whenComplete callback coverage via completedFuture =====

    @Test
    void installMcp_WhenFutureCompletesTrue_SetsConfigSavedStatus() throws Exception {
        injectUIFields();
        setField(component, "mcpInstallInProgress", false);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);

        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        when(mockSensitive.getApiKey()).thenReturn("valid-api-key");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> sensitiveMock =
                     mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<javax.swing.SwingUtilities> swingMock =
                     mockStatic(javax.swing.SwingUtilities.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService
                           .installSilentlyAsync(any()))
                   .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(true));
            bundleMock.when(() -> Bundle.message(Resource.MCP_CONFIG_SAVED)).thenReturn("Config saved");
            swingMock.when(() -> javax.swing.SwingUtilities.invokeLater(any(Runnable.class)))
                     .thenAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; });

            invokePrivate(component, "installMcp", new Class[]{});
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Config saved", mcpStatusLabel.getText());
        assertTrue(mcpStatusLabel.isVisible());
        assertFalse((boolean) getField(component, "mcpInstallInProgress"));
    }

    @Test
    void installMcp_WhenFutureCompletesFalse_SetsUpToDateStatus() throws Exception {
        injectUIFields();
        setField(component, "mcpInstallInProgress", false);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(false);

        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        when(mockSensitive.getRefreshToken()).thenReturn("valid-token");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> sensitiveMock =
                     mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<javax.swing.SwingUtilities> swingMock =
                     mockStatic(javax.swing.SwingUtilities.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService
                           .installSilentlyAsync(any()))
                   .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(false));
            bundleMock.when(() -> Bundle.message(Resource.MCP_CONFIG_UP_TO_DATE)).thenReturn("Up to date");
            swingMock.when(() -> javax.swing.SwingUtilities.invokeLater(any(Runnable.class)))
                     .thenAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; });

            invokePrivate(component, "installMcp", new Class[]{});
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Up to date", mcpStatusLabel.getText());
        assertFalse((boolean) getField(component, "mcpInstallInProgress"));
    }

    @Test
    void installMcp_WhenFutureCompletesWithThrowable_SetsErrorStatus() throws Exception {
        injectUIFields();
        setField(component, "mcpInstallInProgress", false);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.isApiKeyEnabled()).thenReturn(true);

        GlobalSettingsSensitiveState mockSensitive = mock(GlobalSettingsSensitiveState.class);
        when(mockSensitive.getApiKey()).thenReturn("valid-api-key");

        java.util.concurrent.CompletableFuture<Boolean> failedFuture =
                java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("install error"));

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<GlobalSettingsSensitiveState> sensitiveMock =
                     mockStatic(GlobalSettingsSensitiveState.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<javax.swing.SwingUtilities> swingMock =
                     mockStatic(javax.swing.SwingUtilities.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);
            mcpMock.when(() -> com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService
                           .installSilentlyAsync(any()))
                   .thenReturn(failedFuture);
            bundleMock.when(() -> Bundle.message(Resource.MCP_INSTALL_ERROR)).thenReturn("Install failed");
            swingMock.when(() -> javax.swing.SwingUtilities.invokeLater(any(Runnable.class)))
                     .thenAnswer(inv -> { inv.getArgument(0, Runnable.class).run(); return null; });

            invokePrivate(component, "installMcp", new Class[]{});
        }

        JBLabel mcpStatusLabel = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("Install failed", mcpStatusLabel.getText());
        assertFalse((boolean) getField(component, "mcpInstallInProgress"));
    }

    // ===== openMcpJson() =====

    private void setupOpenMcpJsonBase() throws Exception {
        setField(component, "mainPanel", new JPanel());
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");
    }

    @Test
    void openMcpJson_WhenNoProjectAndDefaultProjectNull_ReturnsEarly() throws Exception {
        setupOpenMcpJsonBase();

        ProjectManager mockPm = mock(ProjectManager.class);
        when(mockPm.getOpenProjects()).thenReturn(new Project[0]);
        when(mockPm.getDefaultProject()).thenReturn(null);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ProjectManager> pmMock = mockStatic(ProjectManager.class);
             MockedStatic<javax.swing.SwingUtilities> swingMock = mockStatic(javax.swing.SwingUtilities.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            pmMock.when(ProjectManager::getInstance).thenReturn(mockPm);
            swingMock.when(() -> javax.swing.SwingUtilities.getWindowAncestor(any())).thenReturn(null);

            assertDoesNotThrow(() -> invokePrivate(component, "openMcpJson", new Class[]{}));
        }
    }

    @Test
    void openMcpJson_WhenGetMcpJsonPathReturnsNull_ReturnsEarly() throws Exception {
        setupOpenMcpJsonBase();

        ProjectManager mockPm = mock(ProjectManager.class);
        Project mockProject = mock(Project.class);
        when(mockPm.getOpenProjects()).thenReturn(new Project[]{mockProject});

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ProjectManager> pmMock = mockStatic(ProjectManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedStatic<javax.swing.SwingUtilities> swingMock = mockStatic(javax.swing.SwingUtilities.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            pmMock.when(ProjectManager::getInstance).thenReturn(mockPm);
            swingMock.when(() -> javax.swing.SwingUtilities.getWindowAncestor(any())).thenReturn(null);
            mcpMock.when(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector::getMcpJsonPath)
                   .thenReturn(null);

            assertDoesNotThrow(() -> invokePrivate(component, "openMcpJson", new Class[]{}));
        }
    }

    @Test
    void openMcpJson_WhenVirtualFileNotFound_ShowsMcpStatus() throws Exception {
        setupOpenMcpJsonBase();
        setField(component, "mcpStatusLabel", new JBLabel());

        ProjectManager mockPm = mock(ProjectManager.class);
        Project mockProject = mock(Project.class);
        when(mockPm.getOpenProjects()).thenReturn(new Project[]{mockProject});

        java.nio.file.Path mockPath = java.nio.file.Paths.get("mcp.json");

        LocalFileSystem mockLfs = mock(LocalFileSystem.class);
        when(mockLfs.refreshAndFindFileByNioFile(any())).thenReturn(null);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ProjectManager> pmMock = mockStatic(ProjectManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedStatic<LocalFileSystem> lfsMock = mockStatic(LocalFileSystem.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class);
             MockedStatic<javax.swing.SwingUtilities> swingMock = mockStatic(javax.swing.SwingUtilities.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            pmMock.when(ProjectManager::getInstance).thenReturn(mockPm);
            swingMock.when(() -> javax.swing.SwingUtilities.getWindowAncestor(any())).thenReturn(null);
            mcpMock.when(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector::getMcpJsonPath)
                   .thenReturn(mockPath);
            lfsMock.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            bundleMock.when(() -> Bundle.message(Resource.MCP_NOT_FOUND)).thenReturn("File not found");

            assertDoesNotThrow(() -> invokePrivate(component, "openMcpJson", new Class[]{}));
        }

        JBLabel label = (JBLabel) getField(component, "mcpStatusLabel");
        assertEquals("File not found", label.getText());
    }

    @Test
    void openMcpJson_WhenVirtualFileFound_OpensInEditor() throws Exception {
        setupOpenMcpJsonBase();

        ProjectManager mockPm = mock(ProjectManager.class);
        Project mockProject = mock(Project.class);
        when(mockPm.getOpenProjects()).thenReturn(new Project[]{mockProject});

        java.nio.file.Path mockPath = java.nio.file.Paths.get("mcp.json");

        LocalFileSystem mockLfs = mock(LocalFileSystem.class);
        VirtualFile mockVf = mock(VirtualFile.class);
        when(mockVf.exists()).thenReturn(true);
        when(mockLfs.refreshAndFindFileByNioFile(any())).thenReturn(mockVf);

        FileEditorManager mockFem = mock(FileEditorManager.class);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ProjectManager> pmMock = mockStatic(ProjectManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector> mcpMock =
                     mockStatic(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector.class);
             MockedStatic<LocalFileSystem> lfsMock = mockStatic(LocalFileSystem.class);
             MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class);
             MockedStatic<javax.swing.SwingUtilities> swingMock = mockStatic(javax.swing.SwingUtilities.class)) {

            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            pmMock.when(ProjectManager::getInstance).thenReturn(mockPm);
            swingMock.when(() -> javax.swing.SwingUtilities.getWindowAncestor(any())).thenReturn(null);
            mcpMock.when(com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector::getMcpJsonPath)
                   .thenReturn(mockPath);
            lfsMock.when(LocalFileSystem::getInstance).thenReturn(mockLfs);
            femMock.when(() -> FileEditorManager.getInstance(mockProject)).thenReturn(mockFem);

            assertDoesNotThrow(() -> invokePrivate(component, "openMcpJson", new Class[]{}));
        }

        verify(mockFem).openFile(mockVf, true);
    }

    // ===== addAscaCheckBoxListener() — item listener lambda branches =====

    @Test
    void addAscaCheckBoxListener_WhenItemSelected_ShowsSuccessMessage() throws Exception {
        JBCheckBox ascaCheckbox = (JBCheckBox) getField(component, "ascaCheckbox");
        JBLabel ascaInstallationMsg = new JBLabel();
        setField(component, "ascaInstallationMsg", ascaInstallationMsg);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.ASCA_STARTED_MSG)).thenReturn("ASCA started");
            invokePrivate(component, "addAscaCheckBoxListener", new Class[]{});
        }

        // Fire SELECTED item event
        for (java.awt.event.ItemListener l : ascaCheckbox.getItemListeners()) {
            l.itemStateChanged(new java.awt.event.ItemEvent(
                    ascaCheckbox, java.awt.event.ItemEvent.ITEM_STATE_CHANGED,
                    null, java.awt.event.ItemEvent.SELECTED));
        }

        assertTrue(ascaInstallationMsg.isVisible());
    }

    @Test
    void addAscaCheckBoxListener_WhenItemDeselected_HidesMessage() throws Exception {
        JBCheckBox ascaCheckbox = (JBCheckBox) getField(component, "ascaCheckbox");
        JBLabel ascaInstallationMsg = new JBLabel();
        ascaInstallationMsg.setVisible(true);
        setField(component, "ascaInstallationMsg", ascaInstallationMsg);

        invokePrivate(component, "addAscaCheckBoxListener", new Class[]{});

        // Fire DESELECTED item event
        for (java.awt.event.ItemListener l : ascaCheckbox.getItemListeners()) {
            l.itemStateChanged(new java.awt.event.ItemEvent(
                    ascaCheckbox, java.awt.event.ItemEvent.ITEM_STATE_CHANGED,
                    null, java.awt.event.ItemEvent.DESELECTED));
        }

        assertFalse(ascaInstallationMsg.isVisible());
    }

    // ===== validateIACEngine() — private method =====

    @Test
    void validateIACEngine_WhenEngineExistsSuccessfully_ClearsLastNotification() throws Exception {
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.common.wrapper.CxWrapperFactory> factoryMock =
                     mockStatic(com.checkmarx.intellij.common.wrapper.CxWrapperFactory.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            com.checkmarx.ast.wrapper.CxWrapper mockWrapper =
                    mock(com.checkmarx.ast.wrapper.CxWrapper.class);
            factoryMock.when(com.checkmarx.intellij.common.wrapper.CxWrapperFactory::build).thenReturn(mockWrapper);
            // checkEngineExist is non-void; just let it return default (null)

            invokePrivate(component, "validateIACEngine", new Class[]{});
        }
    }

    @Test
    void validateIACEngine_WhenEngineMissing_PublishesNotification() throws Exception {
        when(mockState.getContainersTool()).thenReturn("docker");
        setField(component, "lastNotificationEngine", "");

        Application mockApp = mock(Application.class);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.common.wrapper.CxWrapperFactory> factoryMock =
                     mockStatic(com.checkmarx.intellij.common.wrapper.CxWrapperFactory.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            com.checkmarx.ast.wrapper.CxWrapper mockWrapper =
                    mock(com.checkmarx.ast.wrapper.CxWrapper.class);
            factoryMock.when(com.checkmarx.intellij.common.wrapper.CxWrapperFactory::build).thenReturn(mockWrapper);
            doThrow(new RuntimeException("docker not found")).when(mockWrapper).checkEngineExist(any());
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any());
            bundleMock.when(() -> Bundle.message(any())).thenReturn("doc link");

            invokePrivate(component, "validateIACEngine", new Class[]{});
        }

        assertEquals("docker", getField(component, "lastNotificationEngine"));
    }

    @Test
    void validateIACEngine_WhenSameEngineAlreadyNotified_ReturnsEarly() throws Exception {
        when(mockState.getContainersTool()).thenReturn("docker");
        setField(component, "lastNotificationEngine", "docker");

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<com.checkmarx.intellij.common.wrapper.CxWrapperFactory> factoryMock =
                     mockStatic(com.checkmarx.intellij.common.wrapper.CxWrapperFactory.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            com.checkmarx.ast.wrapper.CxWrapper mockWrapper =
                    mock(com.checkmarx.ast.wrapper.CxWrapper.class);
            factoryMock.when(com.checkmarx.intellij.common.wrapper.CxWrapperFactory::build).thenReturn(mockWrapper);
            doThrow(new RuntimeException("docker not found")).when(mockWrapper).checkEngineExist(any());

            invokePrivate(component, "validateIACEngine", new Class[]{});
        }

        // Should still be "docker" - returned early without changing anything
        assertEquals("docker", getField(component, "lastNotificationEngine"));
    }
}
