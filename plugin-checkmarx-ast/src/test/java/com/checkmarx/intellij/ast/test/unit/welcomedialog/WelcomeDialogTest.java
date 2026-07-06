package com.checkmarx.intellij.ast.test.unit.welcomedialog;

import com.checkmarx.intellij.ast.ui.WelcomeDialog;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WelcomeDialogTest {

    static class FakeSettings implements WelcomeDialog.RealTimeSettingsManager {
        boolean all;
        boolean any;

        public FakeSettings() {
            this.all = false;
            this.any = false;
        }

        public FakeSettings(boolean all, boolean any) {
            this.all = all;
            this.any = any;
        }

        @Override public boolean areAllEnabled() { return all; }
        @Override public boolean areAnyEnabled() { return any; }
        @Override public void setAll(boolean enable) {
            this.all = enable;
            this.any = enable; // If we enable/disable all, any should match
        }
    }

    private WelcomeDialog newDialogBypassCtor(boolean mcpEnabled, WelcomeDialog.RealTimeSettingsManager mgr) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        WelcomeDialog dlg = (WelcomeDialog) unsafe.allocateInstance(WelcomeDialog.class);
        // Set required fields via reflection
        setField(dlg, "mcpEnabled", mcpEnabled);
        setField(dlg, "settingsManager", mgr);
        // Prepare checkbox field as done by createFeatureCardHeader
        JBCheckBox check = new JBCheckBox();
        check.setEnabled(mcpEnabled);
        setField(dlg, "realTimeScannersCheckbox", check);
        return dlg;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Object invokeProtected(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    @Test
    @DisplayName("createBullet wraps text and returns panel with glyph and label")
    void testCreateBullet_WrapsAndReturnsPanel() throws Exception {
        WelcomeDialog dlg = newDialogBypassCtor(false, new FakeSettings());
        JComponent bullet = dlg.createBullet(Resource.WELCOME_MAIN_FEATURE_1);
        assertNotNull(bullet);
        JPanel bulletPanel = assertInstanceOf(JPanel.class, bullet);
        assertEquals(2, bulletPanel.getComponentCount());
    }

    @Test
    @DisplayName("Checkbox disabled when MCP is not enabled and tooltip indicates MCP not enabled")
    void testCheckbox_McpDisabled_TooltipMessage() throws Exception {
        FakeSettings settings = new FakeSettings();
        WelcomeDialog dlg = newDialogBypassCtor(false, settings);
        invokeProtected(dlg, "refreshCheckboxState", new Class<?>[]{});
        JCheckBox box = dlg.getRealTimeScannersCheckbox();
        assertNotNull(box);
        assertFalse(box.isEnabled());
        assertFalse(box.isSelected());
        assertEquals("Checkmarx MCP is not enabled for this tenant.", box.getToolTipText());
    }

    @Test
    @DisplayName("Checkbox action toggles settings and updates selection state when enabled")
    void testCheckbox_Action_TogglesSettingsAndSelection() throws Exception {
        FakeSettings settings = new FakeSettings();
        WelcomeDialog dlg = newDialogBypassCtor(true, settings);
        invokeProtected(dlg, "configureCheckboxBehavior", new Class<?>[]{});
        JCheckBox box = dlg.getRealTimeScannersCheckbox();
        assertNotNull(box);
        assertTrue(box.isEnabled());
        assertFalse(box.isSelected());
        box.doClick();
        assertTrue(settings.areAllEnabled());
        invokeProtected(dlg, "refreshCheckboxState", new Class<?>[]{});
        assertEquals(settings.areAllEnabled(), box.isSelected());
        assertEquals("Disable all real-time scanners", box.getToolTipText());
    }

    @Test
    @DisplayName("Feature card header initializes checkbox enabled state based on MCP")
    void testCreateFeatureCardHeader_CheckboxEnabledByMcp() throws Exception {
        WelcomeDialog dlgEnabled = newDialogBypassCtor(true, new FakeSettings());
        JPanel headerEnabled = (JPanel) invokeProtected(dlgEnabled, "createFeatureCardHeader", new Class<?>[]{Color.class}, Color.GRAY);
        assertNotNull(headerEnabled);
        JCheckBox boxEnabled = dlgEnabled.getRealTimeScannersCheckbox();
        assertTrue(boxEnabled.isEnabled());

        WelcomeDialog dlgDisabled = newDialogBypassCtor(false, new FakeSettings());
        JPanel headerDisabled = (JPanel) invokeProtected(dlgDisabled, "createFeatureCardHeader", new Class<?>[]{Color.class}, Color.GRAY);
        assertNotNull(headerDisabled);
        JCheckBox boxDisabled = dlgDisabled.getRealTimeScannersCheckbox();
        assertFalse(boxDisabled.isEnabled());
    }

    @Test
    @DisplayName("Feature card bullets include MCP info when enabled, icon when disabled")
    void testCreateFeatureCardBullets_McpBranches() throws Exception {
        WelcomeDialog dlgEnabled = newDialogBypassCtor(true, new FakeSettings());
        JPanel bulletsEnabled = (JPanel) invokeProtected(dlgEnabled, "createFeatureCardBullets", new Class<?>[]{});
        assertNotNull(bulletsEnabled);
        assertTrue(bulletsEnabled.getComponentCount() >= 4); // includes MCP installed info bullet

        WelcomeDialog dlgDisabled = newDialogBypassCtor(false, new FakeSettings());
        JPanel bulletsDisabled = (JPanel) invokeProtected(dlgDisabled, "createFeatureCardBullets", new Class<?>[]{});
        assertNotNull(bulletsDisabled);
        assertTrue(bulletsDisabled.getComponentCount() >= 4); // last is icon label when MCP disabled
        Component last = bulletsDisabled.getComponent(bulletsDisabled.getComponentCount() - 1);
        assertInstanceOf(JLabel.class, last);
    }

    @Test
    @DisplayName("Right image panel creates fixed-size panel with image label")
    void testCreateRightImagePanel_PanelAndImage() throws Exception {
        WelcomeDialog dlg = newDialogBypassCtor(false, new FakeSettings());
        JPanel right = (JPanel) invokeProtected(dlg, "createRightImagePanel", new Class<?>[]{});
        assertNotNull(right);
        assertTrue(right.getComponentCount() >= 1);
        Component c = right.getComponent(0);
        assertInstanceOf(JLabel.class, c);
    }

    @Test
    @DisplayName("updateCheckboxTooltip shows enable/disable messages when MCP enabled")
    void testUpdateCheckboxTooltip_EnableDisableMessages() throws Exception {
        // Test case 1: No scanners enabled
        FakeSettings settingsNone = new FakeSettings(false, false);
        WelcomeDialog dlgNone = newDialogBypassCtor(true, settingsNone);
        JCheckBox boxNone = dlgNone.getRealTimeScannersCheckbox();
        invokeProtected(dlgNone, "updateCheckboxTooltip", new Class<?>[]{});
        assertEquals("Enable all real-time scanners", boxNone.getToolTipText());

        // Test case 2: Some scanners enabled (any=true, all=false)
        FakeSettings settingsSome = new FakeSettings(false, true);
        WelcomeDialog dlgSome = newDialogBypassCtor(true, settingsSome);
        JCheckBox boxSome = dlgSome.getRealTimeScannersCheckbox();
        invokeProtected(dlgSome, "updateCheckboxTooltip", new Class<?>[]{});
        assertEquals("Some scanners are enabled. Click to enable all real-time scanners", boxSome.getToolTipText());

        // Test case 3: All scanners enabled
        FakeSettings settingsAll = new FakeSettings(true, true);
        WelcomeDialog dlgAll = newDialogBypassCtor(true, settingsAll);
        JCheckBox boxAll = dlgAll.getRealTimeScannersCheckbox();
        invokeProtected(dlgAll, "updateCheckboxTooltip", new Class<?>[]{});
        assertEquals("Disable all real-time scanners", boxAll.getToolTipText());
    }

    @Test
    @DisplayName("updateCheckboxTooltip shows MCP not enabled when MCP disabled")
    void testUpdateCheckboxTooltip_McpDisabledMessage() throws Exception {
        WelcomeDialog dlg = newDialogBypassCtor(false, new FakeSettings());
        JCheckBox box = dlg.getRealTimeScannersCheckbox();
        box.setSelected(true);
        invokeProtected(dlg, "updateCheckboxTooltip", new Class<?>[]{});
        assertEquals("Checkmarx MCP is not enabled for this tenant.", box.getToolTipText());
    }

    @Test
    @DisplayName("createFeatureCard builds header + bullets")
    void testCreateFeatureCard_Composition() throws Exception {
        WelcomeDialog dlg = newDialogBypassCtor(false, new FakeSettings());
        JPanel featureCard = (JPanel) invokeProtected(dlg, "createFeatureCard", new Class<?>[]{});
        assertNotNull(featureCard);
        assertTrue(featureCard.getComponentCount() >= 2); // header + bullets
    }

    static class TestSubclass extends WelcomeDialog {
        TestSubclass(boolean mcp, RealTimeSettingsManager mgr) throws Exception { super(null, mcp, mgr); }
        public JComponent exposedCenter() { return createCenterPanel(); }
    }

    @Test
    @DisplayName("createCenterPanel returns panel with left and right child when MCP disabled")
    void testCreateCenterPanel_McpDisabled() throws Exception {
        WelcomeDialog dlg = newDialogBypassCtor(false, new FakeSettings());
        JPanel center = (JPanel) invokeProtected(dlg, "createCenterPanel", new Class<?>[]{});
        assertNotNull(center);
        assertTrue(center.getComponentCount() >= 2);
    }

    // ===== DefaultRealTimeSettingsManager =====

    private WelcomeDialog.RealTimeSettingsManager newDefaultManager() throws Exception {
        Class<?> managerClass = Class.forName("com.checkmarx.intellij.ast.ui.WelcomeDialog$DefaultRealTimeSettingsManager");
        Constructor<?> ctor = managerClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        return (WelcomeDialog.RealTimeSettingsManager) ctor.newInstance();
    }

    @Test
    @DisplayName("DefaultManager.areAllEnabled returns false when all scanners disabled")
    void defaultManager_AreAllEnabled_WhenAllFalse_ReturnsFalse() throws Exception {
        WelcomeDialog.RealTimeSettingsManager mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertFalse(mgr.areAllEnabled());
        }
    }

    @Test
    @DisplayName("DefaultManager.areAllEnabled returns true when all scanners enabled")
    void defaultManager_AreAllEnabled_WhenAllTrue_ReturnsTrue() throws Exception {
        WelcomeDialog.RealTimeSettingsManager mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(true);
        when(mockState.isSecretDetectionRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(true);
        when(mockState.isIacRealtime()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertTrue(mgr.areAllEnabled());
        }
    }

    @Test
    @DisplayName("DefaultManager.areAnyEnabled returns false when no scanner enabled")
    void defaultManager_AreAnyEnabled_WhenNone_ReturnsFalse() throws Exception {
        WelcomeDialog.RealTimeSettingsManager mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertFalse(mgr.areAnyEnabled());
        }
    }

    @Test
    @DisplayName("DefaultManager.areAnyEnabled returns true when one scanner enabled")
    void defaultManager_AreAnyEnabled_WhenOneEnabled_ReturnsTrue() throws Exception {
        WelcomeDialog.RealTimeSettingsManager mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            assertTrue(mgr.areAnyEnabled());
        }
    }

    @Test
    @DisplayName("DefaultManager.setAll(true) enables all scanners and publishes settings")
    void defaultManager_SetAll_True_EnablesAllAndPublishes() throws Exception {
        WelcomeDialog.RealTimeSettingsManager mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mgr.setAll(true);
        }

        verify(mockState).setAscaRealtime(true);
        verify(mockState).setOssRealtime(true);
        verify(mockState).setSecretDetectionRealtime(true);
        verify(mockState).setContainersRealtime(true);
        verify(mockState).setIacRealtime(true);
        verify(mockState).setUserPreferences(true, true, true, true, true);
        verify(mockListener).settingsApplied();
    }

    // ===== initializeRealtimeState =====

    @Test
    @DisplayName("initializeRealtimeState when prefs not set and none enabled enables all scanners")
    void initializeRealtimeState_WhenPrefsNotSet_AndNoScannersEnabled_EnablesAll() throws Exception {
        FakeSettings settings = new FakeSettings(false, false);
        WelcomeDialog dlg = newDialogBypassCtor(true, settings);

        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            invokeProtected(dlg, "initializeRealtimeState", new Class<?>[]{});
        }

        assertTrue(settings.areAllEnabled());
    }

    @Test
    @DisplayName("initializeRealtimeState when prefs set and settings changed publishes settingsApplied")
    void initializeRealtimeState_WhenPrefsSet_AndSettingsChanged_PublishesSettingsApplied() throws Exception {
        FakeSettings settings = new FakeSettings(true, true);
        WelcomeDialog dlg = newDialogBypassCtor(true, settings);

        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.applyUserPreferencesToRealtimeSettings()).thenReturn(true);

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            invokeProtected(dlg, "initializeRealtimeState", new Class<?>[]{});
        }

        verify(mockListener).settingsApplied();
    }

    @Test
    @DisplayName("initializeRealtimeState when prefs set and no change does not publish")
    void initializeRealtimeState_WhenPrefsSet_AndNoChange_DoesNotPublish() throws Exception {
        FakeSettings settings = new FakeSettings(true, true);
        WelcomeDialog dlg = newDialogBypassCtor(true, settings);

        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.applyUserPreferencesToRealtimeSettings()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            invokeProtected(dlg, "initializeRealtimeState", new Class<?>[]{});
        }

        assertTrue(true); // ApplicationManager not called — no exception means pass
    }

    @Test
    @DisplayName("initializeRealtimeState when mcpEnabled is false returns early without calling state")
    void initializeRealtimeState_WhenMcpDisabled_ReturnsEarly() throws Exception {
        FakeSettings settings = new FakeSettings(false, false);
        WelcomeDialog dlg = newDialogBypassCtor(false, settings);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            invokeProtected(dlg, "initializeRealtimeState", new Class<?>[]{});
            stateMock.verifyNoInteractions();
        }

        assertFalse(settings.areAllEnabled());
    }

    // ===== createCenterPanel with mcpEnabled=true =====

    @Test
    @DisplayName("createCenterPanel with mcpEnabled=true exercises initializeRealtimeState branch")
    void testCreateCenterPanel_McpEnabled_ExercisesInitializePath() throws Exception {
        FakeSettings settings = new FakeSettings(true, true);
        WelcomeDialog dlg = newDialogBypassCtor(true, settings);

        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            JPanel center = (JPanel) invokeProtected(dlg, "createCenterPanel", new Class<?>[]{});
            assertNotNull(center);
            assertTrue(center.getComponentCount() >= 2);
        }
    }

    @Test
    @DisplayName("createLeftPanel when mcpEnabled=false skips initializeRealtimeState")
    void testCreateLeftPanel_McpDisabled_SkipsInitializeState() throws Exception {
        FakeSettings settings = new FakeSettings(false, false);
        WelcomeDialog dlg = newDialogBypassCtor(false, settings);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            JComponent panel = (JComponent) invokeProtected(dlg, "createLeftPanel", new Class<?>[]{});
            assertNotNull(panel);
            stateMock.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("DefaultManager.setAll(false) disables all scanners")
    void defaultManager_SetAll_False_DisablesAll() throws Exception {
        WelcomeDialog.RealTimeSettingsManager mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            mgr.setAll(false);
        }

        verify(mockState).setAscaRealtime(false);
        verify(mockState).setOssRealtime(false);
        verify(mockState).setUserPreferences(false, false, false, false, false);
    }
}
