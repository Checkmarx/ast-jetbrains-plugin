package com.checkmarx.intellij.settings.global;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Authentication;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.service.AscaService;
import com.checkmarx.intellij.service.AuthService;
import com.checkmarx.intellij.settings.SettingsComponent;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.util.CheckmarxValidator;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.messages.MessageBus;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;

public class GlobalSettingsComponent implements SettingsComponent {
    private static final Logger LOGGER = Utils.getLogger(GlobalSettingsComponent.class);

    private static GlobalSettingsState SETTINGS_STATE;
    private static GlobalSettingsSensitiveState SENSITIVE_SETTINGS_STATE;

    private final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    private final Project project = ProjectManager.getInstance().getDefaultProject();

    @Getter
    private final JPanel mainPanel = new JPanel();

    @Getter
    private final JBPasswordField apiKeyField = new JBPasswordField();
    private final ButtonGroup authGroup = new ButtonGroup();
    private final JRadioButton oauthRadio = new JRadioButton("OAuth");
    private final ExpandableTextField baseUrlField = new ExpandableTextField();
    private final ExpandableTextField tenantField = new ExpandableTextField();
    private final JRadioButton apiKeyRadio = new JRadioButton("API Key");
    private final JButton logoutButton = new JButton("Log out");
    private final JBLabel oauthLabel = new JBLabel("(Login using Checkmarx One credentials)");
    private final JBLabel baseUrlLabel = new JBLabel();
    private final JBLabel tenantLabel = new JBLabel();

    @Getter
    private final ExpandableTextField additionalParametersField = new ExpandableTextField();

    private final JButton connectButton = new JButton(Bundle.message(Resource.CONNECT_BUTTON));
    private final JBLabel validateResult = new JBLabel();

