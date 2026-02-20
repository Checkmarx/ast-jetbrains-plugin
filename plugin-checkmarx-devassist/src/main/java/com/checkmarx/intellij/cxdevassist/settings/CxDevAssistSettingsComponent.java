package com.checkmarx.intellij.cxdevassist.settings;

import com.checkmarx.intellij.common.commands.Authentication;
import com.checkmarx.intellij.common.commands.TenantSetting;
import com.checkmarx.intellij.common.components.CxLinkLabel;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsComponent;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector;
import com.checkmarx.intellij.cxdevassist.ui.CxDevAssistWelcomeDialog;
import com.checkmarx.intellij.cxdevassist.utils.CxDevAssistConstants;
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * CxDevAssistSettingsComponent is a user interface component responsible for managing
 * and interacting with settings related to the Checkmarx Developer Assist application's integration
 * within the IntelliJ IDEA platform. It handles various authentication methods,
 * API configurations, and user preferences while ensuring their proper validation
 * and persistence.
 */
public class CxDevAssistSettingsComponent implements SettingsComponent {
    private static final Logger LOGGER = Utils.getLogger(CxDevAssistSettingsComponent.class);

    private static GlobalSettingsState globalSettingsState;
    private static GlobalSettingsSensitiveState globalSettingsSensitiveState;
    private final MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    private final Project project = ProjectManager.getInstance().getDefaultProject();

    @Getter
    private final JPanel mainPanel = new JPanel();

    @Getter
    private final JBPasswordField apiKeyField = new JBPasswordField();
    private final JBLabel apiKeyLabel = new JBLabel(getMessage(Resource.API_KEY));
    private final JButton logoutButton = new JButton(getMessage(Resource.LOG_OUT));

    private final JButton connectButton = new JButton(getMessage(Resource.DEVASSIST_PLUGIN_SETTINGS_SIGN_IN_BUTTON));
    private final JBLabel validateResult = new JBLabel();
    private CxLinkLabel assistLink;
    @Getter
    private final ExpandableTextField additionalParametersField = new ExpandableTextField();

    public CxDevAssistSettingsComponent() {
        initState();
        addValidateConnectionListener();
        setupFields();
        buildGUI();
        addLogoutListener();
    }

    @Override
    public boolean isModified() {
        initState();
        return !String.valueOf(apiKeyField.getPassword()).equals(globalSettingsSensitiveState.getApiKey());
    }

    @Override
    public void apply() {
        // Always persist auth state
        GlobalSettingsState state = getStateFromFields();

        state.setAuthenticated(globalSettingsState.isAuthenticated());
        state.setValidationInProgress(globalSettingsState.isValidationInProgress());
        state.setRefreshTokenExpiry(globalSettingsState.getRefreshTokenExpiry());
        state.setValidationExpiry(globalSettingsState.getValidationExpiry());
        state.setValidationMessage(globalSettingsState.getValidationMessage());
        state.setLastValidationSuccess(globalSettingsState.isLastValidationSuccess());
        globalSettingsState.apply(state);
        globalSettingsSensitiveState.apply(state, getSensitiveStateFromFields());
        messageBus.syncPublisher(SettingsListener.SETTINGS_APPLIED).settingsApplied();
    }

    @Override
    public void reset() {
        initState();
        boolean isValidating = globalSettingsState.isValidationInProgress();
        boolean useApiKey = globalSettingsState.isApiKeyEnabled();
        boolean isAuthValid = isValid();

        setInputFields();

        // Not authenticated, authentication in progress
        if (!isAuthValid && isValidating && !isValidateTimeExpired()) {
            setValidationResult();
            setFieldsEditable(false); // Lock UI while validating
            connectButton.setEnabled(false);
            logoutButton.setEnabled(false);
        } else if (!isAuthValid) { // Not authenticated (token expired, new authentication, logout)
            globalSettingsState.setValidationInProgress(false);
            setValidationResult();
            setSessionExpired();
        } else { // Authenticated
            apiKeyField.setEnabled(useApiKey);
            updateFieldLabels();
            setValidationResult();
            setFieldsEditable(false);
            logoutButton.setEnabled(true);
            logoutButton.requestFocusInWindow();
        }

        updateAssistLinkVisibility();

        SwingUtilities.invokeLater(() -> {
            if (useApiKey) {
                apiKeyField.requestFocusInWindow();
            }
        });
    }

