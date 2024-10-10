package com.checkmarx.intellij.settings.global;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Authentication;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.settings.SettingsComponent;
import com.checkmarx.intellij.settings.SettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.messages.MessageBus;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Component for the actual drawing of the global settings.
 */
public class GlobalSettingsComponent implements SettingsComponent {
    private static final Logger LOGGER = Utils.getLogger(GlobalSettingsComponent.class);

    private static GlobalSettingsState SETTINGS_STATE;
    private static GlobalSettingsSensitiveState SENSITIVE_SETTINGS_STATE;

    private final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();

    @Getter
    private final JPanel mainPanel = new JPanel();

    private final JBPasswordField apiKeyField = new JBPasswordField();

    private final ExpandableTextField additionalParametersField = new ExpandableTextField();

    private final JButton validateButton = new JButton(Bundle.message(Resource.VALIDATE_BUTTON));
    private final JBLabel validateResult = new JBLabel();
    private final JBCheckBox ascaCheckBox = new JBCheckBox(Bundle.message(Resource.ASCA_CHECKBOX));

    public GlobalSettingsComponent() {
        if (SETTINGS_STATE == null) {
            SETTINGS_STATE = GlobalSettingsState.getInstance();
        }
        if (SENSITIVE_SETTINGS_STATE == null) {
            SENSITIVE_SETTINGS_STATE = GlobalSettingsSensitiveState.getInstance();
        }
        addValidateConnectionListener();
        addAscaCheckBoxListener();

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
    public void apply() {
        GlobalSettingsState state = getStateFromFields();
        SETTINGS_STATE.apply(state);
        SENSITIVE_SETTINGS_STATE.apply(getSensitiveStateFromFields());
        messageBus.syncPublisher(SettingsListener.SETTINGS_APPLIED).settingsApplied();
    }

    @Override
    public void reset() {
        additionalParametersField.setText(SETTINGS_STATE.getAdditionalParameters());
        ascaCheckBox.setSelected(SETTINGS_STATE.isAsca());

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
        state.setAdditionalParameters(additionalParametersField.getText().trim());
        state.setAsca(ascaCheckBox.isSelected());

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
                    Authentication.validateConnection(getStateFromFields(),
                            getSensitiveStateFromFields());
                    setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
                    LOGGER.info(Bundle.message(Resource.VALIDATE_SUCCESS));
                } catch (IOException | URISyntaxException | InterruptedException e) {
                    setValidationResult(Bundle.message(Resource.VALIDATE_ERROR), JBColor.RED);
                    LOGGER.error(Bundle.message(Resource.VALIDATE_ERROR), e);
                } catch (CxException | CxConfig.InvalidCLIConfigException e) {
                    String msg = e.getMessage().trim();
                    int lastLineIndex = Math.max(msg.lastIndexOf('\n'), 0);
                    setValidationResult(msg.substring(lastLineIndex).trim(), JBColor.RED);
                    LOGGER.warn(Bundle.message(Resource.VALIDATE_FAIL, e.getMessage()));
                } finally {
                    validateButton.setEnabled(true);
                }
            });
        });
    }

    private void addAscaCheckBoxListener() {
        ascaCheckBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                System.out.println("Checkbox selected!");
            } else {
                System.out.println("Checkbox deselected!");
            }
        });
    }

    /**
     * Set validation message text and color.
     *
     * @param message text
     * @param color   color
     */
    private void setValidationResult(String message, JBColor color) {
        validateResult.setText(String.format("<html>%s</html>", message));
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

        addSectionHeader(Resource.CREDENTIALS_SECTION);
        addField(Resource.API_KEY, apiKeyField, true, true);

        addSectionHeader(Resource.SCAN_SECTION);
        addField(Resource.ADDITIONAL_PARAMETERS, additionalParametersField, true, false);
        mainPanel.add(new JBLabel());
        mainPanel.add(CxLinkLabel.buildDocLinkLabel(Constants.ADDITIONAL_PARAMETERS_HELP, Resource.HELP_CLI),
                "gapleft 5,gapbottom 10, wrap");

        // Add ASCA checkbox
        addSectionHeader(Resource.ASCA_DESCRIPTION);
        mainPanel.add(ascaCheckBox,"wrap");

        mainPanel.add(validateButton, "sizegroup bttn, gaptop 30");
        mainPanel.add(validateResult, "gapleft 5, gaptop 30");
    }

    private void setupFields() {
        apiKeyField.setName(Constants.FIELD_NAME_API_KEY);
        additionalParametersField.setName(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS);
        ascaCheckBox.setName(Constants.FIELD_NAME_ASCA);
    }


    private void addSectionHeader(Resource resource) {
        validatePanel();
        mainPanel.add(new JBLabel(Bundle.message(resource)), "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
    }

    private void addField(Resource resource, Component field, boolean gapAfter, boolean required) {
        validatePanel();
        String constraints = "grow, wrap";
        if (gapAfter) {
            constraints += ", " + Constants.FIELD_GAP_BOTTOM;
        }
        String label = String.format(Constants.FIELD_FORMAT,
                Bundle.message(resource),
                required ? Constants.REQUIRED_MARK : "");
        mainPanel.add(new JBLabel(label), gapAfter ? Constants.FIELD_GAP_BOTTOM : "");
        mainPanel.add(field, constraints);
    }

    private void validatePanel() {
        if (!(mainPanel.getLayout() instanceof MigLayout)) {
            throw new IllegalArgumentException("panel must be using MigLayout");
        }
    }

    public boolean isValid() {
        return SENSITIVE_SETTINGS_STATE.isValid();
    }
}
