package com.checkmarx.intellij.unit.welcomedialog;

import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.ui.WelcomeDialog;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import java.awt.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WelcomeDialog} logic.
 * Ensures real-time toggle orchestration and layout composition work as intended.
 */
public class WelcomeDialogTest {

    private static final int WRAP_WIDTH = 250;

    /**
     * Fake manager for tracking calls for verification.
     */
    static class FakeManager implements WelcomeDialog.RealTimeSettingsManager {
        final AtomicBoolean enabled = new AtomicBoolean(false);
        int setAllCalls = 0;

        @Override
        public boolean areAllEnabled() {
            return enabled.get();
        }

        @Override
        public void setAll(boolean enable) {
            enabled.set(enable);
            setAllCalls++;
        }
    }

    @Test
    @DisplayName("MCP enabled: initialization behavior with different initial states")
    void testMcpEnabledInitialization() throws Exception {
        // Test 1: Force enable when starting disabled
        FakeManager mgr1 = new FakeManager();
        assertFalse(mgr1.areAllEnabled(), "Precondition: settings should be disabled");

        WelcomeDialog dialog1 = runOnEdt(() -> new WelcomeDialog(null, true, mgr1));

        assertTrue(mgr1.areAllEnabled(), "Settings should be enabled after dialog initialization");
        assertEquals(1, mgr1.setAllCalls, "setAll should be called once during initialization");
        assertNotNull(dialog1.getRealTimeScannersCheckbox(), "Checkbox should be present when MCP is enabled");
        assertTrue(dialog1.getRealTimeScannersCheckbox().isSelected(), "Checkbox should be selected");

        // Test 2: No duplicate call when already enabled
        FakeManager mgr2 = new FakeManager();
        mgr2.setAll(true); // Start with scanners already enabled
        assertEquals(1, mgr2.setAllCalls, "Precondition: one call to set enabled state");

        WelcomeDialog dialog2 = runOnEdt(() -> new WelcomeDialog(null, true, mgr2));

        assertNotNull(dialog2.getRealTimeScannersCheckbox(), "Checkbox should be present");
        assertTrue(dialog2.getRealTimeScannersCheckbox().isSelected(), "Checkbox should be selected");
        assertEquals(1, mgr2.setAllCalls, "setAll should NOT be called again if scanners are already enabled");
    }

