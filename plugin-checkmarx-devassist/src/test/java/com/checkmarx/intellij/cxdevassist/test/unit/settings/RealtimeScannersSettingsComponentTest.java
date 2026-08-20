package com.checkmarx.intellij.cxdevassist.test.unit.settings;

import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.components.CxLinkLabel;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.checkmarx.intellij.cxdevassist.settings.RealtimeScannersSettingsComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.swing.*;
import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class RealtimeScannersSettingsComponentTest {

    private GlobalSettingsState mockState;
    private RealtimeScannersSettingsComponent component;
    private MockedStatic<GlobalSettingsState> mockedGlobalState;

    // ---- Reflection helpers ----

    private static RealtimeScannersSettingsComponent newInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (RealtimeScannersSettingsComponent) unsafe.allocateInstance(RealtimeScannersSettingsComponent.class);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private void injectCheckboxes() throws Exception {
        setField(component, "ascaCheckbox", new JBCheckBox());
        setField(component, "ossCheckbox", new JBCheckBox());
        setField(component, "secretsCheckbox", new JBCheckBox());
        setField(component, "containersCheckbox", new JBCheckBox());
        setField(component, "iacCheckbox", new JBCheckBox());
        setField(component, "containersToolCombo", new ComboBox<>(new String[]{"docker", "podman"}));
        setField(component, "assistMessageLabel", new com.intellij.ui.components.JBLabel());
        setField(component, "mainPanel", new JPanel());

        // CxLinkLabel extends HyperlinkLabel (IntelliJ platform) — mock to avoid Swing peer init
        setField(component, "installMcpLink", mock(CxLinkLabel.class));
    }

    // ---- Test lifecycle ----

    @BeforeEach
    void setUp() throws Exception {
        mockState = mock(GlobalSettingsState.class);
        mockedGlobalState = mockStatic(GlobalSettingsState.class);
        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(mockState);

        component = newInstanceWithoutConstructor();
        injectCheckboxes();
        setField(component, "state", mockState);
    }

    @AfterEach
    void tearDown() {
        mockedGlobalState.close();
    }

    // ===== isModified() =====

    @Test
    void isModified_WhenCheckboxesMatchState_ReturnsFalse() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        assertFalse(component.isModified());
    }

    @Test
    void isModified_WhenAscaCheckboxDiffersFromState_ReturnsTrue() throws Exception {
        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        ascaCheckbox.setSelected(true);

        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        assertTrue(component.isModified());
    }

    @Test
    void isModified_WhenContainersToolDiffersFromState_ReturnsTrue() throws Exception {
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("podman");

        assertTrue(component.isModified());
    }

    // ===== apply() =====

    @Test
    void apply_SetsStateFlagsAndPublishesSettingsEvent() throws Exception {
        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        JBCheckBox ossCheckbox = getField(component, "ossCheckbox");
        JBCheckBox secretsCheckbox = getField(component, "secretsCheckbox");
        JBCheckBox containersCheckbox = getField(component, "containersCheckbox");
        JBCheckBox iacCheckbox = getField(component, "iacCheckbox");
        ascaCheckbox.setSelected(true);
        ossCheckbox.setSelected(true);
        secretsCheckbox.setSelected(false);
        containersCheckbox.setSelected(false);
        iacCheckbox.setSelected(true);

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        // executeOnPooledThread returns Future<?>, not void — use thenReturn instead of doNothing
        when(mockApp.executeOnPooledThread(any(Runnable.class))).thenReturn(null);

        try (MockedStatic<ApplicationManager> appManagerMock = mockStatic(ApplicationManager.class)) {
            appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            assertDoesNotThrow(() -> component.apply());
        }

        verify(mockState).setAscaRealtime(true);
        verify(mockState).setOssRealtime(true);
        verify(mockState).setSecretDetectionRealtime(false);
        verify(mockState).setContainersRealtime(false);
        verify(mockState).setIacRealtime(true);
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
        when(mockState.getContainersTool()).thenReturn("podman");
        // Authenticated + has license + MCP checked/enabled so updateUIWithMcpStatus sets checkboxes from state
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        component.reset();

        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        JBCheckBox ossCheckbox = getField(component, "ossCheckbox");
        JBCheckBox containersCheckbox = getField(component, "containersCheckbox");

        assertTrue(ascaCheckbox.isSelected());
        assertTrue(ossCheckbox.isSelected());
        assertTrue(containersCheckbox.isSelected());
    }

    @Test
    void reset_WhenAscaLegacyFlagSet_CheckboxReflectsIt() throws Exception {
        // isAscaRealtime=false but isAsca=true (legacy flag). reset() reads the OR of both,
        // but updateUIWithMcpStatus subsequently overrides with isAscaRealtime only.
        // This test documents that post-reset the checkbox reflects the realtime flag, not legacy.
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(true);
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
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        component.reset();

        // updateUIWithMcpStatus sets ascaCheckbox from isAscaRealtime() which is false,
        // overriding the legacy isAsca() flag set in the initial lines of reset().
        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        assertFalse(ascaCheckbox.isSelected());
    }

    // ===== getMainPanel() =====

    @Test
    void getMainPanel_ReturnsInjectedPanel() throws Exception {
        JPanel panel = getField(component, "mainPanel");
        assertNotNull(panel);
        assertSame(panel, component.getMainPanel());
    }

    // ===== formatTitle() — static, via reflection =====

    private static String invokeFormatTitle(String raw) throws Exception {
        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("formatTitle", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }

    @Test
    void formatTitle_WithNull_ReturnsEmptyHtml() throws Exception {
        assertEquals("<html></html>", invokeFormatTitle(null));
    }

    @Test
    void formatTitle_WithoutColon_WrapsEntireText() throws Exception {
        assertEquals("<html>SimpleTitle</html>", invokeFormatTitle("SimpleTitle"));
    }

    @Test
    void formatTitle_WithColonAndValue_BoldsTextAfterColon() throws Exception {
        String result = invokeFormatTitle("Scanner: ASCA");
        assertTrue(result.contains("<b>ASCA</b>"));
        assertTrue(result.startsWith("<html>"));
    }

    @Test
    void formatTitle_WithColonAtEnd_WrapsWithoutBold() throws Exception {
        assertEquals("<html>Heading:</html>", invokeFormatTitle("Heading:"));
    }

    // ===== ensureState() =====

    @Test
    void ensureState_RefreshesStateFromSingleton() throws Exception {
        GlobalSettingsState freshState = mock(GlobalSettingsState.class);
        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(freshState);

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("ensureState");
        m.setAccessible(true);
        m.invoke(component);

        assertSame(freshState, getField(component, "state"));
    }

    // ===== handleMcpResult() =====

    @Test
    void handleMcpResult_WhenThrowableNotNull_ShowsErrorStatus() throws Exception {
        JBLabel mcpStatusLabel = new JBLabel();
        setField(component, "mcpStatusLabel", mcpStatusLabel);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.MCP_INSTALL_ERROR)).thenReturn("Install failed");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                    "handleMcpResult", Boolean.class, Throwable.class);
            m.setAccessible(true);
            m.invoke(component, null, new RuntimeException("test"));
        }

        assertEquals("Install failed", mcpStatusLabel.getText());
        assertFalse((boolean) getField(component, "mcpInstallInProgress"));
    }

    @Test
    void handleMcpResult_WhenChangedTrue_ShowsSuccessStatus() throws Exception {
        JBLabel mcpStatusLabel = new JBLabel();
        setField(component, "mcpStatusLabel", mcpStatusLabel);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.MCP_CONFIG_SAVED)).thenReturn("Config saved");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                    "handleMcpResult", Boolean.class, Throwable.class);
            m.setAccessible(true);
            m.invoke(component, Boolean.TRUE, null);
        }

        assertEquals("Config saved", mcpStatusLabel.getText());
    }

    @Test
    void handleMcpResult_WhenChangedFalse_ShowsUpToDateStatus() throws Exception {
        JBLabel mcpStatusLabel = new JBLabel();
        setField(component, "mcpStatusLabel", mcpStatusLabel);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.MCP_CONFIG_UP_TO_DATE)).thenReturn("Up to date");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                    "handleMcpResult", Boolean.class, Throwable.class);
            m.setAccessible(true);
            m.invoke(component, Boolean.FALSE, null);
        }

        assertEquals("Up to date", mcpStatusLabel.getText());
    }

    // ===== showContainerEngineStatus() =====

    @Test
    void showContainerEngineStatus_SetsLabelTextColorAndStartsTimer() throws Exception {
        JBLabel containerToolLabel = new JBLabel();
        setField(component, "containerToolLabel", containerToolLabel);

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "showContainerEngineStatus", String.class, Color.class);
        m.setAccessible(true);
        m.invoke(component, "Engine OK", JBColor.GREEN);

        assertEquals("Engine OK", containerToolLabel.getText());
        assertTrue(containerToolLabel.isVisible());
        assertNotNull(getField(component, "containerToolTimer"));
    }

    @Test
    void showContainerEngineStatus_StopsPreviousTimerBeforeStartingNew() throws Exception {
        JBLabel containerToolLabel = new JBLabel();
        setField(component, "containerToolLabel", containerToolLabel);
        Timer existingTimer = mock(Timer.class);
        setField(component, "containerToolTimer", existingTimer);

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "showContainerEngineStatus", String.class, Color.class);
        m.setAccessible(true);
        m.invoke(component, "msg", JBColor.RED);

        verify(existingTimer).stop();
    }

    // ===== setAscaInstallationMsg() =====

    @Test
    void setAscaInstallationMsg_SetsHtmlTextAndColor() throws Exception {
        JBLabel ascaInstallationMsg = new JBLabel();
        setField(component, "ascaInstallationMsg", ascaInstallationMsg);

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "setAscaInstallationMsg", String.class, JBColor.class);
        m.setAccessible(true);
        m.invoke(component, "ASCA started", JBColor.GREEN);

        assertEquals("<html>ASCA started</html>", ascaInstallationMsg.getText());
        assertEquals(JBColor.GREEN, ascaInstallationMsg.getForeground());
    }

    // ===== disableAssistUI() =====

    /** Stubs all scanner-state getters to return false so settingsChanged stays false (no ApplicationManager call). */
    private void stubScannersAllFalse() {
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isAsca()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
    }

    @Test
    void disableAssistUI_WithKeepVisibleFalse_HidesPanelAndMessage() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        stubScannersAllFalse();

        JPanel mainPanel = getField(component, "mainPanel");
        mainPanel.setVisible(true);

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "disableAssistUI", String.class, Color.class, boolean.class);
        m.setAccessible(true);
        m.invoke(component, "msg", JBColor.RED, false);

        assertFalse(mainPanel.isVisible());
        JBLabel assistMsg = getField(component, "assistMessageLabel");
        assertFalse(assistMsg.isVisible());
    }

    @Test
    void disableAssistUI_WithKeepVisibleTrue_ShowsMessageAndDisablesCheckboxes() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        stubScannersAllFalse();

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "disableAssistUI", String.class, Color.class, boolean.class);
        m.setAccessible(true);
        m.invoke(component, "No license", JBColor.RED, true);

        JBLabel assistMsg = getField(component, "assistMessageLabel");
        assertTrue(assistMsg.isVisible());
        assertEquals("No license", assistMsg.getText());

        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        assertFalse(ascaCheckbox.isEnabled());
    }

    @Test
    void disableAssistUI_WhenPreferencesNotSet_SavesCurrentPreferences() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        stubScannersAllFalse();

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "disableAssistUI", String.class, Color.class, boolean.class);
        m.setAccessible(true);
        m.invoke(component, "msg", JBColor.RED, false);

        verify(mockState).saveCurrentSettingsAsUserPreferences();
    }

    // ===== updateUIWithMcpStatus() =====

    @Test
    void updateUIWithMcpStatus_WhenMcpDisabled_DisablesCheckboxesAndShowsMessage() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        stubScannersAllFalse();
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE))
                    .thenReturn("MCP disabled");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                    "updateUIWithMcpStatus", boolean.class, boolean.class);
            m.setAccessible(true);
            m.invoke(component, false, true);
        }

        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        assertFalse(ascaCheckbox.isEnabled());
        assertFalse(ascaCheckbox.isSelected());
        JBLabel assistMsg = getField(component, "assistMessageLabel");
        assertTrue(assistMsg.isVisible());
    }

    @Test
    void updateUIWithMcpStatus_WhenMcpEnabled_EnablesCheckboxesAndRestoresState() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "updateUIWithMcpStatus", boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(component, true, true);

        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        JBCheckBox ossCheckbox = getField(component, "ossCheckbox");
        assertTrue(ascaCheckbox.isEnabled());
        assertTrue(ossCheckbox.isEnabled());
        assertTrue(ascaCheckbox.isSelected());
        assertFalse(ossCheckbox.isSelected());
        JBLabel assistMsg = getField(component, "assistMessageLabel");
        assertFalse(assistMsg.isVisible());
    }

    @Test
    void updateUIWithMcpStatus_WhenMcpEnabledWithPreferences_AppliesPreferences() throws Exception {
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.applyUserPreferencesToRealtimeSettings()).thenReturn(false);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);
        when(mockState.getContainersTool()).thenReturn("docker");

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod(
                "updateUIWithMcpStatus", boolean.class, boolean.class);
        m.setAccessible(true);
        m.invoke(component, true, true);

        verify(mockState).applyUserPreferencesToRealtimeSettings();
    }

    // ===== updateAssistState() =====

    @Test
    void updateAssistState_WhenNoLicense_HidesPanel() throws Exception {
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        stubScannersAllFalse();

        JPanel mainPanel = getField(component, "mainPanel");
        mainPanel.setVisible(true);

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("updateAssistState");
        m.setAccessible(true);
        m.invoke(component);

        assertFalse(mainPanel.isVisible());
    }

    @Test
    void updateAssistState_WhenLicenseButNotAuthenticated_ShowsLoginMessage() throws Exception {
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isAuthenticated()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        stubScannersAllFalse();

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_LOGIN_MESSAGE)).thenReturn("Please login");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("updateAssistState");
            m.setAccessible(true);
            m.invoke(component);
        }

        JPanel mainPanel = getField(component, "mainPanel");
        assertTrue(mainPanel.isVisible());
        JBLabel assistMsg = getField(component, "assistMessageLabel");
        assertEquals("Please login", assistMsg.getText());
    }

    @Test
    void updateAssistState_WhenAuthenticatedAndMcpEnabled_EnablesCheckboxes() throws Exception {
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
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

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("updateAssistState");
        m.setAccessible(true);
        m.invoke(component);

        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        assertTrue(ascaCheckbox.isEnabled());
        assertTrue(ascaCheckbox.isSelected());
    }

    @Test
    void updateAssistState_WhenAuthenticatedAndMcpDisabled_DisablesCheckboxes() throws Exception {
        when(mockState.isOneAssistLicenseEnabled()).thenReturn(true);
        when(mockState.isDevAssistLicenseEnabled()).thenReturn(false);
        when(mockState.isAuthenticated()).thenReturn(true);
        when(mockState.isMcpStatusChecked()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(false);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        stubScannersAllFalse();
        when(mockState.getContainersTool()).thenReturn("docker");

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP disabled");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("updateAssistState");
            m.setAccessible(true);
            m.invoke(component);
        }

        JBCheckBox ascaCheckbox = getField(component, "ascaCheckbox");
        assertFalse(ascaCheckbox.isEnabled());
    }

    // ===== dispose() =====

    @Test
    void dispose_WhenConnectionNotNull_DisposesConnection() throws Exception {
        MessageBusConnection mockConnection = mock(MessageBusConnection.class);
        setField(component, "connection", mockConnection);

        assertDoesNotThrow(() -> component.dispose());
        verify(mockConnection).dispose();
    }

    @Test
    void dispose_WhenConnectionThrowsException_DoesNotPropagate() throws Exception {
        MessageBusConnection mockConnection = mock(MessageBusConnection.class);
        doThrow(new RuntimeException("dispose error")).when(mockConnection).dispose();
        setField(component, "connection", mockConnection);

        assertDoesNotThrow(() -> component.dispose());
    }

    // ===== installMcp() =====

    @Test
    void installMcp_WhenInstallInProgress_ReturnsImmediately() throws Exception {
        setField(component, "mcpInstallInProgress", true);

        Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("installMcp");
        m.setAccessible(true);
        m.invoke(component);

        verify(mockState, never()).isMcpEnabled();
    }

    @Test
    void installMcp_WhenMcpNotEnabled_ShowsErrorStatus() throws Exception {
        setField(component, "mcpInstallInProgress", false);
        JBLabel mcpStatusLabel = new JBLabel();
        setField(component, "mcpStatusLabel", mcpStatusLabel);
        when(mockState.isMcpEnabled()).thenReturn(false);

        try (MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            bundleMock.when(() -> Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE)).thenReturn("MCP disabled");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("installMcp");
            m.setAccessible(true);
            m.invoke(component);
        }

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

        try (MockedStatic<GlobalSettingsSensitiveState> sensitiveMock = mockStatic(GlobalSettingsSensitiveState.class)) {
            sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitive);

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("installMcp");
            m.setAccessible(true);
            m.invoke(component);
        }

        assertFalse((boolean) getField(component, "mcpInstallInProgress"));
    }

    // ===== validateIACEngine() =====

    @Test
    void validateIACEngine_WhenEngineExists_ClearsLastNotificationEngine() throws Exception {
        setField(component, "lastNotificationEngine", "docker");
        when(mockState.getContainersTool()).thenReturn("docker");

        CxWrapper mockWrapper = mock(CxWrapper.class);

        try (MockedStatic<CxWrapperFactory> wfMock = mockStatic(CxWrapperFactory.class)) {
            wfMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("validateIACEngine");
            m.setAccessible(true);
            m.invoke(component);
        }

        assertEquals("", getField(component, "lastNotificationEngine"));
    }

    @Test
    void validateIACEngine_WhenEngineNotFoundAndSameAsPrevious_ReturnsEarlyWithoutNotification() throws Exception {
        setField(component, "lastNotificationEngine", "docker");
        when(mockState.getContainersTool()).thenReturn("docker");

        CxWrapper mockWrapper = mock(CxWrapper.class);
        doThrow(new RuntimeException("docker not found")).when(mockWrapper).checkEngineExist("docker");

        try (MockedStatic<CxWrapperFactory> wfMock = mockStatic(CxWrapperFactory.class)) {
            wfMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("validateIACEngine");
            m.setAccessible(true);
            m.invoke(component);
        }

        // Same engine — returns early, lastNotificationEngine stays "docker"
        assertEquals("docker", getField(component, "lastNotificationEngine"));
    }

    @Test
    void validateIACEngine_WhenEngineNotFoundAndDifferentFromPrevious_SetsLastNotificationEngine() throws Exception {
        setField(component, "lastNotificationEngine", "");
        when(mockState.getContainersTool()).thenReturn("podman");

        CxWrapper mockWrapper = mock(CxWrapper.class);
        doThrow(new RuntimeException("podman not found")).when(mockWrapper).checkEngineExist("podman");

        Application mockApp = mock(Application.class);

        try (MockedStatic<CxWrapperFactory> wfMock = mockStatic(CxWrapperFactory.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class);
             MockedStatic<Bundle> bundleMock = mockStatic(Bundle.class)) {
            wfMock.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            bundleMock.when(() -> Bundle.message(any(Resource.class))).thenReturn("doc-link");

            Method m = RealtimeScannersSettingsComponent.class.getDeclaredMethod("validateIACEngine");
            m.setAccessible(true);
            m.invoke(component);
        }

        assertEquals("podman", getField(component, "lastNotificationEngine"));
    }
}
