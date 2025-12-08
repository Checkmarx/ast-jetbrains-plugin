package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.settings.SettingsComponent;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService;
import com.checkmarx.intellij.devassist.configuration.mcp.McpSettingsInjector;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.JBColor;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.fileEditor.FileEditorManager;

/**
 * UI component shown under Tools > Checkmarx One > CxOne Assist.
 * Currently shows OSS realtime scanner toggle and MCP configuration installation.
 * Other realtime scanners and container management tools are temporarily hidden and will be restored in a future release.
 * MCP status is shown inline in the UI.
 */
public class CxOneAssistComponent implements SettingsComponent, Disposable {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistComponent.class);

    private final JPanel mainPanel = new JPanel(new MigLayout("", "[][grow]"));

    private final JBLabel ossTitle = new JBLabel(formatTitle(Bundle.message(Resource.OSS_REALTIME_TITLE)));
    private final JBCheckBox ossCheckbox = new JBCheckBox(Bundle.message(Resource.OSS_REALTIME_CHECKBOX));
    private final JBLabel assistMessageLabel = new JBLabel();

    // TEMPORARILY HIDDEN FIELDS - Will be restored in future release
    private final JBLabel secretsTitle = new JBLabel(formatTitle(Bundle.message(Resource.SECRETS_REALTIME_TITLE)));
    private final JBCheckBox secretsCheckbox = new JBCheckBox(Bundle.message(Resource.SECRETS_REALTIME_CHECKBOX));

    @SuppressWarnings("unused")
    private final JBLabel containersTitle = new JBLabel(formatTitle(Bundle.message(Resource.CONTAINERS_REALTIME_TITLE)));
    @SuppressWarnings("unused")
    private final JBCheckBox containersCheckbox = new JBCheckBox(Bundle.message(Resource.CONTAINERS_REALTIME_CHECKBOX));

    @SuppressWarnings("unused")
    private final JBLabel iacTitle = new JBLabel(formatTitle(Bundle.message(Resource.IAC_REALTIME_TITLE)));
    @SuppressWarnings("unused")
    private final JBCheckBox iacCheckbox = new JBCheckBox(Bundle.message(Resource.IAC_REALTIME_CHECKBOX));

    @SuppressWarnings("unused")
    private final ComboBox<String> containersToolCombo = new ComboBox<>(new String[]{"docker", "podman"});

    private GlobalSettingsState state;
    private final MessageBusConnection connection;

    private final JBLabel mcpStatusLabel = new JBLabel();
    private CxLinkLabel installMcpLink;
    private boolean mcpInstallInProgress;
    private Timer mcpClearTimer;

    public CxOneAssistComponent() {
        buildUI();
        reset();

        connection = ApplicationManager.getApplication().getMessageBus().connect();
        connection.subscribe(SettingsListener.SETTINGS_APPLIED, new SettingsListener() {
            @Override
            public void settingsApplied() {
                SwingUtilities.invokeLater(() -> {
                    LOGGER.debug("[CxOneAssist] Detected settings change, refreshing checkboxes.");
                    reset();
                });
            }
        });
    }

    @Override
    public void dispose() {
        if (connection != null) {
            try {
                connection.dispose();
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    private void buildUI() {
        // Status message label - shown at the top in red when authentication/MCP issues exist
        assistMessageLabel.setForeground(JBColor.RED);
        assistMessageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        assistMessageLabel.setVisible(false);
        mainPanel.add(assistMessageLabel, "hidemode 3, growx, alignx left, wrap, gapbottom 5");

        // OSS Realtime
        mainPanel.add(ossTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(ossCheckbox, "wrap, gapbottom 10, gapleft 15");

        // Secret Detection
        mainPanel.add(secretsTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(secretsCheckbox, "wrap, gapbottom 10, gapleft 15");


         mainPanel.add(containersTitle, "split 2, span");
         mainPanel.add(new JSeparator(), "growx, wrap");
         mainPanel.add(containersCheckbox, "wrap, gapbottom 10, gapleft 15");

        // TEMPORARILY HIDDEN: IaC Realtime - Will be restored in future release
        // mainPanel.add(iacTitle, "split 2, span");
        // mainPanel.add(new JSeparator(), "growx, wrap");
        // mainPanel.add(iacCheckbox, "wrap, gapbottom 10, gapleft 15");

        // TEMPORARILY HIDDEN: Containers management tool dropdown - Will be restored in future release
        // JBLabel containersLabel = new JBLabel(formatTitle(Bundle.message(Resource.IAC_REALTIME_SCANNER_PREFIX)));
        // mainPanel.add(containersLabel, "split 2, span, gaptop 10");
        // mainPanel.add(new JSeparator(), "growx, wrap");
        // mainPanel.add(new JBLabel(Bundle.message(Resource.CONTAINERS_TOOL_DESCRIPTION)), "wrap, gapleft 15");
        // containersToolCombo.setPreferredSize(new Dimension(
        //         containersLabel.getPreferredSize().width,
        //         containersToolCombo.getPreferredSize().height
        // ));
        // mainPanel.add(containersToolCombo, "wrap, gapleft 15");

        // MCP Section
        mainPanel.add(new JBLabel(formatTitle(Bundle.message(Resource.MCP_SECTION_TITLE))), "split 2, span, gaptop 10");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(new JBLabel(Bundle.message(Resource.MCP_DESCRIPTION)), "wrap, gapleft 15");

        installMcpLink = new CxLinkLabel(Bundle.message(Resource.MCP_INSTALL_LINK), e -> installMcp());
        mcpStatusLabel.setVisible(false);
        mcpStatusLabel.setBorder(new EmptyBorder(0, 20, 0, 0));

        mainPanel.add(installMcpLink, "split 2, gapleft 15");
        mainPanel.add(mcpStatusLabel, "wrap, gapleft 15");

        CxLinkLabel editJsonLink = new CxLinkLabel(Bundle.message(Resource.MCP_EDIT_JSON_LINK), e -> openMcpJson());
        mainPanel.add(editJsonLink, "wrap, gapleft 15");
    }

    /**
     * Manual MCP installation invoked by the "Install MCP" link.
     * Provides inline status feedback (successfully saved, already up to date, or installation failure).
     * Note: Authentication is handled by disabling the button when not authenticated.
     */
    private void installMcp() {
        if (mcpInstallInProgress) {
            return;
        }

        ensureState();

        // Check if MCP is enabled at tenant level (this should not happen since button is disabled, but defensive check)
        if (!state.isMcpEnabled()) {
            showMcpStatus(Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE), JBColor.RED);
            return;
        }

        // At this point, user should be authenticated (button is disabled otherwise)
        GlobalSettingsSensitiveState sensitive = GlobalSettingsSensitiveState.getInstance();
        String credential = state.isApiKeyEnabled() ? sensitive.getApiKey() : sensitive.getRefreshToken();
        if (credential == null || credential.isBlank()) {
            return;
        }

        LOGGER.debug("[CxOneAssist] Manual MCP install started.");
        mcpInstallInProgress = true;

        McpInstallService.installSilentlyAsync(credential)
                .whenComplete((changed, throwable) ->
                        SwingUtilities.invokeLater(() -> handleMcpResult(changed, throwable)));
    }

    private void handleMcpResult(Boolean changed, Throwable throwable) {
        mcpInstallInProgress = false;

        if (throwable != null || changed == null) {
            showMcpStatus(Bundle.message(Resource.MCP_INSTALL_ERROR), JBColor.RED);
        } else if (changed) {
            showMcpStatus(Bundle.message(Resource.MCP_CONFIG_SAVED), JBColor.GREEN);
        } else {
            showMcpStatus(Bundle.message(Resource.MCP_CONFIG_UP_TO_DATE), JBColor.GREEN);
        }
    }

    private void showMcpStatus(String message, Color color) {
        mcpStatusLabel.setText(message);
        mcpStatusLabel.setForeground(color);
        mcpStatusLabel.setVisible(true);

        if (mcpClearTimer != null) {
            mcpClearTimer.stop();
        }
        mcpClearTimer = new Timer(5000, e -> {
            mcpStatusLabel.setVisible(false);
            mcpStatusLabel.setText("");
        });
        mcpClearTimer.setRepeats(false);
        mcpClearTimer.start();
    }

    /** Opens (and creates if necessary) the Copilot MCP configuration file then closes the settings dialog. */
    private void openMcpJson() {
        // Apply settings if modified, then close dialog window
        try {
            if (isModified()) {
                apply();
            }
        } catch (Exception ex) {
            LOGGER.warn("[CxOneAssist] Failed applying settings before closing dialog", ex);
        }
        java.awt.Window w = SwingUtilities.getWindowAncestor(mainPanel);
        if (w != null) {
            w.dispose();
        }

        Project[] open = ProjectManager.getInstance().getOpenProjects();
        Project project = (open.length > 0) ? open[0] : ProjectManager.getInstance().getDefaultProject();
        if (project == null) {
            LOGGER.warn("[CxOneAssist] No project available to open mcp.json");
            return;
        }

        java.nio.file.Path path;
        try {
            path = McpSettingsInjector.getMcpJsonPath();
        } catch (Exception ex) {
            LOGGER.warn("[CxOneAssist] Failed resolving MCP config path", ex);
            return;
        }
        if (path == null) {
            LOGGER.warn("[CxOneAssist] MCP config path is null");
            return;
        }

        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        if (vf != null && vf.exists()) {
            FileEditorManager.getInstance(project).openFile(vf, true);
        } else {
            LOGGER.warn("[CxOneAssist] mcp.json not found at: " + path);
            showMcpStatus(Bundle.message(Resource.MCP_NOT_FOUND), JBColor.RED);
        }
    }

    @Override
    public JPanel getMainPanel() {
        return mainPanel;
    }

    @Override
    public boolean isModified() {
        ensureState();
        return ossCheckbox.isSelected() != state.isOssRealtime()
                || secretsCheckbox.isSelected() != state.isSecretDetectionRealtime()
                || containersCheckbox.isSelected() != state.isContainersRealtime();
                // TEMPORARILY HIDDEN: Other realtime scanners - Will be restored in future release

                // || iacCheckbox.isSelected() != state.isIacRealtime()
                // || !Objects.equals(String.valueOf(containersToolCombo.getSelectedItem()), state.getContainersTool());
    }

    @Override
    public void apply() {
        ensureState();

        // Apply current UI selections to active scanner settings
        state.setOssRealtime(ossCheckbox.isSelected());
        state.setSecretDetectionRealtime(secretsCheckbox.isSelected());
        state.setContainersRealtime(containersCheckbox.isSelected());
        // TEMPORARILY HIDDEN: Other realtime scanners - Will be restored in future release
        // state.setIacRealtime(iacCheckbox.isSelected());
        // state.setContainersTool(String.valueOf(containersToolCombo.getSelectedItem()));

        // Save user preferences to preserve choices across MCP enable/disable cycles
        // This ensures that when MCP is temporarily disabled and then re-enabled,
        // the user's individual scanner preferences are restored instead of defaulting to "all enabled"
        state.setUserPreferences(
            ossCheckbox.isSelected(),
            secretsCheckbox.isSelected(),
            containersCheckbox.isSelected(), // Use current state for hidden fields
            state.isIacRealtime()         // Use current state for hidden fields
        );


        GlobalSettingsState.getInstance().apply(state);

        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                .settingsApplied();
    }

    @Override
    public void reset() {
        state = GlobalSettingsState.getInstance();
        ossCheckbox.setSelected(state.isOssRealtime());
        secretsCheckbox.setSelected(state.isSecretDetectionRealtime());
        containersCheckbox.setSelected(state.isContainersRealtime());
        // TEMPORARILY HIDDEN: Other realtime scanners - Will be restored in future release
        // iacCheckbox.setSelected(state.isIacRealtime());
        // containersToolCombo.setSelectedItem(
        //         state.getContainersTool() == null || state.getContainersTool().isBlank()
        //                 ? "docker"
        //                 : state.getContainersTool()
        // );
        updateAssistState();
    }

    private void updateAssistState() {
        ensureState();
        boolean authenticated = state.isAuthenticated();

        if (!authenticated) {
            // If not authenticated, immediately show message, disable controls, and uncheck scanners
            ossCheckbox.setEnabled(false);
            ossCheckbox.setSelected(false);
            secretsCheckbox.setEnabled(false);
            secretsCheckbox.setSelected(false);
            installMcpLink.setEnabled(false);
            // TEMPORARILY HIDDEN: Other realtime scanners - Will be restored in future release
             containersCheckbox.setEnabled(false);
             containersCheckbox.setSelected(false);
            // iacCheckbox.setEnabled(false);
            // iacCheckbox.setSelected(false);

            assistMessageLabel.setText(Bundle.message(Resource.CXONE_ASSIST_LOGIN_MESSAGE));
            assistMessageLabel.setForeground(JBColor.RED);
            assistMessageLabel.setVisible(true);
            return;
        }

        // Check if MCP status hasn't been checked yet (upgrade scenario)
        if (!state.isMcpStatusChecked()) {
            checkAndUpdateMcpStatusAsync();
            return; // UI will be updated when async check completes
        }

        // If authenticated, use the cached MCP status (determined during authentication)
        boolean mcpEnabled = state.isMcpEnabled();
        boolean isAuthenticated = state.isAuthenticated();
        updateUIWithMcpStatus(mcpEnabled, isAuthenticated);
    }


    private void updateUIWithMcpStatus(boolean mcpEnabled, boolean isAuthenticated) {
        ossCheckbox.setEnabled(mcpEnabled);
        secretsCheckbox.setEnabled(mcpEnabled);
        // Enable install MCP link only if MCP is enabled at tenant level AND user is authenticated
        installMcpLink.setEnabled(mcpEnabled && isAuthenticated);
        containersCheckbox.setEnabled(mcpEnabled);
        // TEMPORARILY HIDDEN: Other realtime scanners - Will be restored in future release
        // iacCheckbox.setEnabled(mcpEnabled);

        if (!mcpEnabled) {
            ensureState();

            // Preserve current scanner settings as user preferences before disabling
            if (!state.isUserPreferencesSet()) {
                state.saveCurrentSettingsAsUserPreferences();
                LOGGER.debug("[CxOneAssist] Preserved scanner settings as user preferences (MCP disabled)");
            }

            // When MCP is disabled, uncheck all scanner checkboxes to prevent realtime scanning
            ossCheckbox.setSelected(false);
            secretsCheckbox.setSelected(false);
            containersCheckbox.setSelected(false);
            // TEMPORARILY HIDDEN: Other realtime scanners - Will be restored in future release

            // iacCheckbox.setSelected(false);

            boolean settingsChanged = false;
            if (state.isOssRealtime()) {
                state.setOssRealtime(false);
                settingsChanged = true;
            }
            if (state.isSecretDetectionRealtime()) {
                state.setSecretDetectionRealtime(false);
                settingsChanged = true;
            }

             if (state.isContainersRealtime()) {
                 state.setContainersRealtime(false);
                 settingsChanged = true;
             }
            // TEMPORARILY HIDDEN: Other realtime scanners - Will be restored in future release
            // if (state.isIacRealtime()) {
            //     state.setIacRealtime(false);
            //     settingsChanged = true;
            // }

            if (settingsChanged) {
                GlobalSettingsState.getInstance().apply(state);
                ApplicationManager.getApplication().getMessageBus()
                        .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                        .settingsApplied();
            }

            assistMessageLabel.setText(Bundle.message(Resource.CXONE_ASSIST_MCP_DISABLED_MESSAGE));
            assistMessageLabel.setForeground(JBColor.RED);
            assistMessageLabel.setVisible(true);
        } else {
            // MCP is enabled - restore user preferences if available
            ensureState();
            if (state.isUserPreferencesSet()) {
                boolean preferencesApplied = state.applyUserPreferencesToRealtimeSettings();
                if (preferencesApplied) {
                    LOGGER.debug("[CxOneAssist] Restored user preferences for realtime scanners");
                    GlobalSettingsState.getInstance().apply(state);
                    ApplicationManager.getApplication().getMessageBus()
                            .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                            .settingsApplied();
                }
            }

            // Update UI to reflect current scanner state (including any restored preferences)
            ossCheckbox.setSelected(state.isOssRealtime());
            secretsCheckbox.setSelected(state.isSecretDetectionRealtime());
            containersCheckbox.setSelected(state.isContainersRealtime());
            assistMessageLabel.setVisible(false);
            assistMessageLabel.setText(""); // Clear any previous message
        }
    }

    /**
     * Asynchronously checks MCP status when it hasn't been checked before.
     * This handles the upgrade scenario where a user is already authenticated
     * but using a newer plugin version that includes MCP status checking.
     */
    private void checkAndUpdateMcpStatusAsync() {
        // Show loading message while checking
        assistMessageLabel.setText(Bundle.message(Resource.CHECKING_MCP_STATUS));
        assistMessageLabel.setForeground(JBColor.GRAY);
        assistMessageLabel.setVisible(true);

        // Disable controls while checking
        ossCheckbox.setEnabled(false);
        secretsCheckbox.setEnabled(false);
        containersCheckbox.setEnabled(false);
        installMcpLink.setEnabled(false);

        CompletableFuture.supplyAsync(() -> {
            try {
                GlobalSettingsState currentState = GlobalSettingsState.getInstance();
                GlobalSettingsSensitiveState currentSensitiveState = GlobalSettingsSensitiveState.getInstance();
                return com.checkmarx.intellij.commands.TenantSetting.isAiMcpServerEnabled(currentState, currentSensitiveState);
            } catch (Exception ex) {
                LOGGER.warn("Failed to check MCP status during upgrade scenario", ex);
                return false; // Default to disabled on error
            }
        }).whenCompleteAsync((mcpEnabled, throwable) -> {
            SwingUtilities.invokeLater(() -> {
                ensureState();

                // For future upgrade scenarios: preserve existing scanner configuration as user preferences
                // This prevents plugin updates from losing the user's current scanner settings
                if (!state.isUserPreferencesSet()) {
                    state.saveCurrentSettingsAsUserPreferences();
                    LOGGER.debug("[CxOneAssist] Preserved existing scanner configuration during upgrade");
                }

                // Update state with the determined MCP status
                state.setMcpEnabled(mcpEnabled);
                state.setMcpStatusChecked(true);
                GlobalSettingsState.getInstance().apply(state);

                // Update UI based on MCP availability (will restore preferences if MCP enabled)
                boolean isAuthenticated = state.isAuthenticated();
                updateUIWithMcpStatus(mcpEnabled, isAuthenticated);

                if (throwable != null) {
                    LOGGER.warn("Error during MCP status check", throwable);
                }
            });
        });
    }

    private void ensureState() {
        // Always get fresh state to ensure we have the latest MCP configuration
        state = GlobalSettingsState.getInstance();
    }

    private static String formatTitle(String raw) {
        if (raw == null) {
            return "<html></html>";
        }
        int idx = raw.indexOf(':');
        if (idx < 0 || idx == raw.length() - 1) {
            return String.format(Constants.HTML_WRAPPER_FORMAT, raw);
        }
        String before = raw.substring(0, idx + 1);
        String after = raw.substring(idx + 1).trim();
        String html = String.format("%s <b>%s</b>", before, after);
        return String.format(Constants.HTML_WRAPPER_FORMAT, html);
    }
}