    // Getting existing saved input details from the setting state.
    private void setInputFields() {
        additionalParametersField.setText(globalSettingsState.getAdditionalParameters());

        apiKeyField.setText(globalSettingsSensitiveState.getApiKey());
    }

    //Setting validation result to UI
    private void setValidationResult() {
        // Restore validation UI
        if (globalSettingsState.isValidationInProgress()) {
            setValidationResult(getMessage(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);
            validateResult.setVisible(true);
        } else {
            if (globalSettingsState.isLastValidationSuccess() && !StringUtils.isBlank(globalSettingsState.getValidationMessage())) { // success message
                setValidationResult(globalSettingsState.getValidationMessage(), JBColor.GREEN);
                validateResult.setVisible(true);
            } else if (!StringUtils.isBlank(globalSettingsState.getValidationMessage())) { // Error messages
                setValidationResult(globalSettingsState.getValidationMessage(), JBColor.RED);
                validateResult.setVisible(true);
            } else {
                validateResult.setVisible(false);
            }
        }
    }

    /**
     * Creates a GlobalSettingsState object from the current UI field values.
     * <p>
     * IMPORTANT: This method must preserve ALL existing state fields that are not directly
     * managed by this UI panel, including user preferences for realtime scanners.
     * Failure to copy these fields will result in them being reset to default values
     * when the state is applied, causing user preferences to be lost.
     */
    private GlobalSettingsState getStateFromFields() {
        GlobalSettingsState state = new GlobalSettingsState();
        // Fields directly managed by this UI panel
        state.setAdditionalParameters(additionalParametersField.getText().trim());
        state.setApiKeyEnabled(true);

        // Preserve all other state fields from the current settings
        if (globalSettingsState != null) {
            // Realtime scanner active states
            state.setAscaRealtime(globalSettingsState.isAscaRealtime());
            state.setOssRealtime(globalSettingsState.isOssRealtime());
            state.setSecretDetectionRealtime(globalSettingsState.isSecretDetectionRealtime());
            state.setContainersRealtime(globalSettingsState.isContainersRealtime());
            state.setIacRealtime(globalSettingsState.isIacRealtime());
            state.setContainersTool(globalSettingsState.getContainersTool());

            // MCP and dialog state
            state.setWelcomeShown(globalSettingsState.isWelcomeShown());
            state.setMcpEnabled(globalSettingsState.isMcpEnabled());
            state.setMcpStatusChecked(globalSettingsState.isMcpStatusChecked());

            // User preferences for realtime scanners - CRITICAL for preference preservation
            state.setUserPreferencesSet(globalSettingsState.getUserPreferencesSet());
            state.setUserPrefAscaRealtime(globalSettingsState.getUserPrefAscaRealtime());
            state.setUserPrefOssRealtime(globalSettingsState.getUserPrefOssRealtime());
            state.setUserPrefSecretDetectionRealtime(globalSettingsState.getUserPrefSecretDetectionRealtime());
            state.setUserPrefContainersRealtime(globalSettingsState.getUserPrefContainersRealtime());
            state.setUserPrefIacRealtime(globalSettingsState.getUserPrefIacRealtime());

            // License flags – must be preserved to control UI elements like Assist link
            state.setDevAssistLicenseEnabled(globalSettingsState.isDevAssistLicenseEnabled());
            state.setOneAssistLicenseEnabled(globalSettingsState.isOneAssistLicenseEnabled());
        }
        return state;
    }

    private GlobalSettingsSensitiveState getSensitiveStateFromFields() {
        GlobalSettingsSensitiveState state = new GlobalSettingsSensitiveState();
        char[] apiKey = apiKeyField.getPassword();
        state.setApiKey(apiKey != null ? String.valueOf(apiKey) : globalSettingsSensitiveState.getApiKey());
        state.setRefreshToken(globalSettingsSensitiveState.getRefreshToken());
        return state;
    }

    private void addValidateConnectionListener() {
        connectButton.addActionListener(event -> {
            connectButton.setEnabled(false);
            validateResult.setVisible(true);
            validateResult.requestFocusInWindow();
            setValidationResult(getMessage(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);
            setInvalidAuthState("");

            CompletableFuture.runAsync(() -> {
                try {
                    Authentication.validateConnection(getStateFromFields(), getSensitiveStateFromFields());
                    SwingUtilities.invokeLater(this::onAuthSuccessApiKey);
                    LOGGER.info(getMessage(Resource.DEVASSIST_PLUGIN_AUTH_SUCCESS_MSG));
                } catch (Exception e) {
                    handleConnectionFailure(e);
                }
            });
        });
    }

    private void onAuthSuccessApiKey() {
        // Set basic authentication success state
        setValidationResult(getMessage(Resource.DEVASSIST_PLUGIN_AUTH_SUCCESS_MSG), JBColor.GREEN);
        logoutButton.setEnabled(true);
        connectButton.setEnabled(false);
        setFieldsEditable(false);
        globalSettingsState.setAuthenticated(true);
        globalSettingsState.setLastValidationSuccess(true);
        globalSettingsState.setValidationMessage(getMessage(Resource.DEVASSIST_PLUGIN_AUTH_SUCCESS_MSG));
        // Reset session expired notification flag on successful login
        Utils.resetSessionExpiredNotificationFlag();
        fetchAndStoreLicenseStatus();
        SwingUtilities.invokeLater(this::updateAssistLinkVisibility);
        logoutButton.requestFocusInWindow();

        // Complete post-authentication setup
        completeAuthenticationSetup(String.valueOf(apiKeyField.getPassword()));
    }

    /**
     * Fetches tenant settings from the API and stores license status in the global state.
     * Clears license flags before fetching to ensure stale values are not used on API failure.
     */
    private void fetchAndStoreLicenseStatus() {
        // Clear stale license flags first (fail-safe: if API call fails, flags remain false)
        globalSettingsState.setDevAssistLicenseEnabled(false);
        globalSettingsState.setOneAssistLicenseEnabled(false);
        try {
            Map<String, String> tenantSettings = TenantSetting.getTenantSettingsMap(
                    getStateFromFields(), getSensitiveStateFromFields());
            boolean devAssistEnabled = Boolean.parseBoolean(
                    tenantSettings.getOrDefault(TenantSetting.KEY_DEV_ASSIST, "false"));
            boolean oneAssistEnabled = Boolean.parseBoolean(
                    tenantSettings.getOrDefault(TenantSetting.KEY_ONE_ASSIST, "false"));
            globalSettingsState.setDevAssistLicenseEnabled(devAssistEnabled);
            globalSettingsState.setOneAssistLicenseEnabled(oneAssistEnabled);
            LOGGER.info("License status: devAssist=" + devAssistEnabled + ", oneAssist=" + oneAssistEnabled);
        } catch (Exception e) {
            LOGGER.warn("Failed to check tenant license status", e);
        }
    }

    /**
     * Common post-authentication setup logic for both API key and OAuth authentication.
     * Checks MCP server status, configures realtime scanners, and shows welcome dialog.
     *
     * @param credential The credential to use for MCP installation (API key or refresh token)
     */
    private void completeAuthenticationSetup(String credential) {
        // Check MCP server status using current authentication credentials
        boolean mcpServerEnabled = false;
        try {
            mcpServerEnabled = TenantSetting.isAiMcpServerEnabled(
                    getStateFromFields(), getSensitiveStateFromFields());
        } catch (Exception ex) {
            LOGGER.warn("Failed MCP server check", ex);
        }

        // Determine if MCP status has actually changed to avoid resetting user preferences on simple re-authentication
        boolean previousMcpEnabled = globalSettingsState.isMcpEnabled();
        boolean mcpStatusPreviouslyChecked = globalSettingsState.isMcpStatusChecked();
        boolean mcpStatusChanged = mcpStatusPreviouslyChecked && (previousMcpEnabled != mcpServerEnabled);

        // Store MCP status and authentication state
        globalSettingsState.setMcpEnabled(mcpServerEnabled);
        globalSettingsState.setMcpStatusChecked(true);
        apply();

        // Configure realtime scanners based on MCP status - only modify settings when necessary to preserve user preferences during routine re-authentication
        if (!mcpStatusPreviouslyChecked) {
            // First time checking MCP status (new user or plugin upgrade scenario)
            if (mcpServerEnabled) {
                autoEnableAllRealtimeScanners(); // Enable scanners with preference detection
                installMcpAsync(credential);
            } else {
                disableAllRealtimeScanners(); // Disable scanners while preserving preferences
            }
            LOGGER.debug("[Auth] Initial MCP status setup completed for MCP enabled: " + mcpServerEnabled);
        } else if (mcpStatusChanged) {
            // MCP status has changed since last authentication - update scanner configuration
            if (mcpServerEnabled) {
                LOGGER.debug("[Auth] MCP re-enabled - restoring user preferences");
                autoEnableAllRealtimeScanners(); // Restore user preferences
                installMcpAsync(credential);
            } else {
                LOGGER.debug("[Auth] MCP disabled - preserving user preferences and disabling scanners");
                disableAllRealtimeScanners(); // Preserve preferences before disabling
            }
        } else {
            // MCP status unchanged - preserve existing scanner settings and user preferences
            if (mcpServerEnabled) {
                installMcpAsync(credential); // Ensure MCP config is up to date
                LOGGER.debug("[Auth] MCP unchanged (enabled) - user preferences preserved");
            } else {
                LOGGER.debug("[Auth] MCP unchanged (disabled) - user preferences preserved");
            }
        }

        showWelcomeDialog(mcpServerEnabled);
    }

    private void installMcpAsync(String credential) {
        CompletableFuture.supplyAsync(() -> {
            try {
                // Returns Boolean.TRUE if MCP modified, Boolean.FALSE if already up-to-date
                return McpSettingsInjector.installForCopilot(credential);
            } catch (Exception ex) {
                return ex;
            }
        }).thenAccept(result -> SwingUtilities.invokeLater(() -> {
            if (result instanceof Exception) {
                Utils.showNotification(
                        getMessage(Resource.MCP_NOTIFICATION_TITLE),
                        getMessage(Resource.MCP_INSTALL_ERROR),
                        NotificationType.ERROR,
                        project, false, ""
                );
                LOGGER.warn("MCP install error", (Exception) result);
            } else if (Boolean.TRUE.equals(result)) {
                Utils.showNotification(
                        getMessage(Resource.MCP_NOTIFICATION_TITLE),
                        getMessage(Resource.MCP_CONFIG_SAVED),
                        NotificationType.INFORMATION,
                        project, false, ""
                );
            } else if (Boolean.FALSE.equals(result)) {
                Utils.showNotification(
                        getMessage(Resource.MCP_NOTIFICATION_TITLE),
                        getMessage(Resource.MCP_CONFIG_UP_TO_DATE),
                        NotificationType.INFORMATION,
                        project, false, ""
                );
            }
        }));
    }

    private void showWelcomeDialog(boolean mcpEnabled) {
        try {
            CxDevAssistWelcomeDialog dlg = new CxDevAssistWelcomeDialog(project, mcpEnabled);
            dlg.show();
        } catch (Exception ex) {
            LOGGER.warn("Failed to show welcome dialog", ex);
        }
    }

    private void handleConnectionFailure(Exception e) {
        SwingUtilities.invokeLater(() -> {
            setValidationResult(getMessage(Resource.VALIDATE_ERROR), JBColor.RED);
            connectButton.setEnabled(true);
        });
        LOGGER.error("Connection failed", e);
    }


    private void setValidationResult(String message, JBColor color) {
        validateResult.setText(String.format("<html>%s</html>", message));
        validateResult.setForeground(color);
    }

    private void buildGUI() {
        // Remove default insets and reduce horizontal gaps so fields/buttons are shifted left
        mainPanel.setLayout(new MigLayout("insets 0, gapx 5", "[][grow]", ""));
        mainPanel.add(CxLinkLabel.buildDocLinkLabel(CxDevAssistConstants.DEVASSIST_HELP_LINK, Resource.DEVASSIST_PLUGIN_SETTINGS_HELP_LINK_LABEL),
                "span, growx, wrap, gapbottom 10");
        addSectionHeader(Resource.DEVASSIST_PLUGIN_SETTINGS_AUTH_SECTION, false);
        mainPanel.add(apiKeyLabel, "aligny top");
        // Shift apiKeyField left and define a consistent left gap to align with buttons
        mainPanel.add(apiKeyField, "growx, wrap, aligny top, gapleft 0");

        setFieldsEditable(true);
        updateFieldLabels();
        updateConnectButtonState();
        SwingUtilities.invokeLater(apiKeyField::requestFocusInWindow);

        // Place both buttons in the second column (same as apiKeyField) and eliminate gap between them
        mainPanel.add(connectButton, "skip 1, gaptop 10, gapleft 0, gapright 0, split 2, gapx 0");
        mainPanel.add(logoutButton, "gaptop 10, gapleft 10, wrap");
        mainPanel.add(validateResult, "span 2, gaptop 5, wrap");

        addSectionHeader(Resource.SCAN_SECTION, false);
        addField(Resource.ADDITIONAL_PARAMETERS, additionalParametersField, false, false);
        mainPanel.add(new JBLabel());
        mainPanel.add(CxLinkLabel.buildDocLinkLabel(Constants.ADDITIONAL_PARAMETERS_HELP, Resource.HELP_CLI),
                "gapleft 5,gapbottom 10, wrap");

        // === CxOne Assist link section ===
        assistLink = new CxLinkLabel("Go to " + getMessage(Resource.DEVASSIST_PLUGIN_SETTINGS_CHILD_TITLE),
                e -> {
                    DataContext context = DataManager.getInstance().getDataContext(mainPanel);
                    Settings settings = context.getData(Settings.KEY);
                    if (settings == null) return;

                    Configurable configurable = settings.find(CxDevAssistConstants.PLUGIN_CHILD_REALTIME_SETTINGS_ID);
                    if (configurable instanceof RealtimeScannersSettingsConfigurable) {
                        settings.select(configurable);
                    } else {
                        // Configurable not in tree (Settings opened before authentication)
                        // Close and reopen Settings to rebuild the tree
                        Window dialog = SwingUtilities.getWindowAncestor(mainPanel);
                        if (dialog != null) {
                            dialog.dispose();
                        }
                        ApplicationManager.getApplication().invokeLater(() -> ShowSettingsUtil.getInstance()
                                .showSettingsDialog(project, RealtimeScannersSettingsConfigurable.class));
                    }
                }
        );
        assistLink.setVisible(shouldShowAssistLink());
        mainPanel.add(assistLink, "wrap, gapleft 5, gaptop 10");
    }

    private void setupFields() {
        apiKeyField.setName(Constants.FIELD_NAME_API_KEY);
        apiKeyField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                updateConnectButtonState();
            }
        });

