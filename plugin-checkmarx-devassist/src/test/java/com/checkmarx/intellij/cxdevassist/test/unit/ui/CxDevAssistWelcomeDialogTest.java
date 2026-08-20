package com.checkmarx.intellij.cxdevassist.test.unit.ui;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.cxdevassist.ui.CxDevAssistWelcomeDialog;
import com.checkmarx.intellij.cxdevassist.ui.CxDevAssistWelcomeDialog.RealTimeSettingsManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CxDevAssistWelcomeDialogTest {

    static class FakeRealTimeSettingsManager implements RealTimeSettingsManager {
        boolean all;
        boolean any;

        public FakeRealTimeSettingsManager() {
            this.all = false;
            this.any = false;
        }

        public FakeRealTimeSettingsManager(boolean all, boolean any) {
            this.all = all;
            this.any = any;
        }

        @Override
        public boolean areAllEnabled() {
            return all;
        }

        @Override
        public boolean areAnyEnabled() {
            return any;
        }

        @Override
        public void setAll(boolean enable) {
            this.all = enable;
            this.any = enable;
        }
    }

    private CxDevAssistWelcomeDialog createDialogBypassCtor(boolean mcpEnabled, RealTimeSettingsManager mgr) throws Exception {
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        CxDevAssistWelcomeDialog dialog = (CxDevAssistWelcomeDialog) unsafe.allocateInstance(CxDevAssistWelcomeDialog.class);

        // Set required fields via reflection
        setField(dialog, "mcpEnabled", mcpEnabled);
        setField(dialog, "settingsManager", mgr);

        return dialog;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) {
                return findField(clazz.getSuperclass(), fieldName);
            }
            throw e;
        }
    }

    private Object invokeMethod(Object target, String methodName, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    @Test
    @DisplayName("Dialog can be created with MCP enabled")
    void testDialogCreation_McpEnabled() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager();
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);
        assertNotNull(dialog, "Dialog should be created");
    }

    @Test
    @DisplayName("Dialog can be created with MCP disabled")
    void testDialogCreation_McpDisabled() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager();
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, mgr);
        assertNotNull(dialog, "Dialog should be created");
    }

    @Test
    @DisplayName("Dialog works with null settings manager")
    void testDialogCreation_NullSettingsManager() throws Exception {
        assertDoesNotThrow(() -> createDialogBypassCtor(false, null),
                          "Dialog should handle null settings manager");
    }

    @Test
    @DisplayName("Settings manager can be accessed via reflection")
    void testSettingsManagerAccess() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(true, true);
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        Field field = findField(dialog.getClass(), "settingsManager");
        field.setAccessible(true);
        RealTimeSettingsManager retrieved = (RealTimeSettingsManager) field.get(dialog);

        assertEquals(mgr, retrieved, "Settings manager should be accessible");
    }

    @Test
    @DisplayName("MCP enabled flag can be set and retrieved")
    void testMcpEnabledFlag() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager();
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        Field field = findField(dialog.getClass(), "mcpEnabled");
        field.setAccessible(true);
        boolean mcpEnabled = (boolean) field.get(dialog);

        assertTrue(mcpEnabled, "MCP enabled flag should be true");
    }

    @Test
    @DisplayName("Multiple dialog instances are independent")
    void testMultipleDialogInstances() throws Exception {
        FakeRealTimeSettingsManager mgr1 = new FakeRealTimeSettingsManager(true, true);
        FakeRealTimeSettingsManager mgr2 = new FakeRealTimeSettingsManager(false, false);

        CxDevAssistWelcomeDialog dialog1 = createDialogBypassCtor(true, mgr1);
        CxDevAssistWelcomeDialog dialog2 = createDialogBypassCtor(false, mgr2);

        assertNotSame(dialog1, dialog2, "Dialog instances should be different");
    }

    @Test
    @DisplayName("FakeRealTimeSettingsManager setAll updates all and any flags")
    void testFakeSettingsManager_SetAll() {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(false, false);

        mgr.setAll(true);

        assertTrue(mgr.areAllEnabled(), "All should be enabled");
        assertTrue(mgr.areAnyEnabled(), "Any should be enabled");
    }

    @Test
    @DisplayName("FakeRealTimeSettingsManager initial state is correct")
    void testFakeSettingsManager_InitialState() {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(true, false);

        assertTrue(mgr.areAllEnabled(), "All should be enabled");
        assertFalse(mgr.areAnyEnabled(), "Any should be disabled");
    }

    @Test
    @DisplayName("Dialog instance is of correct type")
    void testDialogInstanceType() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager();
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        assertInstanceOf(CxDevAssistWelcomeDialog.class, dialog,
                        "Instance should be of type CxDevAssistWelcomeDialog");
    }

    @Test
    @DisplayName("Reflection-based field setting works correctly")
    void testReflectionFieldSetting() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager();
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        setField(dialog, "mcpEnabled", false);

        Field field = findField(dialog.getClass(), "mcpEnabled");
        field.setAccessible(true);
        boolean value = (boolean) field.get(dialog);

        assertFalse(value, "Field value should be updated");
    }

    @Test
    @DisplayName("Dialog can be created multiple times with different configurations")
    void testMultipleCreationsWithDifferentConfigs() throws Exception {
        // Test various combinations
        assertDoesNotThrow(() -> {
            createDialogBypassCtor(true, new FakeRealTimeSettingsManager(true, true));
            createDialogBypassCtor(false, new FakeRealTimeSettingsManager(false, false));
            createDialogBypassCtor(true, new FakeRealTimeSettingsManager(false, true));
            createDialogBypassCtor(false, new FakeRealTimeSettingsManager(true, false));
        }, "Should handle multiple different configurations");
    }

    @Test
    @DisplayName("Settings manager state transitions work correctly")
    void testSettingsManagerStateTransitions() {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(false, false);

        // Enable all
        mgr.setAll(true);
        assertTrue(mgr.areAllEnabled());
        assertTrue(mgr.areAnyEnabled());

        // Disable all
        mgr.setAll(false);
        assertFalse(mgr.areAllEnabled());
        assertFalse(mgr.areAnyEnabled());
    }

    @Test
    @DisplayName("Dialog creation performance is acceptable")
    void testDialogCreationPerformance() {
        long startTime = System.currentTimeMillis();

        assertDoesNotThrow(() -> {
            for (int i = 0; i < 10; i++) {
                createDialogBypassCtor(i % 2 == 0,
                                      new FakeRealTimeSettingsManager(i % 2 == 0, i % 3 == 0));
            }
        }, "Should create multiple dialogs quickly");

        long endTime = System.currentTimeMillis();
        assertTrue(endTime - startTime < 5000,
                  "Creating 10 dialogs should take less than 5 seconds");
    }

    // ===== updateCheckboxTooltip() =====

    @Test
    void updateCheckboxTooltip_WhenCheckboxNull_ReturnsEarly() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager());
        setField(dialog, "realTimeScannersCheckbox", null);
        assertDoesNotThrow(() -> invokeMethod(dialog, "updateCheckboxTooltip", new Class[]{}));
    }

    @Test
    void updateCheckboxTooltip_WhenMcpDisabled_SetsNotEnabledTooltip() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        JBCheckBox checkbox = new JBCheckBox();
        setField(dialog, "realTimeScannersCheckbox", checkbox);

        invokeMethod(dialog, "updateCheckboxTooltip", new Class[]{});

        assertEquals("Checkmarx MCP is not enabled for this tenant.", checkbox.getToolTipText());
    }

    @Test
    void updateCheckboxTooltip_WhenMcpEnabledAndAllEnabled_SetsDisableAllTooltip() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager(true, true));
        JBCheckBox checkbox = new JBCheckBox();
        setField(dialog, "realTimeScannersCheckbox", checkbox);

        invokeMethod(dialog, "updateCheckboxTooltip", new Class[]{});

        assertEquals("Disable all real-time scanners", checkbox.getToolTipText());
    }

    @Test
    void updateCheckboxTooltip_WhenMcpEnabledAndSomeEnabled_SetsSomeEnabledTooltip() throws Exception {
        // areAllEnabled=false, areAnyEnabled=true → "Some scanners are enabled"
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager(false, true));
        JBCheckBox checkbox = new JBCheckBox();
        setField(dialog, "realTimeScannersCheckbox", checkbox);

        invokeMethod(dialog, "updateCheckboxTooltip", new Class[]{});

        assertTrue(checkbox.getToolTipText().contains("Some scanners are enabled"));
    }

    @Test
    void updateCheckboxTooltip_WhenMcpEnabledAndNoneEnabled_SetsEnableAllTooltip() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager(false, false));
        JBCheckBox checkbox = new JBCheckBox();
        setField(dialog, "realTimeScannersCheckbox", checkbox);

        invokeMethod(dialog, "updateCheckboxTooltip", new Class[]{});

        assertEquals("Enable all real-time scanners", checkbox.getToolTipText());
    }

    // ===== refreshCheckboxState() =====

    @Test
    void refreshCheckboxState_WhenCheckboxNull_ReturnsEarly() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager());
        setField(dialog, "realTimeScannersCheckbox", null);
        assertDoesNotThrow(() -> invokeMethod(dialog, "refreshCheckboxState", new Class[]{}));
    }

    @Test
    void refreshCheckboxState_WhenAnyEnabled_SelectsCheckbox() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager(false, true));
        JBCheckBox checkbox = new JBCheckBox();
        setField(dialog, "realTimeScannersCheckbox", checkbox);

        invokeMethod(dialog, "refreshCheckboxState", new Class[]{});

        assertTrue(checkbox.isSelected());
    }

    @Test
    void refreshCheckboxState_WhenNoneEnabled_UnselectsCheckbox() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager(false, false));
        JBCheckBox checkbox = new JBCheckBox();
        checkbox.setSelected(true);
        setField(dialog, "realTimeScannersCheckbox", checkbox);

        invokeMethod(dialog, "refreshCheckboxState", new Class[]{});

        assertFalse(checkbox.isSelected());
    }

    // ===== configureCheckboxBehavior() =====

    @Test
    void configureCheckboxBehavior_WhenCheckboxNull_ReturnsEarly() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager());
        setField(dialog, "realTimeScannersCheckbox", null);
        assertDoesNotThrow(() -> invokeMethod(dialog, "configureCheckboxBehavior", new Class[]{}));
    }

    @Test
    void configureCheckboxBehavior_WhenActionFires_TogglesAllViaSettingsManager() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(false, true);
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);
        JBCheckBox checkbox = new JBCheckBox();
        setField(dialog, "realTimeScannersCheckbox", checkbox);

        invokeMethod(dialog, "configureCheckboxBehavior", new Class[]{});
        // Fire the action: anyEnabled=true → setAll(!true)=setAll(false)
        checkbox.doClick();

        assertFalse(mgr.areAllEnabled());
        assertFalse(mgr.areAnyEnabled());
    }

    // ===== DefaultRealTimeSettingsManager =====

    private static Object newDefaultManager() throws Exception {
        for (Class<?> c : CxDevAssistWelcomeDialog.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("DefaultRealTimeSettingsManager")) {
                java.lang.reflect.Constructor<?> ctor = c.getDeclaredConstructor();
                ctor.setAccessible(true);
                return ctor.newInstance();
            }
        }
        throw new IllegalStateException("DefaultRealTimeSettingsManager not found");
    }

    private static Object invokeInner(Object obj, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        java.lang.reflect.Method m = obj.getClass().getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(obj, args);
    }

    @Test
    void defaultSettingsManager_AreAllEnabled_WhenAllFlagsTrue_ReturnsTrue() throws Exception {
        Object mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(true);
        when(mockState.isSecretDetectionRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(true);
        when(mockState.isIacRealtime()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            boolean result = (boolean) invokeInner(mgr, "areAllEnabled", new Class<?>[0]);
            assertTrue(result);
        }
    }

    @Test
    void defaultSettingsManager_AreAllEnabled_WhenOneFlagFalse_ReturnsFalse() throws Exception {
        Object mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isOssRealtime()).thenReturn(true);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false); // one false
        when(mockState.isContainersRealtime()).thenReturn(true);
        when(mockState.isIacRealtime()).thenReturn(true);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            boolean result = (boolean) invokeInner(mgr, "areAllEnabled", new Class<?>[0]);
            assertFalse(result);
        }
    }

    @Test
    void defaultSettingsManager_AreAnyEnabled_WhenAllFlagsFalse_ReturnsFalse() throws Exception {
        Object mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            boolean result = (boolean) invokeInner(mgr, "areAnyEnabled", new Class<?>[0]);
            assertFalse(result);
        }
    }

    @Test
    void defaultSettingsManager_AreAnyEnabled_WhenOneTrue_ReturnsTrue() throws Exception {
        Object mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.isAscaRealtime()).thenReturn(false);
        when(mockState.isOssRealtime()).thenReturn(false);
        when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        when(mockState.isContainersRealtime()).thenReturn(false);
        when(mockState.isIacRealtime()).thenReturn(true); // one true

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            boolean result = (boolean) invokeInner(mgr, "areAnyEnabled", new Class<?>[0]);
            assertTrue(result);
        }
    }

    @Test
    void defaultSettingsManager_SetAll_EnablesAllStateFlags() throws Exception {
        Object mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            invokeInner(mgr, "setAll", new Class<?>[]{boolean.class}, true);
        }

        verify(mockState).setAscaRealtime(true);
        verify(mockState).setOssRealtime(true);
        verify(mockState).setSecretDetectionRealtime(true);
        verify(mockState).setContainersRealtime(true);
        verify(mockState).setIacRealtime(true);
        verify(mockState).setUserPreferences(true, true, true, true, true);
    }

    // ===== UI building methods =====

    @Test
    @DisplayName("createBullet returns non-null panel with glyph and text")
    void createBullet_ReturnsPanel_WithComponents() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createBullet",
                new Class[]{com.checkmarx.intellij.common.resources.Resource.class},
                com.checkmarx.intellij.common.resources.Resource.WELCOME_MAIN_FEATURE_1);
        assertNotNull(result);
        assertInstanceOf(javax.swing.JComponent.class, result);
        javax.swing.JPanel panel = (javax.swing.JPanel) result;
        assertTrue(panel.getComponentCount() >= 2);
    }

    @Test
    @DisplayName("createBullet works for all four main features")
    void createBullet_WorksForAllMainFeatures() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        com.checkmarx.intellij.common.resources.Resource[] resources = {
            com.checkmarx.intellij.common.resources.Resource.WELCOME_MAIN_FEATURE_1,
            com.checkmarx.intellij.common.resources.Resource.WELCOME_MAIN_FEATURE_2,
            com.checkmarx.intellij.common.resources.Resource.WELCOME_MAIN_FEATURE_3,
            com.checkmarx.intellij.common.resources.Resource.WELCOME_MAIN_FEATURE_4
        };
        for (com.checkmarx.intellij.common.resources.Resource res : resources) {
            assertDoesNotThrow(() -> invokeMethod(dialog, "createBullet",
                    new Class[]{com.checkmarx.intellij.common.resources.Resource.class}, res));
        }
    }

    @Test
    @DisplayName("createFeatureCardHeader creates panel with checkbox; MCP disabled → checkbox disabled")
    void createFeatureCardHeader_McpDisabled_CheckboxIsDisabled() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createFeatureCardHeader",
                new Class[]{java.awt.Color.class}, java.awt.Color.GRAY);
        assertNotNull(result);
        JBCheckBox checkbox = dialog.getRealTimeScannersCheckbox();
        assertNotNull(checkbox);
        assertFalse(checkbox.isEnabled(), "Checkbox must be disabled when MCP is disabled");
    }

    @Test
    @DisplayName("createFeatureCardHeader creates panel with checkbox; MCP enabled → checkbox enabled")
    void createFeatureCardHeader_McpEnabled_CheckboxIsEnabled() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createFeatureCardHeader",
                new Class[]{java.awt.Color.class}, java.awt.Color.GRAY);
        assertNotNull(result);
        JBCheckBox checkbox = dialog.getRealTimeScannersCheckbox();
        assertNotNull(checkbox);
        assertTrue(checkbox.isEnabled(), "Checkbox must be enabled when MCP is enabled");
    }

    @Test
    @DisplayName("createFeatureCardBullets includes MCP info bullet when MCP enabled")
    void createFeatureCardBullets_McpEnabled_HasExtraBullet() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createFeatureCardBullets", new Class[]{});
        assertNotNull(result);
        javax.swing.JPanel panel = (javax.swing.JPanel) result;
        assertTrue(panel.getComponentCount() >= 4);
    }

    @Test
    @DisplayName("createFeatureCardBullets includes MCP disabled icon when MCP disabled")
    void createFeatureCardBullets_McpDisabled_HasIconComponent() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createFeatureCardBullets", new Class[]{});
        assertNotNull(result);
        javax.swing.JPanel panel = (javax.swing.JPanel) result;
        assertTrue(panel.getComponentCount() >= 4);
        // Last component should be the MCP disabled icon label
        java.awt.Component last = panel.getComponent(panel.getComponentCount() - 1);
        assertInstanceOf(javax.swing.JLabel.class, last);
    }

    @Test
    @DisplayName("createFeatureCard builds header + bullets panel")
    void createFeatureCard_BuildsCardWithHeaderAndBullets() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createFeatureCard", new Class[]{});
        assertNotNull(result);
        javax.swing.JPanel panel = (javax.swing.JPanel) result;
        assertTrue(panel.getComponentCount() >= 2);
    }

    @Test
    @DisplayName("createRightImagePanel creates panel with image label")
    void createRightImagePanel_CreatesPanelWithImageLabel() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createRightImagePanel", new Class[]{});
        assertNotNull(result);
        javax.swing.JPanel panel = (javax.swing.JPanel) result;
        assertTrue(panel.getComponentCount() >= 1);
        java.awt.Component first = panel.getComponent(0);
        assertInstanceOf(javax.swing.JLabel.class, first);
    }

    @Test
    @DisplayName("createCenterPanel combines left and right panels (mcpDisabled)")
    void createCenterPanel_McpDisabled_CombinesLeftAndRight() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        Object result = invokeMethod(dialog, "createCenterPanel", new Class[]{});
        assertNotNull(result);
        javax.swing.JPanel panel = (javax.swing.JPanel) result;
        assertTrue(panel.getComponentCount() >= 2);
    }

    @Test
    @DisplayName("createCenterPanel with mcpEnabled exercises initializeRealtimeState branch")
    void createCenterPanel_McpEnabled_CallsInitializeRealtimeState() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(true, true);
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            GlobalSettingsState mockState = mock(GlobalSettingsState.class);
            when(mockState.getUserPreferencesSet()).thenReturn(false);
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);

            Object result = invokeMethod(dialog, "createCenterPanel", new Class[]{});
            assertNotNull(result);
        }
    }

    // ===== initializeRealtimeState =====

    @Test
    @DisplayName("initializeRealtimeState when mcpEnabled=false returns early")
    void initializeRealtimeState_McpDisabled_ReturnsEarly() throws Exception {
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(false, new FakeRealTimeSettingsManager());
        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            invokeMethod(dialog, "initializeRealtimeState", new Class[]{});
            stateMock.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("initializeRealtimeState when prefs set and changed publishes settings")
    void initializeRealtimeState_PrefsSet_Changed_PublishesSettings() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(true, true);
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getUserPreferencesSet()).thenReturn(true);
        when(mockState.applyUserPreferencesToRealtimeSettings()).thenReturn(true);

        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        com.checkmarx.intellij.common.settings.SettingsListener mockListener =
                mock(com.checkmarx.intellij.common.settings.SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            invokeMethod(dialog, "initializeRealtimeState", new Class[]{});
        }
        verify(mockListener).settingsApplied();
    }

    @Test
    @DisplayName("initializeRealtimeState when prefs not set and none enabled enables all")
    void initializeRealtimeState_PrefsNotSet_NoneEnabled_EnablesAll() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(false, false);
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            invokeMethod(dialog, "initializeRealtimeState", new Class[]{});
        }
        assertTrue(mgr.areAllEnabled());
    }

    @Test
    @DisplayName("initializeRealtimeState when prefs not set but some enabled skips setAll")
    void initializeRealtimeState_PrefsNotSet_SomeEnabled_DoesNotSetAll() throws Exception {
        FakeRealTimeSettingsManager mgr = new FakeRealTimeSettingsManager(false, true);
        CxDevAssistWelcomeDialog dialog = createDialogBypassCtor(true, mgr);

        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        when(mockState.getUserPreferencesSet()).thenReturn(false);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            invokeMethod(dialog, "initializeRealtimeState", new Class[]{});
        }
        // anyEnabled=true → condition `!anyEnabled && !prefs` is false → setAll NOT called
        assertFalse(mgr.areAllEnabled(), "setAll should not be called when some scanners already enabled");
    }

    @Test
    @DisplayName("defaultSettingsManager_SetAll_DisablesAllStateFlags")
    void defaultSettingsManager_SetAll_DisablesAllStateFlags() throws Exception {
        Object mgr = newDefaultManager();
        GlobalSettingsState mockState = mock(GlobalSettingsState.class);
        Application mockApp = mock(Application.class);
        MessageBus mockBus = mock(MessageBus.class);
        SettingsListener mockListener = mock(SettingsListener.class);
        when(mockApp.getMessageBus()).thenReturn(mockBus);
        when(mockBus.syncPublisher(any())).thenReturn(mockListener);

        try (MockedStatic<GlobalSettingsState> stateMock = mockStatic(GlobalSettingsState.class);
             MockedStatic<ApplicationManager> appMgrMock = mockStatic(ApplicationManager.class)) {
            stateMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            appMgrMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            invokeInner(mgr, "setAll", new Class<?>[]{boolean.class}, false);
        }

        verify(mockState).setAscaRealtime(false);
        verify(mockState).setOssRealtime(false);
        verify(mockState).setSecretDetectionRealtime(false);
        verify(mockState).setContainersRealtime(false);
        verify(mockState).setIacRealtime(false);
        verify(mockState).setUserPreferences(false, false, false, false, false);
    }
}