    @Getter
    private final JBCheckBox ascaCheckBox = new JBCheckBox(Bundle.message(Resource.ASCA_CHECKBOX));
    private final JBLabel ascaInstallationMsg = new JBLabel();
    private boolean sessionConnected = false;

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
        addLogoutListener();
    }

    @Override
    public boolean isModified() {
        if (SETTINGS_STATE == null) {
            SETTINGS_STATE = GlobalSettingsState.getInstance();
        }
        if (SENSITIVE_SETTINGS_STATE == null) {
            SENSITIVE_SETTINGS_STATE = GlobalSettingsSensitiveState.getInstance();
        }

        // Check only editable values
        if (!additionalParametersField.getText().trim().equals(SETTINGS_STATE.getAdditionalParameters())) {
            return true;
        }

        if (ascaCheckBox.isSelected() != SETTINGS_STATE.isAsca()) {
            return true;
        }

        if (apiKeyRadio.isSelected() != SETTINGS_STATE.isUseApiKey()) {
            return true;
        }

        if (!String.valueOf(apiKeyField.getPassword()).equals(SENSITIVE_SETTINGS_STATE.getApiKey())) {
            return true;
        }

        return false;
    }

    @Override
    public void apply() {
        if (!isModified()) {
            return; // Skip if nothing changed
        }
        GlobalSettingsState state = getStateFromFields();

        // Only persist validation message if it's a success (green)
        if (validateResult.getForeground().equals(JBColor.GREEN)) {
            state.setValidationMessage(validateResult.getText());
            state.setLastValidationSuccess(true);
        } else {
            state.setValidationMessage(""); // Clear red error message
            state.setLastValidationSuccess(false);
        }

        state.setAuthenticated(SETTINGS_STATE.isAuthenticated()); // Persist authentication state
        SETTINGS_STATE.apply(state);
        SENSITIVE_SETTINGS_STATE.apply(getSensitiveStateFromFields());
        messageBus.syncPublisher(SettingsListener.SETTINGS_APPLIED).settingsApplied();
    }

    @Override
    public void reset() {
        if (SETTINGS_STATE == null) {
            SETTINGS_STATE = GlobalSettingsState.getInstance();
        }
        if (SENSITIVE_SETTINGS_STATE == null) {
            SENSITIVE_SETTINGS_STATE = GlobalSettingsSensitiveState.getInstance();
        }

        // Restore fields from persistent state
        additionalParametersField.setText(SETTINGS_STATE.getAdditionalParameters());
        ascaCheckBox.setSelected(SETTINGS_STATE.isAsca());
        apiKeyField.setText(SENSITIVE_SETTINGS_STATE.getApiKey());

        boolean useApiKey = SETTINGS_STATE.isUseApiKey();
        apiKeyRadio.setSelected(useApiKey);
        oauthRadio.setSelected(!useApiKey);

        baseUrlField.setEnabled(!useApiKey);
        tenantField.setEnabled(!useApiKey);
        apiKeyField.setEnabled(useApiKey);

        updateFieldLabels();
        ascaInstallationMsg.setVisible(false);

        // Restore validation message only if it was a success (green)
        String validationMessage = SETTINGS_STATE.getValidationMessage();
        if (SETTINGS_STATE.isLastValidationSuccess() && validationMessage != null && !validationMessage.isEmpty()) {
            validateResult.setText(validationMessage);
            validateResult.setForeground(JBColor.GREEN);
            validateResult.setVisible(true);
        } else {
            validateResult.setVisible(false); // Hide red error messages on reopen
        }

        // Restore button states
        boolean isAuthenticated = SETTINGS_STATE.isAuthenticated();
        connectButton.setEnabled(!isAuthenticated);
        logoutButton.setEnabled(isAuthenticated);
        setFieldsEditable(!isAuthenticated);

        // Ensure the connect button state is updated
        updateConnectButtonState();
    }

    private GlobalSettingsState getStateFromFields() {
        GlobalSettingsState state = new GlobalSettingsState();
        state.setAdditionalParameters(additionalParametersField.getText().trim());
        state.setAsca(ascaCheckBox.isSelected());
        state.setUseApiKey(apiKeyRadio.isSelected());
        return state;
    }

    private GlobalSettingsSensitiveState getSensitiveStateFromFields() {
        GlobalSettingsSensitiveState state = new GlobalSettingsSensitiveState();
        state.setApiKey(String.valueOf(apiKeyField.getPassword()));
        return state;
    }

    private void addValidateConnectionListener() {
        connectButton.addActionListener(event -> {
            connectButton.setEnabled(false);
            validateResult.setVisible(true);
            setValidationResult(Bundle.message(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);

            if (apiKeyRadio.isSelected()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        if (ascaCheckBox.isSelected()) {
                            runAscaScanInBackground();
                        }
                        Authentication.validateConnection(getStateFromFields(), getSensitiveStateFromFields());
                        sessionConnected = true;
                        SwingUtilities.invokeLater(() -> {
                            setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
                            logoutButton.setEnabled(true);
                            connectButton.setEnabled(false);
                            setFieldsEditable(false);
                            SETTINGS_STATE.setAuthenticated(true);
                        });
                        LOGGER.info(Bundle.message(Resource.VALIDATE_SUCCESS));
                    } catch (Exception e) {
                        handleConnectionFailure(e);
                    }
                });
            } else {
                if (baseUrlField.getText().trim().isEmpty() || tenantField.getText().trim().isEmpty()) {
                    setValidationResult(Bundle.message(Resource.MISSING_FIELD, "Base URL or Tenant"), JBColor.RED);
                    connectButton.setEnabled(true);
                    return;
                }
                proceedOAuthAuthentication();
            }
        });
    }

    /**
     * Proceed for authentication using OAUth
     */
    private void proceedOAuthAuthentication() {
        String baseUrl = baseUrlField.getText().trim();
        String tenant = tenantField.getText().trim();

        connectButton.setEnabled(false); // Disable button during validation

        CheckmarxValidator.validateConnection(baseUrl, tenant).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                if (!result.isValid) {
                    // Validation failed – show error message
                    setValidationResult(result.error, JBColor.RED);
                    connectButton.setEnabled(true);
                } else {
                    // Show OAuth confirmation dialog
                    int userChoice = Messages.showOkCancelDialog(
                            "You will be redirected to OAuth login in your default browser. Are you sure you want to continue?",
                            "Continue to OAuth Login",
                            "Continue", "Cancel", Messages.getQuestionIcon()
                    );

                    if (userChoice == Messages.OK) {
                        // Start OAuth authentication
                        new AuthService().authenticate(baseUrl, tenant, authResult -> {
                            if (authResult.startsWith(Constants.AuthConstants.TOKEN)) {
                                handleOAuthSuccess(authResult.split(":")[1]); // Extract token
                            } else {
                                handleOAuthFailure(authResult);
                            }
                        });
                    } else {
                        connectButton.setEnabled(true);
                    }
                }
            });
        });
    }

    /**
     * Handle post-authentication success state
     */
    private void handleOAuthSuccess(String refreshToken) {
        SwingUtilities.invokeLater(() -> {
            setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
            logoutButton.setEnabled(true);
            connectButton.setEnabled(false);
            setFieldsEditable(false);
            sessionConnected = true;
            SETTINGS_STATE.setAuthenticated(true);// also persist
            SENSITIVE_SETTINGS_STATE.setRefreshToken(refreshToken);
            notifyAuthSuccess();
        });
    }

    /**
     * Handle post-authentication failure state
     */
    private void handleOAuthFailure(String error) {
        SwingUtilities.invokeLater(() -> {
            sessionConnected = false;
            setValidationResult(error, JBColor.RED);
            connectButton.setEnabled(true);
            notifyAuthError(error);
        });
    }

    private void handleConnectionFailure(Exception e) {
        SwingUtilities.invokeLater(() -> {
            setValidationResult(Bundle.message(Resource.VALIDATE_ERROR), JBColor.RED);
            connectButton.setEnabled(true);
        });
        LOGGER.error("Connection failed", e);
    }

    private void addAscaCheckBoxListener() {
        ascaCheckBox.addItemListener(e -> {
            if (e.getStateChange() != ItemEvent.SELECTED) {
                ascaInstallationMsg.setVisible(false);
                return;
            }
            runAscaScanInBackground();
        });
    }

    private void runAscaScanInBackground() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    ascaInstallationMsg.setVisible(false);
                    boolean installed = new AscaService().installAsca();
                    if (installed) {
                        setAscaInstallationMsg(Bundle.message(Resource.ASCA_STARTED_MSG), JBColor.GREEN);
                    } else {
                        setAscaInstallationMsg(Bundle.message(Resource.FAILED_INSTALL_ASCA), JBColor.RED);
                    }
                } catch (IOException | URISyntaxException | InterruptedException ex) {
                    LOGGER.warn(Bundle.message(Resource.ASCA_SCAN_WARNING), ex);
                    setAscaInstallationMsg(ex.getMessage(), JBColor.RED);
                } catch (CxException ex) {
                    String msg = ex.getMessage().trim();
                    int lastLineIndex = Math.max(msg.lastIndexOf('\n'), 0);
                    setAscaInstallationMsg(msg.substring(lastLineIndex).trim(), JBColor.RED);
                    LOGGER.warn(Bundle.message(Resource.ASCA_SCAN_WARNING, msg.substring(lastLineIndex).trim()));
                } finally {
                    if (ascaCheckBox.isSelected()) {
                        ascaInstallationMsg.setVisible(true);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                LOGGER.debug("ASCA scan completed.");
            }
        }.execute();
    }

    private void setValidationResult(String message, JBColor color) {
        validateResult.setText(String.format("<html>%s</html>", message));
        validateResult.setForeground(color);
    }

    private void setAscaInstallationMsg(String message, JBColor color) {
        ascaInstallationMsg.setText(String.format("<html>%s</html>", message));
        ascaInstallationMsg.setForeground(color);
    }

    private void buildGUI() {
        mainPanel.setLayout(new MigLayout("", "[][grow]", ""));
        mainPanel.add(CxLinkLabel.buildDocLinkLabel(Constants.INTELLIJ_HELP, Resource.HELP_JETBRAINS),
                "span, growx, wrap, gapbottom 10");
        addSectionHeader(Resource.CREDENTIALS_SECTION, false);
        mainPanel.add(oauthRadio, "split 2, span");
        mainPanel.add(oauthLabel, "gapleft 5, wrap");
        mainPanel.add(baseUrlLabel);
        mainPanel.add(baseUrlField, "growx, wrap");
        mainPanel.add(tenantLabel);
        mainPanel.add(tenantField, "growx, wrap");
        mainPanel.add(apiKeyRadio);
        mainPanel.add(apiKeyField, "growx, wrap");

        oauthRadio.addItemListener(e -> {
            boolean selected = oauthRadio.isSelected();
            baseUrlField.setEnabled(selected);
            tenantField.setEnabled(selected);
            apiKeyField.setEnabled(!selected);
            updateFieldLabels();
            updateConnectButtonState();
        });

        mainPanel.add(connectButton, "gaptop 10");
        mainPanel.add(logoutButton, "gaptop 10, wrap");
        mainPanel.add(validateResult, "span 2, gaptop 5, wrap");

        addSectionHeader(Resource.SCAN_SECTION, false);
        addField(Resource.ADDITIONAL_PARAMETERS, additionalParametersField, false, false);
        mainPanel.add(new JBLabel());
        mainPanel.add(CxLinkLabel.buildDocLinkLabel(Constants.ADDITIONAL_PARAMETERS_HELP, Resource.HELP_CLI),
                "gapleft 5,gapbottom 10, wrap");

        addSectionHeader(Resource.ASCA_DESCRIPTION, false);
        mainPanel.add(ascaCheckBox);
        mainPanel.add(ascaInstallationMsg, "gapleft 5, wrap");
    }

    private void setupFields() {
        apiKeyField.setName(Constants.FIELD_NAME_API_KEY);
        baseUrlField.setName("baseUrlField");
        tenantField.setName("tenantField");
        oauthRadio.setName("oauthRadio");
        apiKeyRadio.setName("apiKeyRadio");
        authGroup.add(oauthRadio);
        authGroup.add(apiKeyRadio);

        // Add validation for baseUrlField
        baseUrlField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                validateBaseUrl();
                updateConnectButtonState();
            }
        });

        tenantField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateConnectButtonState();
            }
        });

        apiKeyField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateConnectButtonState();
            }
        });

        logoutButton.setName("logoutButton");
        additionalParametersField.setName(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS);
        ascaCheckBox.setName(Constants.FIELD_NAME_ASCA);
        baseUrlField.setEnabled(true);
        tenantField.setEnabled(true);
        apiKeyField.setEnabled(false);
        logoutButton.setEnabled(false);
        baseUrlLabel.setText(String.format(Constants.FIELD_FORMAT, "Checkmarx One base URL:", Constants.REQUIRED_MARK));
        tenantLabel.setText(String.format(Constants.FIELD_FORMAT, "Tenant name:", Constants.REQUIRED_MARK));
        boolean useApiKey = SETTINGS_STATE.isUseApiKey();
        apiKeyRadio.setSelected(useApiKey);
        oauthRadio.setSelected(!useApiKey);
    }

    private void validateBaseUrl() {
        String baseUrl = baseUrlField.getText().trim();
        if (baseUrl.isEmpty()) {
            setValidationResult("", JBColor.GREEN); // Clear the message if the field is empty
            connectButton.setEnabled(false); // Disable the button
            return;
        }

        boolean isValid = isValidUrl(baseUrl);
        if (!isValid) {
            setValidationResult("Invalid URL format", JBColor.RED); // Show error for invalid URL
            connectButton.setEnabled(false); // Disable the button
        } else {
            setValidationResult("", JBColor.GREEN); // Clear the error message for valid URL
            updateConnectButtonState();
        }
    }

    // Helper method for URL validation (similar to JS isURL)
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateConnectButtonState() {
        boolean enabled = false;

        if (oauthRadio.isSelected()) {
            // Check for OAuth conditions
            boolean isBaseUrlValid = isValidUrl(baseUrlField.getText().trim());
            boolean isBaseUrlNotEmpty = !baseUrlField.getText().trim().isEmpty();
            boolean isTenantNotEmpty = !tenantField.getText().trim().isEmpty();
            enabled = isBaseUrlValid && isBaseUrlNotEmpty && isTenantNotEmpty;
        } else if (apiKeyRadio.isSelected()) {
            // Check for API Key conditions
            boolean isApiKeyNotEmpty = !String.valueOf(apiKeyField.getPassword()).trim().isEmpty();
            enabled = isApiKeyNotEmpty;
        }
        // Disable connect button if user is authenticated
        if (SETTINGS_STATE.isAuthenticated()) {
            enabled = false;
        }
        connectButton.setEnabled(enabled);
    }

    private void addLogoutListener() {
        logoutButton.addActionListener(e -> {
            int userChoice = Messages.showYesNoDialog(
                    "Are you sure you want to log out?",
                    "Confirm Logout",
                    "Yes",  // Yes button
                    "Cancel",  // Cancel button
                    Messages.getQuestionIcon()
            );

            if (userChoice == Messages.YES) {
                sessionConnected = false;
                baseUrlField.setText("");
                tenantField.setText("");
                apiKeyField.setText("");
                oauthRadio.setSelected(true);
                validateResult.setText(Bundle.message(Resource.LOGOUT_SUCCESS));
                validateResult.setForeground(JBColor.GREEN);
                validateResult.setVisible(true);
                connectButton.setEnabled(true);
                logoutButton.setEnabled(false);
                setFieldsEditable(true);
                updateConnectButtonState();
                SETTINGS_STATE.setAuthenticated(false); // Update authentication state
                if (!SETTINGS_STATE.isUseApiKey()) {
                    SENSITIVE_SETTINGS_STATE.deleteRefreshToken();
                }
            }
            // else: Do nothing (user clicked Cancel)
        });
    }

    private void addSectionHeader(Resource resource, boolean required) {
        validatePanel();
        String labelText = String.format(Constants.FIELD_FORMAT,
                Bundle.message(resource),
                required ? Constants.REQUIRED_MARK : "");
        mainPanel.add(new JBLabel(labelText), "split 2, span");
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

    private void updateFieldLabels() {
        if (oauthRadio.isSelected()) {
            baseUrlLabel.setText(String.format(Constants.FIELD_FORMAT, "Checkmarx One base URL:", Constants.REQUIRED_MARK));
            tenantLabel.setText(String.format(Constants.FIELD_FORMAT, "Tenant name:", Constants.REQUIRED_MARK));
            apiKeyRadio.setText("API Key");
        } else {
            baseUrlLabel.setText("Checkmarx One base URL:");
            tenantLabel.setText("Tenant name:");
            apiKeyRadio.setText(String.format(Constants.FIELD_FORMAT, "API Key", Constants.REQUIRED_MARK));
        }
    }

    private void validatePanel() {
        if (!(mainPanel.getLayout() instanceof MigLayout)) {
            throw new IllegalArgumentException("panel must be using MigLayout");
        }
    }

    public boolean isValid() {
        return SENSITIVE_SETTINGS_STATE.isValid();
    }

    private void setFieldsEditable(boolean editable) {
        baseUrlField.setEnabled(editable && oauthRadio.isSelected());
        tenantField.setEnabled(editable && oauthRadio.isSelected());
        apiKeyField.setEnabled(editable && apiKeyRadio.isSelected());

        oauthRadio.setEnabled(editable);
        apiKeyRadio.setEnabled(editable);
    }

    /**
     * Display notification on notification area on successful authentication
     */
    public void notifyAuthSuccess() {
        ApplicationManager.getApplication().invokeLater(() ->
                Utils.showNotification(Bundle.message(Resource.SUCCESS_AUTHENTICATION_TITLE),
                        Bundle.message(Resource.VALIDATE_SUCCESS),
                        NotificationType.INFORMATION,
                        project)
        );
    }

    /**
     * Display notification on notification area on failure authentication
     */
    public void notifyAuthError(String errorMsg) {
        ApplicationManager.getApplication().invokeLater(() ->
                Utils.showNotification(Bundle.message(Resource.ERROR_AUTHENTICATION_TITLE),
                        errorMsg,
                        NotificationType.ERROR,
                        project)
        );
    }
}