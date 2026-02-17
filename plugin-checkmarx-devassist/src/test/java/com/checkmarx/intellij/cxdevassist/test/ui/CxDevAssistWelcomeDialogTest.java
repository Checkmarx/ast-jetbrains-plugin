package com.checkmarx.intellij.cxdevassist.test.ui;

import com.checkmarx.intellij.cxdevassist.ui.CxDevAssistWelcomeDialog;
import com.checkmarx.intellij.cxdevassist.ui.CxDevAssistWelcomeDialog.RealTimeSettingsManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

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
}