    @Test
    @DisplayName("MCP disabled: checkbox should be disabled and initialization should not force enable")
    void testMcpDisabledDisablesCheckbox() throws Exception {
        FakeManager mgr = new FakeManager();
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, false, mgr));

        JCheckBox checkbox = dialog.getRealTimeScannersCheckbox();
        assertNotNull(checkbox, "Checkbox should be present when MCP is disabled");
        assertFalse(checkbox.isEnabled(), "Checkbox should be disabled when MCP is disabled");
        assertFalse(mgr.areAllEnabled(), "Settings should remain disabled");
        assertEquals(0, mgr.setAllCalls, "setAll should not be called");
    }

    @Test
    @DisplayName("Clicking checkbox should flip real-time state")
    void testCheckboxClickFlipsState() throws Exception {
        FakeManager mgr = new FakeManager();
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, true, mgr));
        JBCheckBox checkbox = dialog.getRealTimeScannersCheckbox();

        assertNotNull(checkbox, "Checkbox must exist for this test");
        assertTrue(mgr.areAllEnabled(), "Initial state should be enabled");
        assertEquals(1, mgr.setAllCalls);

        // Simulate user unchecking the box
        runOnEdt(() -> {
            checkbox.setSelected(false);
            checkbox.getActionListeners()[0].actionPerformed(null); // Manually trigger listener
            return null;
        });
        assertFalse(mgr.areAllEnabled(), "State should be disabled after unchecking");
        assertEquals(2, mgr.setAllCalls, "setAll should be called again");

        // Simulate user re-checking the box
        runOnEdt(() -> {
            checkbox.setSelected(true);
            checkbox.getActionListeners()[0].actionPerformed(null);
            return null;
        });
        assertTrue(mgr.areAllEnabled(), "State should be re-enabled after checking");
        assertEquals(3, mgr.setAllCalls, "setAll should be called a third time");
    }

    @Test
    @DisplayName("Bullet helper should create a glyph and wrapped text")
    void testBulletHelperCreatesFormattedText() throws Exception {
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, false, new FakeManager()));
        JComponent bullet = runOnEdt(() -> dialog.createBullet(Resource.WELCOME_MAIN_FEATURE_1));

        assertEquals(2, bullet.getComponentCount(), "Bullet component should have two parts: a glyph and text");

        assertInstanceOf(JLabel.class, bullet.getComponent(0), "First part should be the glyph label");
        JLabel glyph = (JLabel) bullet.getComponent(0);
        assertEquals("•", glyph.getText(), "Glyph should be a bullet character");

        assertInstanceOf(JLabel.class, bullet.getComponent(1), "Second part should be the text label");
        JLabel text = (JLabel) bullet.getComponent(1);
        assertTrue(text.getText().contains("width:" + WRAP_WIDTH), "Text should be HTML-wrapped with a fixed width");
    }



    @Test
    @DisplayName("UI should show MCP disabled info when MCP is not enabled")
    void testMcpDisabledUi() throws Exception {
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, false, new FakeManager()));
        JBLabel mcpDisabledIcon = findMcpDisabledLabel(dialog.getContentPane());
        assertNotNull(mcpDisabledIcon, "MCP disabled label should exist");
        assertNotNull(mcpDisabledIcon.getIcon(), "Icon should be present when MCP is disabled");
        assertEquals(
                "Checkmarx MCP is not enabled for this tenant.",
                mcpDisabledIcon.getToolTipText(),
                "Tooltip should explain that MCP is disabled"
        );
    }



    @Test
    @DisplayName("Checkbox tooltip should change when state changes")
    void testCheckboxTooltipChangesWithState() throws Exception {
        FakeManager mgr = new FakeManager();
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, true, mgr));

        JBCheckBox checkbox = dialog.getRealTimeScannersCheckbox();
        assertNotNull(checkbox, "Checkbox should be present when MCP is enabled");

        // Initially checked, should show disable message
        assertTrue(checkbox.isSelected());
        assertEquals("Disable all real-time scanners", checkbox.getToolTipText());

        // Uncheck the box and verify tooltip changes
        runOnEdt(() -> {
            checkbox.setSelected(false);
            checkbox.getActionListeners()[0].actionPerformed(null);
            return null;
        });
        assertEquals("Enable all real-time scanners", checkbox.getToolTipText(),
                "Tooltip should show enable message when unchecked");

        // Check the box again and verify tooltip changes back
        runOnEdt(() -> {
            checkbox.setSelected(true);
            checkbox.getActionListeners()[0].actionPerformed(null);
            return null;
        });
        assertEquals("Disable all real-time scanners", checkbox.getToolTipText(),
                "Tooltip should show disable message when checked again");
    }

    // region Helpers
    /**
     * Utility: traverses components recursively to find the MCP disabled JBLabel.
     */
    private JBLabel findMcpDisabledLabel(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JBLabel) {
                JBLabel label = (JBLabel) comp;
                if ("Checkmarx MCP is not enabled for this tenant.".equals(label.getToolTipText())) {
                    return label;
                }
            } else if (comp instanceof Container) {
                JBLabel nested = findMcpDisabledLabel((Container) comp);
                if (nested != null) return nested;
            }
        }
        return null;
    }


    /**
     * Executes a Swing operation on the Event Dispatch Thread (EDT) and waits for it to complete.
     * This is crucial for testing Swing components safely.
     */
    private <T> T runOnEdt(SupplierWithException<T> supplier) throws Exception {
        final Object[] holder = new Object[2];
        SwingUtilities.invokeAndWait(() -> {
            try {
                holder[0] = supplier.get();
            } catch (Throwable t) {
                holder[1] = t;
            }
        });
        if (holder[1] != null) {
            if (holder[1] instanceof Exception) throw (Exception) holder[1];
            throw new RuntimeException("Error on EDT", (Throwable) holder[1]);
        }
        @SuppressWarnings("unchecked")
        T value = (T) holder[0];
        return value;
    }

    /**
     * Functional interface for a supplier that can throw an exception.
     */
    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    // endregion
}
