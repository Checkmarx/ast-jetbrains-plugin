package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.commands.Authentication;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.settings.SettingsComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.fields.ExpandableTextField;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Component for the actual drawing of the global settings.
 */
public class GlobalSettingsComponent implements SettingsComponent {
    private static final Logger LOGGER = Utils.getLogger(GlobalSettingsComponent.class);

    private final static GlobalSettingsState SETTINGS_STATE = GlobalSettingsState.getInstance();
    private final static GlobalSettingsSensitiveState SENSITIVE_SETTINGS_STATE
            = GlobalSettingsSensitiveState.getInstance();

    @Getter
    private final JPanel mainPanel = new JPanel();

    private final JBTextField serverUrlField = new JBTextField();

    private final JBCheckBox useAuthUrlCheckbox = new JBCheckBox(Bundle.message(Resource.USE_AUTH_URL));
    private final JBTextField authUrlField = new JBTextField();

    private final JBTextField tenantField = new JBTextField();

    private final JBPasswordField apiKeyField = new JBPasswordField();

    private final ExpandableTextField additionalParametersField = new ExpandableTextField();

    private final JButton validateButton = new JButton(Bundle.message(Resource.VALIDATE_BUTTON));
    private final JBLabel validateResult = new JBLabel();

    public GlobalSettingsComponent() {
        addUseAuthUrlListener();
        addValidateConnectionListener();

        setupFields();
        buildGUI();
    }

    @Override
    public boolean isModified() {
        if (!Objects.equals(SENSITIVE_SETTINGS_STATE, getSensitiveStateFromFields())) {
            return true;
        }

        return !Objects.equals(SETTINGS_STATE, getStateFromFields());
    }

    @Override
    public void apply() throws ConfigurationException {
        SETTINGS_STATE.apply(getStateFromFields());
        SENSITIVE_SETTINGS_STATE.apply(getSensitiveStateFromFields());
    }

    @Override
    public void reset() {
        serverUrlField.setText(SETTINGS_STATE.getServerURL());
        useAuthUrlCheckbox.setSelected(SETTINGS_STATE.isUseAuthURL());
        authUrlField.setEnabled(SETTINGS_STATE.isUseAuthURL());
        authUrlField.setText(SETTINGS_STATE.isUseAuthURL() ? SETTINGS_STATE.getAuthURL() : "");
        tenantField.setText(SETTINGS_STATE.getTenantName());
        additionalParametersField.setText(SETTINGS_STATE.getAdditionalParameters());

        SENSITIVE_SETTINGS_STATE.reset();
        apiKeyField.setText(SENSITIVE_SETTINGS_STATE.getApiKey());

        validateResult.setVisible(false);
    }

    /**
     * Create a state object from what is currently in the fields
     *
     * @return state object
     */
    private GlobalSettingsState getStateFromFields() {
        GlobalSettingsState state = new GlobalSettingsState();

        state.setServerURL(serverUrlField.getText());
        state.setUseAuthURL(useAuthUrlCheckbox.isSelected());
        state.setAuthURL(useAuthUrlCheckbox.isSelected() ? authUrlField.getText() : "");
        state.setTenantName(tenantField.getText());
        state.setAdditionalParameters(additionalParametersField.getText());

        return state;
    }

    /**
     * Create a sensitive state object from what is currently in the fields
     *
     * @return sensitive state object
     */
    private GlobalSettingsSensitiveState getSensitiveStateFromFields() {
        GlobalSettingsSensitiveState state = new GlobalSettingsSensitiveState();

        state.setApiKey(String.valueOf(apiKeyField.getPassword()));

        return state;
    }

    /**
     * Add listener to enable/disable the auth URL field when the checkbox is selected.
     */
    private void addUseAuthUrlListener() {
        useAuthUrlCheckbox.addActionListener(event -> authUrlField.setEnabled(useAuthUrlCheckbox.isSelected()));
    }

