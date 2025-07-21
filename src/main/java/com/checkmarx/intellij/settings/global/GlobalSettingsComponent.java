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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.messages.MessageBus;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
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
    private final JTextField baseUrlField = new JTextField();
    private final JTextField tenantField = new JTextField();
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

        return !String.valueOf(apiKeyField.getPassword()).equals(SENSITIVE_SETTINGS_STATE.getApiKey());
    }

    @Override
    public void apply() {
        // Always persist auth state
        GlobalSettingsState state = getStateFromFields();
        state.setAuthenticated(SETTINGS_STATE.isAuthenticated());
        state.setValidationInProgress(SETTINGS_STATE.isValidationInProgress());
        state.setRefreshTokenExpiry(SETTINGS_STATE.getRefreshTokenExpiry());
        state.setValidationExpiry(SETTINGS_STATE.getValidationExpiry());

        state.setBaseUrl(baseUrlField.getText().trim());
        state.setTenant(tenantField.getText().trim());

        // Only persist validation message if it's a success (green)
        if (validateResult.getForeground().equals(JBColor.GREEN)) {
            state.setValidationMessage(validateResult.getText());
            state.setLastValidationSuccess(true);
        } else {
            state.setValidationMessage(""); // Clear red error message
            state.setLastValidationSuccess(false);
        }

        SETTINGS_STATE.apply(state);
        SENSITIVE_SETTINGS_STATE.apply(state, getSensitiveStateFromFields());
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
        boolean isValidating = SETTINGS_STATE.isValidationInProgress();
        boolean useApiKey = SETTINGS_STATE.isUseApiKey();

        setInputFields();

        // Not authenticated, authentication in progress
        if (!isValid() && isValidating && !isValidateTimeExpired()){
            setValidationResult();
            setFieldsEditable(false); // Lock UI while validating
            connectButton.setEnabled(false);
            logoutButton.setEnabled(false);
        } else if (!isValid()){
            SETTINGS_STATE.setValidationInProgress(false);
            setLogoutState();
        } else { // Authenticated
            apiKeyRadio.setSelected(useApiKey);
            oauthRadio.setSelected(!useApiKey);
            baseUrlField.setEnabled(!useApiKey);
            tenantField.setEnabled(!useApiKey);
            apiKeyField.setEnabled(useApiKey);

            updateFieldLabels();
            setValidationResult();
            // OAuth succeeded asynchronously → refresh state dynamically
            SwingUtilities.invokeLater(() -> {
                setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
                validateResult.setVisible(true);
                logoutButton.setEnabled(true);
                connectButton.setEnabled(false);
                setFieldsEditable(false);
            });
            logoutButton.requestFocusInWindow();
        }
        ascaInstallationMsg.setVisible(false);
        SwingUtilities.invokeLater(() -> {
            if (useApiKey) {
                apiKeyField.requestFocusInWindow();
            } else {
                baseUrlField.requestFocusInWindow();
            }
        });
    }

    // Getting existing saved inpute details from the setting state.
    private void setInputFields() {
        additionalParametersField.setText(SETTINGS_STATE.getAdditionalParameters());
        ascaCheckBox.setSelected(SETTINGS_STATE.isAsca());
        apiKeyField.setText(SENSITIVE_SETTINGS_STATE.getApiKey());
        baseUrlField.setText(SETTINGS_STATE.getBaseUrl());
        tenantField.setText(SETTINGS_STATE.getTenant());
    }
    //Setting validation result to UI
    private void setValidationResult(){
        // Restore validation UI
        if (SETTINGS_STATE.isValidationInProgress()) {
            setValidationResult(Bundle.message(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);
            validateResult.setVisible(true);
        } else {
            if (SETTINGS_STATE.isLastValidationSuccess() && !StringUtils.isBlank(SETTINGS_STATE.getValidationMessage())) {
                setValidationResult(SETTINGS_STATE.getValidationMessage(), JBColor.GREEN);
                validateResult.setVisible(true);
            } else if (!StringUtils.isBlank(SETTINGS_STATE.getValidationMessage())) {
                setValidationResult(SETTINGS_STATE.getValidationMessage(), JBColor.RED);
                validateResult.setVisible(true);
            } else {
                validateResult.setVisible(false);
            }
        }
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
        state.setRefreshToken(SENSITIVE_SETTINGS_STATE.getRefreshToken());
        return state;
    }

    private void addValidateConnectionListener() {
        connectButton.addActionListener(event -> {
            connectButton.setEnabled(false);
            validateResult.setVisible(true);
            validateResult.requestFocusInWindow();
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
                            apply(); // Persist the state immediately
                            logoutButton.requestFocusInWindow();
                        });
                        LOGGER.info(Bundle.message(Resource.VALIDATE_SUCCESS));
                    } catch (Exception e) {
                        handleConnectionFailure(e);
                    }
                });
            } else {
                // Proceed for OAuth authentication
                proceedOAuthAuthentication();
            }
        });
    }

    /**
     * Proceed for authentication using OAUth
     */
    private void proceedOAuthAuthentication() {
        if (!validateBaseUrl()) {
            return; // Abort if UI validation fails
        }
        String baseUrl = baseUrlField.getText().trim().replaceAll("/+$", "");
        String tenant = tenantField.getText().trim();

        if (tenant.isEmpty()) {
            setValidationResult("Tenant name cannot be empty", JBColor.RED);
            connectButton.setEnabled(false);
            return;
        }
        connectButton.setEnabled(false);
        CheckmarxValidator.validateConnection(baseUrl, tenant).thenAccept(result -> {
            SwingUtilities.invokeLater(() -> {
                if (!result.isValid) {
                    // Validation failed – show error message
                    setValidationResult(result.error, JBColor.RED);
                    connectButton.setEnabled(true);
                    SETTINGS_STATE.setValidationInProgress(false);
                    apply();
                } else {
                    // Show OAuth confirmation dialog
                    int userChoice = Messages.showOkCancelDialog(
                            "You will be redirected to OAuth login in your default browser. Are you sure you want to continue?",
                            "Continue to OAuth Login",
                            "Continue", "Cancel", Messages.getQuestionIcon()
                    );

                    if (userChoice == Messages.OK) {
                        // Start OAuth authentication
                        setFieldsEditable(false);
                        setValidationResult(Bundle.message(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);
                        SETTINGS_STATE.setValidationInProgress(true);
                        SETTINGS_STATE.setValidationExpiry(getValidationExpiry());
                        apply();

                        new AuthService().authenticate(baseUrl, tenant, authResult -> {
                            if (authResult.containsKey(Constants.AuthConstants.REFRESH_TOKEN)) {
                                handleOAuthSuccess(authResult); // Extract token
                            } else {
                                handleOAuthFailure(authResult.get(Constants.AuthConstants.ERROR).toString());
                            }
                        });
                    } else {
                        connectButton.setEnabled(true);
                        validateResult.setVisible(false);
                        SETTINGS_STATE.setValidationInProgress(false);
                        apply();
                    }
                }
            });
        });
    }

    /**
     * Handle post-authentication success state
     */
    private void handleOAuthSuccess(Map<String, Object> refreshTokenDetails) {
        SwingUtilities.invokeLater(() -> {
            sessionConnected = true;

            setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
            validateResult.setVisible(true);

            logoutButton.setEnabled(true);
            connectButton.setEnabled(false);
            setFieldsEditable(false);

            SETTINGS_STATE.setAuthenticated(true);
            SETTINGS_STATE.setValidationInProgress(false);
            SETTINGS_STATE.setValidationExpiry(null);
            SENSITIVE_SETTINGS_STATE.setRefreshToken(refreshTokenDetails.get(Constants.AuthConstants.REFRESH_TOKEN).toString());
            SETTINGS_STATE.setRefreshTokenExpiry(refreshTokenDetails.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY).toString());
            apply();
            notifyAuthSuccess(); // Even if panel is not showing now
        });
    }

    /**
     * Handle post-authentication failure state
     */
    private void handleOAuthFailure(String error) {
        SwingUtilities.invokeLater(() -> {
            sessionConnected = false;
            SETTINGS_STATE.setValidationInProgress(false);
            SETTINGS_STATE.setValidationExpiry(null);
            SETTINGS_STATE.setAuthenticated(false);

            setValidationResult(error, JBColor.RED);
            validateResult.setVisible(true);

            connectButton.setEnabled(true);
            setFieldsEditable(true);

            apply();
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
        mainPanel.add(apiKeyRadio, "aligny top");
        mainPanel.add(apiKeyField, "growx, wrap, aligny top");

        oauthRadio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setFieldsEditable(true);
                updateFieldLabels();
                updateConnectButtonState();
                SwingUtilities.invokeLater(() -> baseUrlField.requestFocusInWindow());
            }
        });
        apiKeyRadio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setFieldsEditable(true);
                updateFieldLabels();
                updateConnectButtonState();
                SwingUtilities.invokeLater(() -> apiKeyField.requestFocusInWindow());
            }
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

    // Listener to update state when switching to OAuth
        oauthRadio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setFieldsEditable(true);
                updateFieldLabels();
                updateConnectButtonState();
                SwingUtilities.invokeLater(() -> baseUrlField.requestFocusInWindow());
            }
        });

    // Listener to update state when switching to API Key
        apiKeyRadio.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setFieldsEditable(true);
                updateFieldLabels();
                updateConnectButtonState();
                SwingUtilities.invokeLater(() -> apiKeyField.requestFocusInWindow());
            }
        });

        logoutButton.setName("logoutButton");
        additionalParametersField.setName(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS);
        ascaCheckBox.setName(Constants.FIELD_NAME_ASCA);

        // Set initial field states
        baseUrlField.setEnabled(true);
        tenantField.setEnabled(true);
        apiKeyField.setEnabled(false);
        logoutButton.setEnabled(false);
        baseUrlLabel.setText(String.format(Constants.FIELD_FORMAT, "Checkmarx One Base URL", Constants.REQUIRED_MARK));
        tenantLabel.setText(String.format(Constants.FIELD_FORMAT, "Tenant Name", Constants.REQUIRED_MARK));

        boolean useApiKey = SETTINGS_STATE.isUseApiKey();
        apiKeyRadio.setSelected(useApiKey);
        oauthRadio.setSelected(!useApiKey);
    }

    private boolean validateBaseUrl() {
        String rawInput = baseUrlField.getText().trim();

        if (rawInput.matches(".*/{2,}$")) {
            setValidationResult("Invalid base URL", JBColor.RED);
            connectButton.setEnabled(false);
            return false;
        }

        if (rawInput.isEmpty()) {
            setValidationResult("", JBColor.GREEN);
            connectButton.setEnabled(false);
            return false;
        }

        if (!isValidUrl(rawInput)) {
            setValidationResult("Invalid URL format", JBColor.RED);
            connectButton.setEnabled(false);
            return false;
        }

        setValidationResult("", JBColor.GREEN);
        return true;
    }

    // Helper method for URL validation
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
            boolean isBaseUrlValid = isValidUrl(baseUrlField.getText().trim());
            boolean isBaseUrlNotEmpty = !baseUrlField.getText().trim().isEmpty();
            boolean isTenantNotEmpty = !tenantField.getText().trim().isEmpty();
            enabled = isBaseUrlValid && isBaseUrlNotEmpty && isTenantNotEmpty;
        } else if (apiKeyRadio.isSelected()) {
            enabled = !String.valueOf(apiKeyField.getPassword()).trim().isEmpty();
        }

        if (SETTINGS_STATE.isAuthenticated() || SETTINGS_STATE.isValidationInProgress()) {
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
                setLogoutState();
                notifyLogout();
            }
            // else: Do nothing (user clicked Cancel)
        });
    }

    // Setting state on log out.
    private void setLogoutState() {
        sessionConnected = false;
        oauthRadio.setSelected(true);
        validateResult.setText(Bundle.message(Resource.LOGOUT_SUCCESS));
        validateResult.setForeground(JBColor.GREEN);
        validateResult.setVisible(true);
        connectButton.setEnabled(true);
        logoutButton.setEnabled(false);
        setFieldsEditable(true);
        updateConnectButtonState();
        SETTINGS_STATE.setAuthenticated(false); // Update authentication state
        if (!SETTINGS_STATE.isUseApiKey()) { // if oauth login is enabled
            SENSITIVE_SETTINGS_STATE.deleteRefreshToken();
        }
        apply();
        updateConnectButtonState(); // Ensure the Connect button state is updated
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
            baseUrlLabel.setText(String.format(Constants.FIELD_FORMAT, "Checkmarx One Base URL", Constants.REQUIRED_MARK));
            tenantLabel.setText(String.format(Constants.FIELD_FORMAT, "Tenant Name", Constants.REQUIRED_MARK));
            apiKeyRadio.setText("API Key");
        } else {
            baseUrlLabel.setText("Checkmarx One Base URL");
            tenantLabel.setText("Tenant Name");
            apiKeyRadio.setText(String.format(Constants.FIELD_FORMAT, "API Key", Constants.REQUIRED_MARK));
        }
    }

    private void validatePanel() {
        if (!(mainPanel.getLayout() instanceof MigLayout)) {
            throw new IllegalArgumentException("panel must be using MigLayout");
        }
    }

    public boolean isValid() {
        if (SETTINGS_STATE.isAuthenticated() && StringUtil.isEmpty(SENSITIVE_SETTINGS_STATE.getApiKey())
                && StringUtil.isEmpty(SENSITIVE_SETTINGS_STATE.getRefreshToken())){
            //This condition handles if the user is authenticated but no sensitive data is present (due to explicitly clearing it form storage)
            SETTINGS_STATE.setAuthenticated(false);
            SETTINGS_STATE.setValidationMessage("");
            return false;
        }
        return SETTINGS_STATE.isAuthenticated() && SENSITIVE_SETTINGS_STATE.isValid(SETTINGS_STATE);
    }

    private void setFieldsEditable(boolean editable) {
        boolean oauthSelected = oauthRadio.isSelected();
        boolean apiKeySelected = apiKeyRadio.isSelected();

        // Enable/disable input fields
        baseUrlField.setEnabled(editable && oauthSelected);
        tenantField.setEnabled(editable && oauthSelected);
        apiKeyField.setEnabled(editable && apiKeySelected);

        // Always keep radio buttons enabled
        oauthRadio.setEnabled(editable);
        apiKeyRadio.setEnabled(editable);

        // System default colors
        Color enabledColor = UIManager.getColor("Label.foreground");
        Color disabledColor = UIManager.getColor("Label.disabledForeground");

        // Simulate label fading manually
        oauthRadio.setForeground((editable && oauthRadio.isSelected()) ? enabledColor : disabledColor);
        oauthLabel.setForeground((editable && oauthSelected) ? enabledColor : disabledColor);
        baseUrlLabel.setForeground((editable && oauthSelected) ? enabledColor : disabledColor);
        tenantLabel.setForeground((editable && oauthSelected) ? enabledColor : disabledColor);
        apiKeyRadio.setForeground((editable && apiKeySelected) ? enabledColor : disabledColor);

        // Repaint to ensure update
        baseUrlLabel.repaint();
        tenantLabel.repaint();
        oauthLabel.repaint();
        apiKeyRadio.repaint();
    }

    /**
     * Display notification on notification area on successful logout
     */
    private void notifyLogout() {
        ApplicationManager.getApplication().invokeLater(() ->
                Utils.showNotification(Bundle.message(Resource.LOGOUT_SUCCESS_TITLE),
                        Bundle.message(Resource.LOGOUT_SUCCESS),
                        NotificationType.INFORMATION,
                        project)
        );
    }

    /**
     * Display notification on notification area on successful authentication
     */
    private void notifyAuthSuccess() {
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
    private void notifyAuthError(String errorMsg) {
        ApplicationManager.getApplication().invokeLater(() ->
                Utils.showNotification(Bundle.message(Resource.ERROR_AUTHENTICATION_TITLE),
                        errorMsg,
                        NotificationType.ERROR,
                        project)
        );
    }

    /**
     * Getting login validation timeout datetime
     * @return timestamp
     */
    private String getValidationExpiry(){
        long timeoutSeconds = Constants.AuthConstants.TIME_OUT_SECONDS+5L;
        return Utils.convertToLocalDateTime(timeoutSeconds, ZoneId.systemDefault()).toString();
    }


    /**
     * Checking if authentication validation time expired
     * @return true, if validation time is exceeded than current time otherwise false.
     */
    private boolean isValidateTimeExpired(){
        if(!StringUtils.isBlank(SETTINGS_STATE.getValidationExpiry())){
            return LocalDateTime.parse(SETTINGS_STATE.getValidationExpiry()).isBefore(LocalDateTime.now());
        }
        return false;
    }
}