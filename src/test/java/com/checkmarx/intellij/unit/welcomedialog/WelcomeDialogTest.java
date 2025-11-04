package com.checkmarx.intellij.unit.welcomedialog;

import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.ui.WelcomeDialog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WelcomeDialog} logic.
 * Ensures realtime toggle orchestration & bullet layout composition work as intended.
 */
public class WelcomeDialogTest {

    /** Fake manager tracking calls for verification. */
    static class FakeManager implements WelcomeDialog.RealTimeSettingsManager {
        final AtomicBoolean enabled = new AtomicBoolean(false);
        int setAllCalls = 0;
        @Override public boolean areAllEnabled() { return enabled.get(); }
        @Override public void setAll(boolean enable) { enabled.set(enable); setAllCalls++; }
    }

    @Test
    @DisplayName("MCP enabled: initialization forces enable when starting disabled")
    void testInitializationEnablesAll() throws Exception {
        FakeManager mgr = new FakeManager();
        assertFalse(mgr.areAllEnabled());
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, true, mgr));
        assertTrue(mgr.areAllEnabled());
        assertEquals(1, mgr.setAllCalls);
        assertEquals("Scanners enabled", dialog.getAggregateStatusText());
        assertNotNull(dialog.getToggleIconLabel());
    }

    @Test
    @DisplayName("MCP disabled: toggle icon hidden and initialization does not force enable")
    void testMcpDisabledHidesToggle() throws Exception {
        FakeManager mgr = new FakeManager();
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, false, mgr));
        assertNull(dialog.getToggleIconLabel());
        assertFalse(mgr.areAllEnabled());
        assertEquals(0, mgr.setAllCalls);
        assertEquals("", dialog.getAggregateStatusText());
    }

    @Test
    @DisplayName("Clicking toggle flips aggregate realtime state and updates accessibility text")
    void testToggleClickFlipsState() throws Exception {
        FakeManager mgr = new FakeManager();
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, true, mgr));
        JLabel toggle = dialog.getToggleIconLabel();
        assertNotNull(toggle);
        assertTrue(mgr.areAllEnabled());
        runOnEdt(() -> { fireClick(toggle); return null; }); // disable
        assertFalse(mgr.areAllEnabled());
        assertEquals("Scanners disabled", dialog.getAggregateStatusText());
        runOnEdt(() -> { fireClick(toggle); return null; }); // re-enable
        assertTrue(mgr.areAllEnabled());
        assertEquals("Scanners enabled", dialog.getAggregateStatusText());
        assertTrue(mgr.setAllCalls >= 2);
    }

    @Test
    @DisplayName("Bullet helper creates glyph + wrapped text")
    void testBulletHelper() throws Exception {
        FakeManager mgr = new FakeManager();
        WelcomeDialog dialog = runOnEdt(() -> new WelcomeDialog(null, true, mgr));
        JComponent bullet = runOnEdt(() -> dialog.createBullet(Resource.WELCOME_MAIN_FEATURE_1));
        assertEquals(2, bullet.getComponentCount());
        assertInstanceOf(JLabel.class, bullet.getComponent(0));
        JLabel glyph = (JLabel) bullet.getComponent(0);
        assertEquals("•", glyph.getText());
        JLabel text = (JLabel) bullet.getComponent(1);
        assertTrue(text.getText().contains("width:" + WelcomeDialog.WRAP_WIDTH));
    }

    // Helpers

    private interface SupplierWithException<T> { T get() throws Exception; }

    private <T> T runOnEdt(SupplierWithException<T> supplier) throws Exception {
        final Object[] holder = new Object[2];
        SwingUtilities.invokeAndWait(() -> {
            try { holder[0] = supplier.get(); } catch (Throwable t) { holder[1] = t; }
        });
        if (holder[1] != null) {
            if (holder[1] instanceof Exception) throw (Exception) holder[1];
            throw new RuntimeException(holder[1].toString(), (Throwable) holder[1]);
        }
        @SuppressWarnings("unchecked") T value = (T) holder[0];
        return value;
    }

    private static void fireClick(JLabel label) {
        for (MouseListener ml : label.getMouseListeners()) {
            ml.mouseClicked(new MouseEvent(label, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false));
        }
    }
}