        setFieldsEditable(true);
        updateFieldLabels();
        updateConnectButtonState();
        SwingUtilities.invokeLater(apiKeyField::requestFocusInWindow);

        logoutButton.setName("logoutButton");
        additionalParametersField.setName(Constants.FIELD_NAME_ADDITIONAL_PARAMETERS);

        // Remove default margins to reduce spacing around buttons
        connectButton.setMargin(new Insets(0, 0, 0, 0));
        logoutButton.setMargin(new Insets(0, 5, 0, 0));

        apiKeyField.setEnabled(false);
        logoutButton.setEnabled(false);
    }

    private void updateConnectButtonState() {
        boolean enabled = !String.valueOf(apiKeyField.getPassword()).trim().isEmpty();
        if (globalSettingsState.isAuthenticated() || globalSettingsState.isValidationInProgress()) {
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

                // Ensure only the Checkmarx MCP entry is removed and log any issues.
                CompletableFuture.runAsync(() -> {
                    try {
                        boolean removed = McpSettingsInjector.uninstallFromCopilot();
                        if (!removed) {
                            LOGGER.debug("Logout completed, but no MCP entry was present to remove.");
                        }
                    } catch (Exception ex) {
                        LOGGER.warn("Failed to remove Checkmarx MCP entry on logout.", ex);
                    }
                });
            }
            // else: Do nothing (user clicked Cancel)
        });
    }

    // Setting state on log out.
    private void setLogoutState() {
        validateResult.setText(getMessage(Resource.LOGOUT_SUCCESS));
        validateResult.setForeground(JBColor.GREEN);
        validateResult.setVisible(true);
        connectButton.setEnabled(true);
        logoutButton.setEnabled(false);
        setFieldsEditable(true);
        updateConnectButtonState();
        globalSettingsState.setAuthenticated(false); // Update authentication state
        // Clear license flags on logout to ensure fresh check on next login
        globalSettingsState.setDevAssistLicenseEnabled(false);
        globalSettingsState.setOneAssistLicenseEnabled(false);
        updateAssistLinkVisibility();
        // Don't clear MCP status on logout - keep it for next login
        globalSettingsState.setValidationMessage(getMessage(Resource.LOGOUT_SUCCESS));
        globalSettingsState.setLastValidationSuccess(true);
        // Reset session expired notification flag to prepare for next session
        Utils.resetSessionExpiredNotificationFlag();
        if (!globalSettingsState.isApiKeyEnabled()) { // if oauth login is enabled
            globalSettingsSensitiveState.deleteRefreshToken();
        }
        apply();
        updateConnectButtonState(); // Ensure the Connect button state is updated

        // Close the Settings dialog to force rebuild of the settings tree
        // This ensures the CxOne Assist sub-panel disappears immediately after logout
        closeSettingsDialog();
    }

    /**
     * Closes the Settings dialog if currently open.
     * This forces IntelliJ to rebuild the settings tree on next open,
     * ensuring conditional configurables (like CxOne Assist) are re-evaluated.
     */
    private void closeSettingsDialog() {
        SwingUtilities.invokeLater(() -> {
            Window dialog = SwingUtilities.getWindowAncestor(mainPanel);
            if (dialog != null) {
                dialog.dispose();
            }
        });
    }

    // Setting state after session expired.
    private void setSessionExpired() {
        connectButton.setEnabled(true);
        logoutButton.setEnabled(false);
        setFieldsEditable(true);

        // Clear authentication, MCP status, and license flags
        globalSettingsState.setAuthenticated(false);
        globalSettingsState.setMcpEnabled(false);
        globalSettingsState.setMcpStatusChecked(false);
        globalSettingsState.setDevAssistLicenseEnabled(false);
        globalSettingsState.setOneAssistLicenseEnabled(false);
        updateAssistLinkVisibility();
        if (!globalSettingsState.isApiKeyEnabled()) { // if oauth login is enabled
            globalSettingsSensitiveState.deleteRefreshToken();
        }
        apply();
        updateConnectButtonState(); // Update button state after all changes
    }


    private boolean shouldShowAssistLink() {
        return globalSettingsState.isAuthenticated()
                && (globalSettingsState.isOneAssistLicenseEnabled() || globalSettingsState.isDevAssistLicenseEnabled());
    }

    private void updateAssistLinkVisibility() {
        if (assistLink == null) {
            return;
        }
        boolean visible = shouldShowAssistLink();
        assistLink.setVisible(visible);
        assistLink.setEnabled(visible);
        mainPanel.revalidate();
        mainPanel.repaint();
    }

    private void addSectionHeader(Resource resource, boolean required) {
        validatePanel();
        String labelText = String.format(Constants.FIELD_FORMAT,
                getMessage(resource),
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
                getMessage(resource),
                required ? Constants.REQUIRED_MARK : "");
        mainPanel.add(new JBLabel(label), gapAfter ? Constants.FIELD_GAP_BOTTOM : "");
        mainPanel.add(field, constraints);
    }

    private void updateFieldLabels() {
        apiKeyLabel.setText(String.format(Constants.FIELD_FORMAT, "API Key", Constants.REQUIRED_MARK));
    }

    private void validatePanel() {
        if (!(mainPanel.getLayout() instanceof MigLayout)) {
            throw new IllegalArgumentException("panel must be using MigLayout");
        }
    }

    /**
     * Checking if user has valid authentication state and credentials
     *
     * @return true, if authentication is valid otherwise false
     */
    public boolean isValid() {
        if (globalSettingsState.isAuthenticated() && StringUtil.isEmpty(globalSettingsSensitiveState.getApiKey())
                && StringUtil.isEmpty(globalSettingsSensitiveState.getRefreshToken())) {
            //This condition handles if the user is authenticated but no sensitive data is present (due to explicitly clearing it form storage)
            setInvalidAuthState("");
            return false;
        } else if (globalSettingsState.isAuthenticated() && !globalSettingsState.isApiKeyEnabled() &&
                globalSettingsSensitiveState.isTokenExpired(globalSettingsState.getRefreshTokenExpiry())) {
            setInvalidAuthState(getMessage(Resource.ERROR_SESSION_EXPIRED));
            return false;
        }
        return globalSettingsState.isAuthenticated() && globalSettingsSensitiveState.isValid(globalSettingsState);
    }

    /**
     * Setting invalid authentication state
     *
     * @param message - message to display on UI
     */
    private void setInvalidAuthState(String message) {
        globalSettingsState.setValidationMessage(message);
        globalSettingsState.setLastValidationSuccess(false);
        globalSettingsState.setAuthenticated(false);
    }

    private void setFieldsEditable(boolean editable) {
        apiKeyField.setEnabled(editable);
    }

    /**
     * Display notification on notification area on successful logout
     */
    private void notifyLogout() {
        ApplicationManager.getApplication().invokeLater(() ->
                Utils.showNotification(getMessage(Resource.LOGOUT_SUCCESS_TITLE),
                        getMessage(Resource.LOGOUT_SUCCESS),
                        NotificationType.INFORMATION,
                        project, false, "")
        );
    }

    /**
     * Checking if authentication validation time expired
     *
     * @return true, if validation time is exceeded than current time otherwise false.
     */
    private boolean isValidateTimeExpired() {
        if (!StringUtils.isBlank(globalSettingsState.getValidationExpiry())) {
            return LocalDateTime.parse(globalSettingsState.getValidationExpiry()).isBefore(LocalDateTime.now());
        }
        return false;
    }

    /**
     * Configures realtime scanners when MCP is enabled, with intelligent preference handling.
     * For existing users: restores their individual scanner preferences
     * For new users: enables all scanners as defaults and saves as initial preferences
     */
    private void autoEnableAllRealtimeScanners() {
        GlobalSettingsState st = GlobalSettingsState.getInstance();
        boolean changed = false;

        // Priority 1: Restore existing user preferences if available
        if (st.getUserPreferencesSet()) {
            changed = st.applyUserPreferencesToRealtimeSettings();
            if (changed) {
                LOGGER.debug("[Auth] Restored user preferences for realtime scanners");
                apply();
                return;
            } else {
                LOGGER.debug("[Auth] User preferences already applied to realtime scanners");
                return;
            }
        }

        // Priority 2: For new users, enable all scanners as sensible defaults
        if (!st.isAscaRealtime()) {
            st.setAscaRealtime(true);
            changed = true;
        }
        if (!st.isOssRealtime()) {
            st.setOssRealtime(true);
            changed = true;
        }
        if (!st.isSecretDetectionRealtime()) {
            st.setSecretDetectionRealtime(true);
            changed = true;
        }
        if (!st.isContainersRealtime()) {
            st.setContainersRealtime(true);
            changed = true;
        }
        if (!st.isIacRealtime()) {
            st.setIacRealtime(true);
            changed = true;
        }

        if (changed) {
            // Save the "all enabled" defaults as initial user preferences for future preservation
            st.saveCurrentSettingsAsUserPreferences();
            LOGGER.debug("[Auth] Enabled all scanners for new user and saved as initial preferences");
            apply();
        } else {
            LOGGER.debug("[Auth] All realtime scanners already enabled");
        }
    }

    /**
     * Disables all realtime scanners when MCP is not available, while preserving user preferences.
     * The user's individual scanner choices are saved before disabling, ensuring they can be
     * restored when MCP becomes available again.
     */
    private void disableAllRealtimeScanners() {
        GlobalSettingsState st = GlobalSettingsState.getInstance();

        // Preserve current scanner settings as user preferences before disabling
        if (!st.getUserPreferencesSet()) {
            st.saveCurrentSettingsAsUserPreferences();
            LOGGER.debug("[Auth] Saved current scanner settings as user preferences before disabling");
        }

        // Disable all scanners for security (MCP not available)
        boolean changed = false;
        if (st.isAscaRealtime()) {
            st.setAscaRealtime(false);
            changed = true;
        }
        if (st.isOssRealtime()) {
            st.setOssRealtime(false);
            changed = true;
        }
        if (st.isSecretDetectionRealtime()) {
            st.setSecretDetectionRealtime(false);
            changed = true;
        }
        if (st.isContainersRealtime()) {
            st.setContainersRealtime(false);
            changed = true;
        }
        if (st.isIacRealtime()) {
            st.setIacRealtime(false);
            changed = true;
        }

        if (changed) {
            LOGGER.debug("[Auth] Disabled all realtime scanners while preserving user preferences");
            apply();
        } else {
            LOGGER.debug("[Auth] Realtime scanners already disabled");
        }
    }

    /**
     * Load state
     */
    private void initState() {
        if (globalSettingsState == null) {
            globalSettingsState = GlobalSettingsState.getInstance();
        }
        if (globalSettingsSensitiveState == null) {
            globalSettingsSensitiveState = GlobalSettingsSensitiveState.getInstance();
        }
    }

    /**
     * Get message from the resource
     *
     * @param resource - Resource enum
     * @return message from the resource
     */
    String getMessage(Resource resource) {
        return Bundle.message(resource);
    }
}