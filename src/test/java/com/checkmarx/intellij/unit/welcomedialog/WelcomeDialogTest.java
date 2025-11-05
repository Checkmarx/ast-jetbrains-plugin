package com.checkmarx.intellij.unit.welcomedialog;

import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.ui.WelcomeDialog;
import com.intellij.ui.components.JBCheckBox;
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
    @DisplayName("MCP enabled: initialization should force enable when starting disabled")
    void testInitializationEnablesAllWhenMcpEnabled() throws Exception {
        FakeManager mgr = new FakeManager();
        assertFalse(mgr.areAllEnabled(), "Precondition: settings should be disabled");

        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, true, mgr));

        assertTrue(mgr.areAllEnabled(), "Settings should be enabled after dialog initialization");
        assertEquals(1, mgr.setAllCalls, "setAll should be called once during initialization");
        assertNotNull(dialog.getRealTimeScannersCheckbox(), "Checkbox should be present when MCP is enabled");
        assertTrue(dialog.getRealTimeScannersCheckbox().isSelected(), "Checkbox should be selected");
    }

    @Test
    @DisplayName("MCP disabled: checkbox should be hidden and initialization should not force enable")
    void testMcpDisabledHidesCheckbox() throws Exception {
        FakeManager mgr = new FakeManager();
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, false, mgr));

        assertNull(dialog.getRealTimeScannersCheckbox(), "Checkbox should not be present when MCP is disabled");
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
    @DisplayName("Dialog with MCP enabled should not re-apply settings if already enabled")
    void testInitialStateWithMcpEnabled() throws Exception {
        FakeManager mgr = new FakeManager();
        mgr.setAll(true); // Start with scanners already enabled
        assertEquals(1, mgr.setAllCalls);

        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, true, mgr));

        assertNotNull(dialog.getRealTimeScannersCheckbox());
        assertTrue(dialog.getRealTimeScannersCheckbox().isSelected());

        // Since scanners were already enabled, initializeRealtimeState() should NOT call setAll() again
        assertEquals(1, mgr.setAllCalls,
                "setAll(true) should NOT be called again if scanners are already enabled");
    }

    // region Helpers

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
