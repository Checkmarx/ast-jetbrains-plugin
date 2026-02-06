package com.checkmarx.intellij.ignite.settings;

import com.checkmarx.intellij.common.auth.AuthService;
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
import com.checkmarx.intellij.common.utils.InputValidator;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector;
import com.checkmarx.intellij.ignite.ui.IgniteWelcomeDialog;
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
import java.awt.event.ItemEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * IgniteSettingsComponent is a user interface component responsible for managing
 * and interacting with settings related to the Ignite application's integration
 * within the IntelliJ IDEA platform. It handles various authentication methods,
 * API configurations, and user preferences while ensuring their proper validation
 * and persistence.
 */
public class IgniteSettingsComponent implements SettingsComponent {
    private static final Logger LOGGER = Utils.getLogger(IgniteSettingsComponent.class);

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
    private CxLinkLabel assistLink;


    public IgniteSettingsComponent() {
        if (SETTINGS_STATE == null) {
            SETTINGS_STATE = GlobalSettingsState.getInstance();
        }
        if (SENSITIVE_SETTINGS_STATE == null) {
            SENSITIVE_SETTINGS_STATE = GlobalSettingsSensitiveState.getInstance();
        }
        addValidateConnectionListener();

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


        if (apiKeyRadio.isSelected() != SETTINGS_STATE.isApiKeyEnabled()) {
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
        state.setValidationMessage(SETTINGS_STATE.getValidationMessage());
        state.setLastValidationSuccess(SETTINGS_STATE.isLastValidationSuccess());

        String baseUrl = baseUrlField.getText();
        String tenant = tenantField.getText();

        state.setBaseUrl(!StringUtils.isBlank(baseUrl) ? baseUrl.trim() : SETTINGS_STATE.getBaseUrl());
        state.setTenant(!StringUtils.isBlank(tenant) ? tenant.trim() : SETTINGS_STATE.getTenant());

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
        boolean useApiKey = SETTINGS_STATE.isApiKeyEnabled();
        boolean isAuthValid = isValid();

        setInputFields();

        // Not authenticated, authentication in progress
        if (!isAuthValid && isValidating && !isValidateTimeExpired()) {
            setValidationResult();
            setFieldsEditable(false); // Lock UI while validating
            connectButton.setEnabled(false);
            logoutButton.setEnabled(false);
        } else if (!isAuthValid) { // Not authenticated (token expired, new authentication, logout)
            SETTINGS_STATE.setValidationInProgress(false);
            setValidationResult();
            setSessionExpired();
        } else { // Authenticated
            apiKeyRadio.setSelected(useApiKey);
            oauthRadio.setSelected(!useApiKey);
            baseUrlField.setEnabled(!useApiKey);
            tenantField.setEnabled(!useApiKey);
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
            } else {
                baseUrlField.requestFocusInWindow();
            }
        });
    }

    // Getting existing saved input details from the setting state.
    private void setInputFields() {
        additionalParametersField.setText(SETTINGS_STATE.getAdditionalParameters());

        apiKeyField.setText(SENSITIVE_SETTINGS_STATE.getApiKey());
        baseUrlField.setText(SETTINGS_STATE.getBaseUrl());
        tenantField.setText(SETTINGS_STATE.getTenant());
    }

    //Setting validation result to UI
    private void setValidationResult() {
        // Restore validation UI
        if (SETTINGS_STATE.isValidationInProgress()) {
            setValidationResult(Bundle.message(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);
            validateResult.setVisible(true);
        } else {
            if (SETTINGS_STATE.isLastValidationSuccess() && !StringUtils.isBlank(SETTINGS_STATE.getValidationMessage())) { // success message
                setValidationResult(SETTINGS_STATE.getValidationMessage(), JBColor.GREEN);
                validateResult.setVisible(true);
            } else if (!StringUtils.isBlank(SETTINGS_STATE.getValidationMessage())) { // Error messages
                setValidationResult(SETTINGS_STATE.getValidationMessage(), JBColor.RED);
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

        state.setApiKeyEnabled(apiKeyRadio.isSelected());

        // Preserve all other state fields from the current settings
        if (SETTINGS_STATE != null) {
            // Realtime scanner active states
            state.setAscaRealtime(SETTINGS_STATE.isAscaRealtime());
            state.setOssRealtime(SETTINGS_STATE.isOssRealtime());
            state.setSecretDetectionRealtime(SETTINGS_STATE.isSecretDetectionRealtime());
            state.setContainersRealtime(SETTINGS_STATE.isContainersRealtime());
            state.setIacRealtime(SETTINGS_STATE.isIacRealtime());
            state.setContainersTool(SETTINGS_STATE.getContainersTool());

            // MCP and dialog state
            state.setWelcomeShown(SETTINGS_STATE.isWelcomeShown());
            state.setMcpEnabled(SETTINGS_STATE.isMcpEnabled());
            state.setMcpStatusChecked(SETTINGS_STATE.isMcpStatusChecked());

            // User preferences for realtime scanners - CRITICAL for preference preservation
            state.setUserPreferencesSet(SETTINGS_STATE.getUserPreferencesSet());
            state.setUserPrefAscaRealtime(SETTINGS_STATE.getUserPrefAscaRealtime());
            state.setUserPrefOssRealtime(SETTINGS_STATE.getUserPrefOssRealtime());
            state.setUserPrefSecretDetectionRealtime(SETTINGS_STATE.getUserPrefSecretDetectionRealtime());
            state.setUserPrefContainersRealtime(SETTINGS_STATE.getUserPrefContainersRealtime());
            state.setUserPrefIacRealtime(SETTINGS_STATE.getUserPrefIacRealtime());

            // License flags – must be preserved to control UI elements like Assist link
            state.setDevAssistLicenseEnabled(SETTINGS_STATE.isDevAssistLicenseEnabled());
            state.setOneAssistLicenseEnabled(SETTINGS_STATE.isOneAssistLicenseEnabled());
        }
        return state;
    }

    private GlobalSettingsSensitiveState getSensitiveStateFromFields() {
        GlobalSettingsSensitiveState state = new GlobalSettingsSensitiveState();
        char[] apiKey = apiKeyField.getPassword();
        state.setApiKey(apiKey != null ? String.valueOf(apiKey) : SENSITIVE_SETTINGS_STATE.getApiKey());
        state.setRefreshToken(SENSITIVE_SETTINGS_STATE.getRefreshToken());
        return state;
    }

    private void addValidateConnectionListener() {
        connectButton.addActionListener(event -> {
            connectButton.setEnabled(false);
            validateResult.setVisible(true);
            validateResult.requestFocusInWindow();
            setValidationResult(Bundle.message(Resource.VALIDATE_IN_PROGRESS), JBColor.GREEN);
            setInvalidAuthState("");

            if (apiKeyRadio.isSelected()) {
                CompletableFuture.runAsync(() -> {
                    try {
                        Authentication.validateConnection(getStateFromFields(), getSensitiveStateFromFields());
                        SwingUtilities.invokeLater(this::onAuthSuccessApiKey);
                        LOGGER.info(Bundle.message(Resource.VALIDATE_SUCCESS));
                    } catch (Exception e) {
                        handleConnectionFailure(e);
                    }
                });
            } else {
                proceedOAuthAuthentication();
            }
        });
    }

    private void onAuthSuccessApiKey() {
        // Set basic authentication success state
        setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
        logoutButton.setEnabled(true);
        connectButton.setEnabled(false);
        setFieldsEditable(false);
        SETTINGS_STATE.setAuthenticated(true);
        SETTINGS_STATE.setLastValidationSuccess(true);
        SETTINGS_STATE.setValidationMessage(Bundle.message(Resource.VALIDATE_SUCCESS));
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
        SETTINGS_STATE.setDevAssistLicenseEnabled(false);
        SETTINGS_STATE.setOneAssistLicenseEnabled(false);
        try {
            Map<String, String> tenantSettings = TenantSetting.getTenantSettingsMap(
                    getStateFromFields(), getSensitiveStateFromFields());
            boolean devAssistEnabled = Boolean.parseBoolean(
                    tenantSettings.getOrDefault(TenantSetting.KEY_DEV_ASSIST, "false"));
            boolean oneAssistEnabled = Boolean.parseBoolean(
                    tenantSettings.getOrDefault(TenantSetting.KEY_ONE_ASSIST, "false"));
            SETTINGS_STATE.setDevAssistLicenseEnabled(devAssistEnabled);
            SETTINGS_STATE.setOneAssistLicenseEnabled(oneAssistEnabled);
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
        boolean previousMcpEnabled = SETTINGS_STATE.isMcpEnabled();
        boolean mcpStatusPreviouslyChecked = SETTINGS_STATE.isMcpStatusChecked();
        boolean mcpStatusChanged = mcpStatusPreviouslyChecked && (previousMcpEnabled != mcpServerEnabled);

        // Store MCP status and authentication state
        SETTINGS_STATE.setMcpEnabled(mcpServerEnabled);
        SETTINGS_STATE.setMcpStatusChecked(true);
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
                        Bundle.message(Resource.MCP_NOTIFICATION_TITLE),
                        Bundle.message(Resource.MCP_INSTALL_ERROR),
                        NotificationType.ERROR,
                        project, false, ""
                );
                LOGGER.warn("MCP install error", (Exception) result);
            } else if (Boolean.TRUE.equals(result)) {
                Utils.showNotification(
                        Bundle.message(Resource.MCP_NOTIFICATION_TITLE),
                        Bundle.message(Resource.MCP_CONFIG_SAVED),
                        NotificationType.INFORMATION,
                        project, false, ""
                );
            } else if (Boolean.FALSE.equals(result)) {
                Utils.showNotification(
                        Bundle.message(Resource.MCP_NOTIFICATION_TITLE),
                        Bundle.message(Resource.MCP_CONFIG_UP_TO_DATE),
                        NotificationType.INFORMATION,
                        project, false, ""
                );
            }
        }));
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
        InputValidator.validateConnection(baseUrl, tenant).thenAccept(result -> {
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

                        new AuthService(project).authenticate(baseUrl, tenant, authResult -> {
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
            setValidationResult(Bundle.message(Resource.VALIDATE_SUCCESS), JBColor.GREEN);
            validateResult.setVisible(true);
            logoutButton.setEnabled(true);
            connectButton.setEnabled(false);
            setFieldsEditable(false);
            SETTINGS_STATE.setAuthenticated(true);
            SETTINGS_STATE.setValidationInProgress(false);
            SETTINGS_STATE.setValidationExpiry(null);
            SETTINGS_STATE.setLastValidationSuccess(true);
            SETTINGS_STATE.setValidationMessage(Bundle.message(Resource.VALIDATE_SUCCESS));
            SENSITIVE_SETTINGS_STATE.setRefreshToken(refreshTokenDetails.get(Constants.AuthConstants.REFRESH_TOKEN).toString());
            SETTINGS_STATE.setRefreshTokenExpiry(refreshTokenDetails.get(Constants.AuthConstants.REFRESH_TOKEN_EXPIRY).toString());
            // Reset session expired notification flag on successful login
            Utils.resetSessionExpiredNotificationFlag();
            notifyAuthSuccess();
            fetchAndStoreLicenseStatus();
            updateAssistLinkVisibility();
            // Complete post-authentication setup
            completeAuthenticationSetup(SENSITIVE_SETTINGS_STATE.getRefreshToken());
        });
    }

    /**
     * Handle post-authentication failure state
     */
    private void handleOAuthFailure(String error) {
        SwingUtilities.invokeLater(() -> {
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

    private void showWelcomeDialog(boolean mcpEnabled) {
        try {
            IgniteWelcomeDialog dlg = new IgniteWelcomeDialog(project, mcpEnabled);
            dlg.show();
        } catch (Exception ex) {
            LOGGER.warn("Failed to show welcome dialog", ex);
        }
    }

    private void handleConnectionFailure(Exception e) {
        SwingUtilities.invokeLater(() -> {
            setValidationResult(Bundle.message(Resource.VALIDATE_ERROR), JBColor.RED);
            connectButton.setEnabled(true);
        });
        LOGGER.error("Connection failed", e);
    }


    private void setValidationResult(String message, JBColor color) {
        validateResult.setText(String.format("<html>%s</html>", message));
        validateResult.setForeground(color);
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


        // === CxOne Assist link section ===
        assistLink = new CxLinkLabel(
                Bundle.message(Resource.GO_TO_CXONE_ASSIST_LINK),
                e -> {
                    DataContext context = DataManager.getInstance().getDataContext(mainPanel);
                    Settings settings = context.getData(Settings.KEY);
                    if (settings == null) return;

                    Configurable configurable = settings.find("settings.ast.assist");
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


        // Set initial field states
        baseUrlField.setEnabled(true);
        tenantField.setEnabled(true);
        apiKeyField.setEnabled(false);
        logoutButton.setEnabled(false);
        baseUrlLabel.setText(String.format(Constants.FIELD_FORMAT, "Checkmarx One Base URL", Constants.REQUIRED_MARK));
        tenantLabel.setText(String.format(Constants.FIELD_FORMAT, "Tenant Name", Constants.REQUIRED_MARK));

        boolean useApiKey = SETTINGS_STATE.isApiKeyEnabled();
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

        if (!InputValidator.isValidUrl(rawInput)) {
            setValidationResult("Invalid URL format", JBColor.RED);
            connectButton.setEnabled(false);
            return false;
        }

        setValidationResult("", JBColor.GREEN);
        return true;
    }

    private void updateConnectButtonState() {
        boolean enabled = false;

        if (oauthRadio.isSelected()) {
            boolean isBaseUrlValid = InputValidator.isValidUrl(baseUrlField.getText().trim());
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
        oauthRadio.setSelected(true);
        validateResult.setText(Bundle.message(Resource.LOGOUT_SUCCESS));
        validateResult.setForeground(JBColor.GREEN);
        validateResult.setVisible(true);
        connectButton.setEnabled(true);
        logoutButton.setEnabled(false);
        setFieldsEditable(true);
        updateConnectButtonState();
        SETTINGS_STATE.setAuthenticated(false); // Update authentication state
        // Clear license flags on logout to ensure fresh check on next login
        SETTINGS_STATE.setDevAssistLicenseEnabled(false);
        SETTINGS_STATE.setOneAssistLicenseEnabled(false);
        updateAssistLinkVisibility();
        // Don't clear MCP status on logout - keep it for next login
        SETTINGS_STATE.setValidationMessage(Bundle.message(Resource.LOGOUT_SUCCESS));
        SETTINGS_STATE.setLastValidationSuccess(true);
        // Reset session expired notification flag to prepare for next session
        Utils.resetSessionExpiredNotificationFlag();
        if (!SETTINGS_STATE.isApiKeyEnabled()) { // if oauth login is enabled
            SENSITIVE_SETTINGS_STATE.deleteRefreshToken();
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
        oauthRadio.setSelected(true);
        connectButton.setEnabled(true);
        logoutButton.setEnabled(false);
        setFieldsEditable(true);

        // Clear authentication, MCP status, and license flags
        SETTINGS_STATE.setAuthenticated(false);
        SETTINGS_STATE.setMcpEnabled(false);
        SETTINGS_STATE.setMcpStatusChecked(false);
        SETTINGS_STATE.setDevAssistLicenseEnabled(false);
        SETTINGS_STATE.setOneAssistLicenseEnabled(false);
        updateAssistLinkVisibility();
        if (!SETTINGS_STATE.isApiKeyEnabled()) { // if oauth login is enabled
            SENSITIVE_SETTINGS_STATE.deleteRefreshToken();
        }
        apply();
        updateConnectButtonState(); // Update button state after all changes
    }


    private boolean shouldShowAssistLink() {
        return SETTINGS_STATE.isAuthenticated()
                && (SETTINGS_STATE.isOneAssistLicenseEnabled() || SETTINGS_STATE.isDevAssistLicenseEnabled());
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

    /**
     * Checking if user has valid authentication state and credentials
     *
     * @return true, if authentication is valid otherwise false
     */
    public boolean isValid() {
        if (SETTINGS_STATE.isAuthenticated() && StringUtil.isEmpty(SENSITIVE_SETTINGS_STATE.getApiKey())
                && StringUtil.isEmpty(SENSITIVE_SETTINGS_STATE.getRefreshToken())) {
            //This condition handles if the user is authenticated but no sensitive data is present (due to explicitly clearing it form storage)
            setInvalidAuthState("");
            return false;
        } else if (SETTINGS_STATE.isAuthenticated() && !SETTINGS_STATE.isApiKeyEnabled() &&
                SENSITIVE_SETTINGS_STATE.isTokenExpired(SETTINGS_STATE.getRefreshTokenExpiry())) {
            setInvalidAuthState(Bundle.message(Resource.ERROR_SESSION_EXPIRED));
            return false;
        }
        return SETTINGS_STATE.isAuthenticated() && SENSITIVE_SETTINGS_STATE.isValid(SETTINGS_STATE);
    }

    /**
     * Setting invalid authentication state
     *
     * @param message - message to display on UI
     */
    private void setInvalidAuthState(String message) {
        SETTINGS_STATE.setValidationMessage(message);
        SETTINGS_STATE.setLastValidationSuccess(false);
        SETTINGS_STATE.setAuthenticated(false);
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
                        project, false, "")
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
                        project, false, "")
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
                        project, false, "")
        );
    }

    /**
     * Getting login validation timeout datetime
     *
     * @return timestamp
     */
    private String getValidationExpiry() {
        long timeoutSeconds = Constants.AuthConstants.TIME_OUT_SECONDS + 5L;
        return Utils.convertToLocalDateTime(timeoutSeconds, ZoneId.systemDefault()).toString();
    }


    /**
     * Checking if authentication validation time expired
     *
     * @return true, if validation time is exceeded than current time otherwise false.
     */
    private boolean isValidateTimeExpired() {
        if (!StringUtils.isBlank(SETTINGS_STATE.getValidationExpiry())) {
            return LocalDateTime.parse(SETTINGS_STATE.getValidationExpiry()).isBefore(LocalDateTime.now());
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
}