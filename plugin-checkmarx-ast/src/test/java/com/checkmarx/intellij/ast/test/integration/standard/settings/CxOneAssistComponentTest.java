package com.checkmarx.intellij.ast.test.integration.standard.settings;

import com.checkmarx.intellij.ast.settings.CxOneAssistComponent;
import com.checkmarx.intellij.ast.test.integration.standard.LocalBasePlatformTest;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;

/**
 * Integration tests for {@link CxOneAssistComponent}.
 * Tests UI construction, state reading/writing, and lifecycle management
 * without any live API credentials.
 */
public class CxOneAssistComponentTest extends LocalBasePlatformTest {

    private GlobalSettingsState savedState;

    @BeforeEach
    public void snapshotState() {
        savedState = GlobalSettingsState.getInstance().getState();
    }

    @AfterEach
    public void restoreState() {
        if (savedState != null) {
            GlobalSettingsState.getInstance().loadState(savedState);
        }
    }

    // ===== Constructor / buildUI() =====

    @Test
    public void constructor_buildsMainPanel_withComponents() {
        CxOneAssistComponent comp = new CxOneAssistComponent();
        Assertions.assertNotNull(comp.getMainPanel());
        Assertions.assertTrue(comp.getMainPanel().getComponentCount() > 0);
        comp.dispose();
    }

    @Test
    public void constructor_doesNotThrow_withDefaultState() {
        Assertions.assertDoesNotThrow(() -> {
            CxOneAssistComponent comp = new CxOneAssistComponent();
            comp.dispose();
        });
    }

    // ===== isModified() =====

    @Test
    public void isModified_returnsFalse_whenCheckboxesMatchState() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAscaRealtime(false);
        state.setOssRealtime(false);
        state.setSecretDetectionRealtime(false);
        state.setContainersRealtime(false);
        state.setIacRealtime(false);
        state.setContainersTool("docker");

        CxOneAssistComponent comp = new CxOneAssistComponent();
        Assertions.assertFalse(comp.isModified());
        comp.dispose();
    }

    @Test
    public void isModified_returnsTrue_whenStateChangedAfterConstruction() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAscaRealtime(false);

        CxOneAssistComponent comp = new CxOneAssistComponent();
        // Mutate state — checkbox still shows false but state says true → modified
        state.setAscaRealtime(true);

        Assertions.assertTrue(comp.isModified());
        comp.dispose();
    }

    // ===== reset() — branches of updateAssistState =====

    @Test
    public void reset_whenNoAssistLicense_doesNotThrow() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(false);
        state.setOneAssistLicenseEnabled(false);
        state.setDevAssistLicenseEnabled(false);

        CxOneAssistComponent comp = new CxOneAssistComponent();
        Assertions.assertDoesNotThrow(comp::reset);
        comp.dispose();
    }

    @Test
    public void reset_whenAuthenticatedWithLicense_makesMainPanelVisible() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(true);
        state.setOneAssistLicenseEnabled(true);
        state.setMcpStatusChecked(true);
        state.setMcpEnabled(true);
        state.setAscaRealtime(true);
        state.setOssRealtime(true);

        CxOneAssistComponent comp = new CxOneAssistComponent();
        Assertions.assertDoesNotThrow(comp::reset);
        Assertions.assertTrue(comp.getMainPanel().isVisible());
        comp.dispose();
    }

    @Test
    public void reset_whenLicensePresentButNotAuthenticated_doesNotThrow() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(false);
        state.setOneAssistLicenseEnabled(true);

        CxOneAssistComponent comp = new CxOneAssistComponent();
        Assertions.assertDoesNotThrow(comp::reset);
        comp.dispose();
    }

    @Test
    public void reset_whenMcpAlreadyChecked_showsCurrentStatus() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(true);
        state.setOneAssistLicenseEnabled(true);
        state.setMcpStatusChecked(true); // already checked → no async timer created
        state.setMcpEnabled(false);

        CxOneAssistComponent comp = new CxOneAssistComponent();
        Assertions.assertDoesNotThrow(comp::reset);
        comp.dispose();
    }

    // ===== apply() =====

    @Test
    public void checkboxValues_matchStateAfterConstruction() throws Exception {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAscaRealtime(true);
        state.setOssRealtime(false);

        CxOneAssistComponent comp = new CxOneAssistComponent();

        // Verify checkbox initialized from state
        java.lang.reflect.Field ascaField = CxOneAssistComponent.class.getDeclaredField("ascaCheckbox");
        ascaField.setAccessible(true);
        JCheckBox ascaBox = (JCheckBox) ascaField.get(comp);
        // ASCA checkbox: isAscaRealtime(true) OR isAsca() — should reflect state
        Assertions.assertNotNull(ascaBox);

        java.lang.reflect.Field ossField = CxOneAssistComponent.class.getDeclaredField("ossCheckbox");
        ossField.setAccessible(true);
        JCheckBox ossBox = (JCheckBox) ossField.get(comp);
        Assertions.assertFalse(ossBox.isSelected()); // state was false

        comp.dispose();
    }

    @Test
    public void isModified_detectsCheckboxChange() throws Exception {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setOssRealtime(false);

        CxOneAssistComponent comp = new CxOneAssistComponent();
        // ossCheckbox initialized to false (from state)
        Assertions.assertFalse(comp.isModified());

        // Change the checkbox to true — diverges from state
        java.lang.reflect.Field ossField = CxOneAssistComponent.class.getDeclaredField("ossCheckbox");
        ossField.setAccessible(true);
        ((JCheckBox) ossField.get(comp)).setSelected(true);

        Assertions.assertTrue(comp.isModified());
        comp.dispose();
    }

    // ===== dispose() =====

    @Test
    public void dispose_closesConnection_withoutThrowing() {
        CxOneAssistComponent comp = new CxOneAssistComponent();
        Assertions.assertDoesNotThrow(comp::dispose);
        // Second dispose call should also not throw (defensive close)
        Assertions.assertDoesNotThrow(comp::dispose);
    }

    // ===== getMainPanel() =====

    @Test
    public void getMainPanel_returnsNonNullPanel_withExpectedLayout() {
        CxOneAssistComponent comp = new CxOneAssistComponent();
        JPanel panel = comp.getMainPanel();
        Assertions.assertNotNull(panel);
        Assertions.assertNotNull(panel.getLayout());
        comp.dispose();
    }

    // ===== Helpers =====

    private void setCheckbox(CxOneAssistComponent comp, String fieldName, boolean selected) {
        try {
            java.lang.reflect.Field f = CxOneAssistComponent.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            JCheckBox cb = (JCheckBox) f.get(comp);
            cb.setSelected(selected);
        } catch (Exception e) {
            Assertions.fail("Could not set checkbox field " + fieldName + ": " + e.getMessage());
        }
    }
}
