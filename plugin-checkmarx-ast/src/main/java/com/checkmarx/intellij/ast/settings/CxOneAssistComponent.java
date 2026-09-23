package com.checkmarx.intellij.ast.settings;

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
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.checkmarx.intellij.devassist.configuration.mcp.McpAgentTarget;
import com.checkmarx.intellij.devassist.configuration.mcp.McpInstallService;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBusConnection;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


/**
 * Settings component for managing Checkmarx One Assist real-time scanner configurations.
 * Displays controls for OSS, Secrets, and Containers real-time scanners.
 */
public class CxOneAssistComponent implements SettingsComponent, Disposable {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistComponent.class);

    private final JPanel mainPanel = new JPanel(new MigLayout("", "[][grow]"));
    private final JBLabel assistMessageLabel = new JBLabel();

    private final JBLabel ascaTitle = new JBLabel(formatTitle("Checkmarx AI Secure Coding Assistant (ASCA): Activate ASCA:"));
    private final JBCheckBox ascaCheckbox = new JBCheckBox("Scan your file as you code");
    private final JBLabel ascaInstallationMsg = new JBLabel();

    private final JBLabel ossTitle = new JBLabel(formatTitle(Bundle.message(Resource.OSS_REALTIME_TITLE)));
    private final JBCheckBox ossCheckbox = new JBCheckBox(Bundle.message(Resource.OSS_REALTIME_CHECKBOX));

    private final JBLabel secretsTitle = new JBLabel(formatTitle(Bundle.message(Resource.SECRETS_REALTIME_TITLE)));
    private final JBCheckBox secretsCheckbox = new JBCheckBox(Bundle.message(Resource.SECRETS_REALTIME_CHECKBOX));

    private final JBLabel containersTitle = new JBLabel(formatTitle(Bundle.message(Resource.CONTAINERS_REALTIME_TITLE)));
    private final JBCheckBox containersCheckbox = new JBCheckBox(Bundle.message(Resource.CONTAINERS_REALTIME_CHECKBOX));

    private final JBLabel iacTitle = new JBLabel(formatTitle(Bundle.message(Resource.IAC_REALTIME_TITLE)));
    private final JBCheckBox iacCheckbox = new JBCheckBox(Bundle.message(Resource.IAC_REALTIME_CHECKBOX));

    private final ComboBox<String> containersToolCombo = new ComboBox<>(new String[]{"docker", "podman"});

    private final ComboBox<String> aiAgentCombo = new ComboBox<>(
            Arrays.stream(AiAgent.values()).map(AiAgent::getAgentName).toArray(String[]::new));

    private GlobalSettingsState state;
    private final MessageBusConnection connection;

    private final JBLabel mcpStatusLabel = new JBLabel();
    private CxLinkLabel installMcpLink;
    private CxLinkLabel editMcpConfigLink;
    private boolean mcpInstallInProgress;
    private Timer mcpClearTimer;
    private String lastNotificationEngine;


    private final JBLabel containerToolLabel = new JBLabel();
    private Timer containerToolTimer;

    public CxOneAssistComponent() {
        buildUI();
        reset();
        addAscaCheckBoxListener();
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
        assistMessageLabel.setForeground(JBColor.RED);
        assistMessageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        assistMessageLabel.setVisible(false);
        mainPanel.add(assistMessageLabel, "hidemode 3, growx, alignx left, wrap, gapbottom 5");

        // ASCA Realtime - First checkbox
        mainPanel.add(ascaTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(ascaCheckbox, "split 2, gapleft 15");
        mainPanel.add(ascaInstallationMsg, "gapleft 5, wrap, gapbottom 10");

        // OSS Realtime
        mainPanel.add(ossTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(ossCheckbox, "wrap, gapbottom 10, gapleft 15");

        mainPanel.add(secretsTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(secretsCheckbox, "wrap, gapbottom 10, gapleft 15");

        mainPanel.add(containersTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(containersCheckbox, "wrap, gapbottom 10, gapleft 15");

        mainPanel.add(iacTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(iacCheckbox, "wrap, gapbottom 10, gapleft 15");


        JBLabel containersLabel = new JBLabel(formatTitle(Bundle.message(Resource.IAC_REALTIME_SCANNER_PREFIX)));
        mainPanel.add(containersLabel, "split 2, span, gaptop 10");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(new JBLabel(Bundle.message(Resource.CONTAINERS_TOOL_DESCRIPTION)), "wrap, gapleft 15");

        containersToolCombo.setPreferredSize(new Dimension(
                containersLabel.getPreferredSize().width,
                containersToolCombo.getPreferredSize().height
        ));
        mainPanel.add(containersToolCombo, "wrap, gapleft 15");

        // AI Agent Section
        mainPanel.add(new JBLabel(formatTitle(Bundle.message(Resource.AI_AGENT_SECTION_TITLE))), "split 2, span, gaptop 10");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(new JBLabel(Bundle.message(Resource.AI_AGENT_DESCRIPTION)), "wrap, gapleft 15");
        aiAgentCombo.setPreferredSize(new Dimension(
                containersToolCombo.getPreferredSize().width,
                aiAgentCombo.getPreferredSize().height
        ));
        mainPanel.add(aiAgentCombo, "wrap, gapleft 15");

        // MCP Section
        mainPanel.add(new JBLabel(formatTitle(Bundle.message(Resource.MCP_SECTION_TITLE))), "split 2, span, gaptop 10");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(new JBLabel(Bundle.message(Resource.MCP_DESCRIPTION)), "wrap, gapleft 15");

        installMcpLink = new CxLinkLabel(Bundle.message(Resource.MCP_INSTALL_LINK), e -> installMcp());
        mcpStatusLabel.setVisible(false);
        mcpStatusLabel.setBorder(new EmptyBorder(0, 20, 0, 0));

        mainPanel.add(installMcpLink, "split 2, gapleft 15");
        mainPanel.add(mcpStatusLabel, "wrap, gapleft 15");

        editMcpConfigLink = new CxLinkLabel(Bundle.message(Resource.MCP_EDIT_JSON_LINK), e -> openMcpJson());
        mainPanel.add(editMcpConfigLink, "wrap, gapleft 15");
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

        // Use whatever is currently selected in the AI Agent dropdown, not the last-persisted
        // value - the user may not have clicked Apply/OK yet.
        AiAgent selectedAgent = AiAgent.fromAgentName((String) aiAgentCombo.getSelectedItem());

        McpInstallService.installSilentlyAsync(credential, selectedAgent)
                .whenComplete((changed, throwable) ->
                        SwingUtilities.invokeLater(() -> handleMcpResult(changed, throwable)));
    }

    /**
     * Installs the Checkmarx MCP entry for a newly-selected agent, invoked from {@link #apply()}
     * once its plugin is confirmed installed. Mirrors {@link #installMcp()}'s credential handling,
     * but silently no-ops (rather than surfacing an error) when MCP isn't enabled or the user
     * isn't authenticated, since {@code apply()} shouldn't fail the rest of the settings save over
     * a best-effort MCP install.
     */
    private void configureMcpForAgentInBackground(AiAgent agent) {
        ensureState();
        if (!state.isMcpEnabled() || !state.isAuthenticated()) {
            return;
        }
        GlobalSettingsSensitiveState sensitive = GlobalSettingsSensitiveState.getInstance();
        String credential = state.isApiKeyEnabled() ? sensitive.getApiKey() : sensitive.getRefreshToken();
        if (credential == null || credential.isBlank()) {
            return;
        }

        McpInstallService.installSilentlyAsync(credential, agent)
                .whenComplete((changed, throwable) ->
                        SwingUtilities.invokeLater(() -> handleMcpResult(changed, throwable)));
    }

    /**
     * The first open project, or {@code null} if none is open (e.g. the Welcome screen). Used for
     * project-scoped "is this agent's plugin available" checks that also accept a global check.
     */
    @Nullable
    private Project currentProjectOrNull() {
        Project[] open = ProjectManager.getInstance().getOpenProjects();
        return open.length > 0 ? open[0] : null;
    }

    /**
     * Package-private seam around {@link AiAgent#isInstalled(Project)} so tests can stub it
     * without needing a real IDE plugin registry/tool-window manager, matching this class's other
     * unit tests, which run without a real IntelliJ Application/Project.
     */
    boolean isAgentInstalled(AiAgent agent) {
        return agent.isInstalled(currentProjectOrNull());
    }

    /**
     * Modal popup shown from {@link #apply()} when the user picks an agent whose plugin isn't
     * installed. Blocking (rather than a balloon notification) so the choice is acknowledged
     * before the Settings dialog closes; offers a direct jump to the plugin's Marketplace page.
     */
    private void showAgentNotInstalledPopup(AiAgent agent) {
        String message = Bundle.message(Resource.AI_AGENT_NOT_INSTALLED_MESSAGE, agent.getAgentName());
        String installLabel = Bundle.message(Resource.AI_AGENT_INSTALL_ACTION_LABEL);
        int result = Messages.showDialog(mainPanel, message, Constants.TOOL_WINDOW_ID,
                new String[]{installLabel, Messages.getOkButton()}, 0, Messages.getWarningIcon());
        if (result == 0) {
            agent.openMarketplacePage(currentProjectOrNull());
        }
    }

    /**
     * Modal popup shown from {@link #apply()} when the user switches to JetBrains AI Chat.
     * Offers to restart immediately rather than just informing the user.
     */
    private int showRestartIdePopup(AiAgent agent) {
        String message = Bundle.message(Resource.AI_AGENT_RESTART_REQUIRED_MESSAGE, agent.getAgentName());
        String restartLabel = Bundle.message(Resource.AI_AGENT_RESTART_ACTION_LABEL);
        return Messages.showDialog(mainPanel, message, Constants.TOOL_WINDOW_ID,
                new String[]{restartLabel, Messages.getOkButton()}, 0, Messages.getInformationIcon());
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

    private void showContainerEngineStatus(String message, Color color) {
        containerToolLabel.setText(message);
        containerToolLabel.setForeground(color);
        containerToolLabel.setVisible(true);

        if (containerToolTimer != null) {
            containerToolTimer.stop();
        }
        containerToolTimer = new Timer(5000, e -> {
            containerToolLabel.setVisible(false);
            containerToolLabel.setText("");
        });
        containerToolTimer.setRepeats(false);
        containerToolTimer.start();
    }

    /**
     * Handles the "Edit in mcp.json" link. Delegates entirely to the currently-selected agent's
     * {@link McpAgentTarget}: if it exposes a dedicated settings page
     * ({@link McpAgentTarget#getSettingsConfigurableId()}), navigates the (still-open) Settings
     * dialog there instead of hand-editing its config file, since that file's location/schema is
     * typically undocumented internals and the settings UI is the officially supported way to
     * manage it. Otherwise opens (and creates if necessary) the raw config file, the same way
     * this always worked for Copilot. Adding a new agent needs no changes here - only its
     * {@code McpAgentTarget} implementation decides which path applies.
     */
    private void openMcpJson() {
        // Capture the currently-selected agent before apply() touches persisted state.
        AiAgent selectedAgent = AiAgent.fromAgentName((String) aiAgentCombo.getSelectedItem());

        try {
            if (isModified()) {
                apply();
            }
        } catch (Exception ex) {
            LOGGER.warn("[CxOneAssist] Failed applying settings before opening MCP configuration", ex);
        }

        McpAgentTarget target = selectedAgent.mcpTarget();
        Optional<String> configurableId = target.getSettingsConfigurableId();
        boolean openedSettingsPage = configurableId.isPresent() && openAgentMcpSettingsPage(configurableId.get());
        if (!openedSettingsPage) {
            // No settings page for this agent, or opening it failed for any reason (plugin not
            // installed/enabled, configurable id renamed, unexpected exception, etc.) - fall back
            // to the raw config file so the user still has a way to inspect/edit the MCP entry.
            openMcpConfigFile(target);
        }
    }

    /**
     * Navigates the currently-open Settings dialog to the given agent-owned MCP settings page.
     *
     * @return true if the page was found and selected; false if it couldn't be located or an
     * error occurred, in which case the caller falls back to opening the raw config file instead.
     */
    private boolean openAgentMcpSettingsPage(String configurableId) {
        try {
            DataContext context = DataManager.getInstance().getDataContext(mainPanel);
            Settings settings = context.getData(Settings.KEY);
            if (settings != null) {
                Configurable configurable = settings.find(configurableId);
                if (configurable != null) {
                    settings.select(configurable);
                    return true;
                }
            }
            LOGGER.warn("[CxOneAssist] Could not locate agent MCP settings page (id: " + configurableId
                    + "). Falling back to opening its MCP config file.");
            return false;
        } catch (Exception ex) {
            LOGGER.warn("[CxOneAssist] Failed opening agent MCP settings page (id: " + configurableId
                    + "). Falling back to opening its MCP config file.", ex);
            return false;
        }
    }

    /**
     * Opens (and creates if necessary) the given agent's raw MCP configuration file, closing the
     * settings dialog first since such agents have no dedicated settings UI for it.
     */
    private void openMcpConfigFile(McpAgentTarget target) {
        Window w = SwingUtilities.getWindowAncestor(mainPanel);
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
            path = target.getConfigPath();
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
        return ascaCheckbox.isSelected() != state.isAscaRealtime()
                || ossCheckbox.isSelected() != state.isOssRealtime()
                || secretsCheckbox.isSelected() != state.isSecretDetectionRealtime()
                || containersCheckbox.isSelected() != state.isContainersRealtime()
                || iacCheckbox.isSelected() != state.isIacRealtime()
                || !Objects.equals(containersToolCombo.getSelectedItem(), state.getContainersTool())
                || !Objects.equals(aiAgentToSettingsValue((String) aiAgentCombo.getSelectedItem()),
                        AiAgent.fromSettingsValue(state.getAiAgent()).name());
    }

    @Override
    public void apply() {
        ensureState();

        boolean ascaSelected = ascaCheckbox.isSelected();
        boolean ossSelected = ossCheckbox.isSelected();
        boolean secretsSelected = secretsCheckbox.isSelected();
        boolean containersSelected = containersCheckbox.isSelected();
        boolean iacSelected = iacCheckbox.isSelected();

        state.setAscaRealtime(ascaSelected);
        // Sync legacy ASCA setting for compatibility with existing code
        state.setAsca(ascaSelected);
        state.setOssRealtime(ossSelected);
        state.setSecretDetectionRealtime(secretsSelected);
        state.setContainersRealtime(containersSelected);
        state.setIacRealtime(iacSelected);
        String selectedValue = (String) containersToolCombo.getSelectedItem();
        state.setContainersTool(selectedValue);

        AiAgent previousAgent = AiAgent.fromSettingsValue(state.getAiAgent());
        AiAgent newAgent = AiAgent.fromAgentName((String) aiAgentCombo.getSelectedItem());
        boolean agentChanged = previousAgent != newAgent;

        // Whatever the user picked is saved, whether or not its plugin is installed - the combo
        // is never reverted. If it's not installed, still save the choice and warn via a popup,
        // but MCP is configured for it either way (as soon as the dropdown actually changes) so
        // it's already in place once the user installs the plugin, rather than requiring them to
        // revisit this page afterward.
        state.setAiAgent(newAgent.name());
        if (agentChanged) {
            int restartResult = 1;
            if (!isAgentInstalled(newAgent)) {
                LOGGER.warn("[CxOneAssist] Selected AI agent plugin is not installed: " + newAgent.getAgentName());
                showAgentNotInstalledPopup(newAgent);
            } else if (newAgent == AiAgent.JETBRAINS_AI_ASSISTANT) {
                restartResult = showRestartIdePopup(AiAgent.JETBRAINS_AI_ASSISTANT);
            }
            // After agent switch. the previously-selected agent's MCP entry (and its credential) is cleared
            previousAgent.uninstallMcpInBackground(LOGGER, "after switching to " + newAgent.getAgentName(),
                    () -> showMcpStatus(Bundle.message(Resource.MCP_PREVIOUS_AGENT_CLEANUP_FAILED, previousAgent.getAgentName()), JBColor.RED));
            configureMcpForAgentInBackground(newAgent);
            if (restartResult == 0) {
                ApplicationManagerEx.getApplicationEx().restart(true);
            }
        }

        state.setUserPreferences(ascaSelected, ossSelected, secretsSelected, containersSelected, iacSelected);

        ApplicationManager.getApplication().getMessageBus()
                .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                .settingsApplied();

        ApplicationManager.getApplication().executeOnPooledThread(this::validateIACEngine);
    }

    @Override
    public void reset() {
        state = GlobalSettingsState.getInstance();

        // Initialize ASCA checkbox - use realtime setting or fallback to legacy setting for compatibility
        boolean ascaState = state.isAscaRealtime() || state.isAsca();
        ascaCheckbox.setSelected(ascaState);

        ossCheckbox.setSelected(state.isOssRealtime());
        secretsCheckbox.setSelected(state.isSecretDetectionRealtime());
        containersCheckbox.setSelected(state.isContainersRealtime());
        iacCheckbox.setSelected(state.isIacRealtime());
        containersToolCombo.setSelectedItem(state.getContainersTool());
        aiAgentCombo.setSelectedItem(aiAgentToLabel(state.getAiAgent()));

        updateAssistState();
    }

    private static String aiAgentToSettingsValue(String label) {
        return AiAgent.fromAgentName(label).name();
    }

    private static String aiAgentToLabel(String settingsValue) {
        return AiAgent.fromSettingsValue(settingsValue).getAgentName();
    }

    private void updateAssistState() {
        ensureState();
        boolean authenticated = state.isAuthenticated();
        boolean hasAssistLicense = state.isOneAssistLicenseEnabled() || state.isDevAssistLicenseEnabled();

        if (!hasAssistLicense) {
            // No Assist licenses: hide UI and hard-disable realtime scanners.
            disableAssistUI("CxOne Assist is unavailable without a One Assist or Dev Assist license.",
                    JBColor.RED,
                    false);
            return;
        }

        if (!authenticated) {
            disableAssistUI(Bundle.message(Resource.CXONE_ASSIST_LOGIN_MESSAGE), JBColor.RED, true);
            return;
        }

        // License present and authenticated - show panel
        mainPanel.setVisible(true);

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

    private void disableAssistUI(String message, Color color, boolean keepVisible) {
        ensureState();

        mainPanel.setVisible(keepVisible);

        ascaCheckbox.setEnabled(false);
        ossCheckbox.setEnabled(false);
        secretsCheckbox.setEnabled(false);
        containersCheckbox.setEnabled(false);
        iacCheckbox.setEnabled(false);
        containersToolCombo.setEnabled(false);
        aiAgentCombo.setEnabled(false);
        if (installMcpLink != null) {
            installMcpLink.setEnabled(false);
        }

        // Preserve user preferences once before clearing, so they can be restored when license becomes available
        if (!state.getUserPreferencesSet()) {
            state.saveCurrentSettingsAsUserPreferences();
        }

        // Uncheck all realtime scanners in UI and state
        ascaCheckbox.setSelected(false);
        ossCheckbox.setSelected(false);
        secretsCheckbox.setSelected(false);
        containersCheckbox.setSelected(false);
        iacCheckbox.setSelected(false);

        boolean settingsChanged = false;
        if (state.isAscaRealtime() || state.isAsca()) {
            state.setAscaRealtime(false);
            state.setAsca(false);
            settingsChanged = true;
        }
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
        if (state.isIacRealtime()) {
            state.setIacRealtime(false);
            settingsChanged = true;
        }

        if (settingsChanged) {
            GlobalSettingsState.getInstance().apply(state);
            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                    .settingsApplied();
        }

        if (keepVisible && message != null && !message.isBlank()) {
            assistMessageLabel.setText(message);
            assistMessageLabel.setForeground(color);
            assistMessageLabel.setVisible(true);
        } else {
            assistMessageLabel.setVisible(false);
        }
    }


    private void updateUIWithMcpStatus(boolean mcpEnabled, boolean isAuthenticated) {
        ascaCheckbox.setEnabled(mcpEnabled);
        ossCheckbox.setEnabled(mcpEnabled);
        secretsCheckbox.setEnabled(mcpEnabled);
        // Enable install MCP link only if MCP is enabled at tenant level AND user is authenticated
        installMcpLink.setEnabled(mcpEnabled && isAuthenticated);
        containersCheckbox.setEnabled(mcpEnabled);
        iacCheckbox.setEnabled(mcpEnabled);
        containersToolCombo.setEnabled(mcpEnabled);

        if (!mcpEnabled) {
            ensureState();

            // Preserve current scanner settings as user preferences before disabling
            if (!state.getUserPreferencesSet()) {
                state.saveCurrentSettingsAsUserPreferences();
                LOGGER.debug("[CxOneAssist] Preserved scanner settings as user preferences (MCP disabled)");
            }

            // When MCP is disabled, uncheck all scanner checkboxes to prevent realtime scanning
            ascaCheckbox.setSelected(false);
            ossCheckbox.setSelected(false);
            secretsCheckbox.setSelected(false);
            containersCheckbox.setSelected(false);
            iacCheckbox.setSelected(false);
            containersToolCombo.setSelectedItem(state.getContainersTool());

            boolean settingsChanged = false;
            if (state.isAscaRealtime()) {
                state.setAscaRealtime(false);
                settingsChanged = true;
            }
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
            if (state.isIacRealtime()) {
                state.setIacRealtime(false);
                settingsChanged = true;
            }

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
            if (state.getUserPreferencesSet()) {
                boolean preferencesApplied = state.applyUserPreferencesToRealtimeSettings();
                if (preferencesApplied) {
                    LOGGER.debug("[CxOneAssist] Restored user preferences for realtime scanners");
                    // Changes are automatically persisted since state is the singleton instance
                    ApplicationManager.getApplication().getMessageBus()
                            .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                            .settingsApplied();
                }
            }

            // Update UI to reflect current scanner state (including any restored preferences)
            ascaCheckbox.setSelected(state.isAscaRealtime());
            ossCheckbox.setSelected(state.isOssRealtime());
            secretsCheckbox.setSelected(state.isSecretDetectionRealtime());
            containersCheckbox.setSelected(state.isContainersRealtime());
            iacCheckbox.setSelected(state.isIacRealtime());
            containersToolCombo.setSelectedItem(state.getContainersTool());
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
        ascaCheckbox.setEnabled(false);
        ossCheckbox.setEnabled(false);
        secretsCheckbox.setEnabled(false);
        containersCheckbox.setEnabled(false);
        iacCheckbox.setEnabled(false);
        installMcpLink.setEnabled(false);

        CompletableFuture.supplyAsync(() -> {
            try {
                GlobalSettingsState currentState = GlobalSettingsState.getInstance();
                GlobalSettingsSensitiveState currentSensitiveState = GlobalSettingsSensitiveState.getInstance();
                return TenantSetting.isAiMcpServerEnabled(currentState, currentSensitiveState);
            } catch (Exception ex) {
                LOGGER.warn("Failed to check MCP status during upgrade scenario", ex);
                return false; // Default to disabled on error
            }
        }).whenCompleteAsync((mcpEnabled, throwable) -> {
            SwingUtilities.invokeLater(() -> {
                ensureState();

                // For future upgrade scenarios: preserve existing scanner configuration as user preferences
                // This prevents plugin updates from losing the user's current scanner settings
                if (!state.getUserPreferencesSet()) {
                    state.saveCurrentSettingsAsUserPreferences();
                    LOGGER.debug("[CxOneAssist] Preserved existing scanner configuration during upgrade");
                }

                // Update state with the determined MCP status
                state.setMcpEnabled(mcpEnabled);
                state.setMcpStatusChecked(true);
                // Changes are automatically persisted since state is the singleton instance

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

    private void addAscaCheckBoxListener() {
        ascaCheckbox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                // Show success message when enabled
                setAscaInstallationMsg(Bundle.message(Resource.ASCA_STARTED_MSG), JBColor.GREEN);
                ascaInstallationMsg.setVisible(true);

                // Auto-hide message after 3 seconds
                Timer timer = new Timer(3000, event -> ascaInstallationMsg.setVisible(false));
                timer.setRepeats(false);
                timer.start();
            } else {
                // Hide message when disabled
                ascaInstallationMsg.setVisible(false);
            }
        });
    }

    private void setAscaInstallationMsg(String message, JBColor color) {
        ascaInstallationMsg.setText(String.format("<html>%s</html>", message));
        ascaInstallationMsg.setForeground(color);
    }

    private void validateIACEngine() {
        String engineName = state.getContainersTool();
        try {
            CxWrapperFactory.build().checkEngineExist(engineName);
            lastNotificationEngine = "";
        } catch (Exception e) {
            if (engineName.equalsIgnoreCase(lastNotificationEngine)) {
                return;
            }
            lastNotificationEngine = engineName;
            ApplicationManager.getApplication().invokeLater(() -> {
                Utils.showAppLevelNotification(DevAssistConstants.IAC_ENGINE_VALIDATION_ERROR, String.format("%s %s", e.getMessage(), DevAssistConstants.IAC_PREREQUISITE),
                        NotificationType.WARNING,
                        true, Bundle.message(Resource.DEVASSIST_DOC_LINK));
            });
        }
    }
}