    /**
     * Add listener to trigger validation of settings through the CLI.
     */
    private void addValidateConnectionListener() {

        // Validation button workflow
        validateButton.addActionListener(event -> {
            validateButton.setEnabled(false);
            validateResult.setVisible(true);
            setValidationResult(Bundle.message(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);
            CompletableFuture.runAsync(() -> {
                try {
                    int result = Authentication.validateConnection(getStateFromFields(),
                                                                   getSensitiveStateFromFields());
                    if (result == 0) {
                        setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
                        LOGGER.info(Bundle.message(Resource.VALIDATE_SUCCESS));
                    } else {
                        setValidationResult(Bundle.message(Resource.VALIDATE_FAIL), JBColor.RED);
                        LOGGER.warn(Bundle.message(Resource.VALIDATE_FAIL));
                    }
                } catch (Exception e) {
                    setValidationResult(Bundle.message(Resource.VALIDATE_ERROR), JBColor.RED);
                    LOGGER.error(Bundle.message(Resource.VALIDATE_ERROR), e);
                } finally {
                    validateButton.setEnabled(true);
                }
            });
        });
    }

    /**
     * Set validation message text and color.
     *
     * @param message text
     * @param color   color
     */
    private void setValidationResult(String message, JBColor color) {
        validateResult.setText(message);
        validateResult.setForeground(color);
    }

    /**
     * Build the GUI with {@link MigLayout}.
     * http://www.miglayout.com/QuickStart.pdf
     */
    private void buildGUI() {
        mainPanel.setLayout(new MigLayout("", "[][grow]"));

        mainPanel.add(CxLinkLabel.buildDocLinkLabel(Constants.INTELLIJ_HELP, Resource.HELP_JETBRAINS),
                      "span, growx, wrap, gapbottom 10");

        addSectionHeader(Resource.SERVER_SECTION);
        addField(Resource.SERVER_URL, serverUrlField, false);
        mainPanel.add(useAuthUrlCheckbox);
        mainPanel.add(authUrlField, "grow, wrap");
        addField(Resource.TENANT_NAME, tenantField, true);

        addSectionHeader(Resource.CREDENTIALS_SECTION);
        addField(Resource.API_KEY, apiKeyField, true);

        addSectionHeader(Resource.SCAN_SECTION);
        addField(Resource.ADDITIONAL_PARAMETERS, additionalParametersField, false);
        mainPanel.add(new JBLabel());
        mainPanel.add(CxLinkLabel.buildDocLinkLabel(Constants.ADDITIONAL_PARAMETERS_HELP, Resource.HELP_CLI),
                      "gapleft 5, wrap");

        mainPanel.add(validateButton, "sizegroup bttn, gaptop 30");
        mainPanel.add(validateResult, "gapleft 5");
    }

    private void setupFields() {
        serverUrlField.setName(Constants.FIELD_NAME_SERVER_URL);
        authUrlField.setName(Constants.FIELD_NAME_AUTH_URL);
        useAuthUrlCheckbox.setName(Constants.FIELD_NAME_USE_AUTH_URL);
        tenantField.setName(Constants.FIELD_NAME_TENANT);
        apiKeyField.setName(Constants.FIELD_NAME_API_KEY);
        additionalParametersField.setName(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS);
    }

    private void addSectionHeader(Resource resource) {
        validatePanel();
        mainPanel.add(new JBLabel(Bundle.message(resource)), "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
    }

    private void addField(Resource resource, Component field, boolean gapAfter) {
        validatePanel();
        String constraints = "grow, wrap";
        if (gapAfter) {
            constraints += ", gapbottom 15";
        }
        mainPanel.add(new JBLabel(Bundle.message(resource)));
        mainPanel.add(field, constraints);
    }

    private void validatePanel() {
        if (!(mainPanel.getLayout() instanceof MigLayout)) {
            throw new IllegalArgumentException("panel must be using MigLayout");
        }
    }
}
