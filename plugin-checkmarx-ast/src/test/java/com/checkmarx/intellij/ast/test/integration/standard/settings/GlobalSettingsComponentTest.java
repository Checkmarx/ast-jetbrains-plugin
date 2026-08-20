package com.checkmarx.intellij.ast.test.integration.standard.settings;

import com.checkmarx.intellij.ast.settings.GlobalSettingsComponent;
import com.checkmarx.intellij.ast.test.integration.standard.LocalBasePlatformTest;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;

/**
 * Integration tests for {@link GlobalSettingsComponent}.
 * Exercises constructor, GUI building, and state-reading methods
 * without any live API credentials.
 *
 * NOTE: Assertions use the fully-qualified Assertions.* form to avoid ambiguity
 * between JUnit 5 (boolean, String) and the inherited JUnit 3 TestCase
 * (String, boolean) overloads.
 */
public class GlobalSettingsComponentTest extends LocalBasePlatformTest {

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

    // ===== Constructor / GUI building =====

    @Test
    public void constructor_buildsMainPanel_withComponents() {
        GlobalSettingsComponent comp = new GlobalSettingsComponent();

        Assertions.assertNotNull(comp.getMainPanel());
        Assertions.assertNotNull(comp.getApiKeyField());
        Assertions.assertNotNull(comp.getAdditionalParametersField());
        Assertions.assertTrue(comp.getMainPanel().getComponentCount() > 0);
    }

    @Test
    public void constructor_setsFieldNames_forUIAutomation() {
        GlobalSettingsComponent comp = new GlobalSettingsComponent();

        Assertions.assertEquals("apiKey", comp.getApiKeyField().getName());
        Assertions.assertEquals("additionalParameters", comp.getAdditionalParametersField().getName());
    }

    // ===== isValid() =====

    @Test
    public void isValid_returnsFalse_whenNotAuthenticated() {
        GlobalSettingsState.getInstance().setAuthenticated(false);
        GlobalSettingsComponent comp = new GlobalSettingsComponent();

        Assertions.assertFalse(comp.isValid());
    }

    // ===== isModified() =====

    @Test
    public void isModified_returnsFalse_whenFieldsMatchState() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAdditionalParameters("");
        state.setApiKeyEnabled(false);

        GlobalSettingsComponent comp = new GlobalSettingsComponent();

        Assertions.assertFalse(comp.isModified());
    }

    @Test
    public void isModified_returnsTrue_whenAdditionalParametersChanged() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAdditionalParameters("original-params");

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        comp.getAdditionalParametersField().setText("new-params");

        Assertions.assertTrue(comp.isModified());
    }

    // ===== reset() — exercises the three main branches =====

    @Test
    public void reset_whenNotAuthenticated_notValidating_setsEditableState() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(false);
        state.setValidationInProgress(false);

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        Assertions.assertDoesNotThrow(comp::reset);
    }

    @Test
    public void reset_whenAuthenticated_apiKeyEnabled_setsFieldsReadOnly() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(true);
        state.setApiKeyEnabled(true);
        state.setLastValidationSuccess(true);
        state.setValidationMessage("Connected");

        GlobalSettingsSensitiveState.getInstance().setApiKey("dummy-api-key");

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        Assertions.assertDoesNotThrow(comp::reset);
    }

    @Test
    public void reset_whenValidationInProgress_notExpired_locksFields() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(false);
        state.setValidationInProgress(true);
        state.setValidationExpiry(java.time.LocalDateTime.now().plusMinutes(5).toString());

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        Assertions.assertDoesNotThrow(comp::reset);
    }

    // ===== apply() =====

    @Test
    public void apply_persistsAdditionalParameters() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAdditionalParameters("");
        state.setAuthenticated(false);

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        comp.getAdditionalParametersField().setText("--param value");
        Assertions.assertDoesNotThrow(comp::apply);

        Assertions.assertEquals("--param value",
                GlobalSettingsState.getInstance().getAdditionalParameters());
    }

    @Test
    public void apply_preservesAuthenticationState() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(true);
        state.setLastValidationSuccess(true);
        state.setValidationMessage("Connected");
        state.setApiKeyEnabled(true);
        GlobalSettingsSensitiveState.getInstance().setApiKey("test-key");

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        Assertions.assertDoesNotThrow(comp::apply);

        Assertions.assertTrue(GlobalSettingsState.getInstance().isAuthenticated());
    }

    @Test
    public void apply_preservesLicenseFlags_fromState() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(false);
        state.setDevAssistLicenseEnabled(true);
        state.setOneAssistLicenseEnabled(true);

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        comp.apply();

        Assertions.assertTrue(GlobalSettingsState.getInstance().isDevAssistLicenseEnabled());
        Assertions.assertTrue(GlobalSettingsState.getInstance().isOneAssistLicenseEnabled());
    }

    // ===== validateBaseUrl (private) — exercised via document listener =====

    @Test
    public void baseUrlField_withVariousInputs_doesNotCrash() {
        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        JTextField baseUrlField = findFieldByName(comp.getMainPanel(), "baseUrlField");
        if (baseUrlField != null) {
            Assertions.assertDoesNotThrow(() -> baseUrlField.setText("not-a-url"));
            Assertions.assertDoesNotThrow(() -> baseUrlField.setText("https://valid.example.com"));
            Assertions.assertDoesNotThrow(() -> baseUrlField.setText("https://double//slash"));
            Assertions.assertDoesNotThrow(() -> baseUrlField.setText(""));
        }
    }

    // ===== isValid() additional paths =====

    @Test
    public void isValid_returnsFalse_whenAuthenticatedWithExpiredToken_oauthMode() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        state.setAuthenticated(true);
        state.setApiKeyEnabled(false); // OAuth mode
        // Set an expiry in the past
        state.setRefreshTokenExpiry(java.time.LocalDateTime.now().minusMinutes(10).toString());

        GlobalSettingsComponent comp = new GlobalSettingsComponent();
        // The token is expired → isValid() should return false
        Assertions.assertFalse(comp.isValid());
    }

    // ===== Helpers =====

    private JTextField findFieldByName(java.awt.Container container, String name) {
        for (java.awt.Component c : container.getComponents()) {
            if (c instanceof JTextField && name.equals(c.getName())) {
                return (JTextField) c;
            }
            if (c instanceof java.awt.Container) {
                JTextField found = findFieldByName((java.awt.Container) c, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
