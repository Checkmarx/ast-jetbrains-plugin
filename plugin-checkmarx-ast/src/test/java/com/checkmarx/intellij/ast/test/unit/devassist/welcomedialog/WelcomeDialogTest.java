package com.checkmarx.intellij.ast.test.unit.devassist.welcomedialog;

import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.devassist.ui.WelcomeDialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import com.intellij.ui.components.JBCheckBox;

import static org.junit.jupiter.api.Assertions.*;

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
}
