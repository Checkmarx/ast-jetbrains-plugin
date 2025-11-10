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
import java.util.Objects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.fileEditor.FileEditorManager;

/**
 * UI component shown under Tools > Checkmarx One > CxOne Assist.
 * Provides realtime feature toggles and container management tool selection.
 * Also offers manual MCP configuration installation.
 */
public class CxOneAssistComponent implements SettingsComponent, Disposable {

    private static final Logger LOGGER = Utils.getLogger(CxOneAssistComponent.class);

    private final JPanel mainPanel = new JPanel(new MigLayout("", "[][grow]"));

    private final JBLabel ossTitle = new JBLabel(formatTitle(Bundle.message(Resource.OSS_REALTIME_TITLE)));
    private final JBCheckBox ossCheckbox = new JBCheckBox(Bundle.message(Resource.OSS_REALTIME_CHECKBOX));

    private final JBLabel secretsTitle = new JBLabel(formatTitle(Bundle.message(Resource.SECRETS_REALTIME_TITLE)));
    private final JBCheckBox secretsCheckbox = new JBCheckBox(Bundle.message(Resource.SECRETS_REALTIME_CHECKBOX));

    private final JBLabel containersTitle = new JBLabel(formatTitle(Bundle.message(Resource.CONTAINERS_REALTIME_TITLE)));
    private final JBCheckBox containersCheckbox = new JBCheckBox(Bundle.message(Resource.CONTAINERS_REALTIME_CHECKBOX));

    private final JBLabel iacTitle = new JBLabel(formatTitle(Bundle.message(Resource.IAC_REALTIME_TITLE)));
    private final JBCheckBox iacCheckbox = new JBCheckBox(Bundle.message(Resource.IAC_REALTIME_CHECKBOX));

    private final ComboBox<String> containersToolCombo = new ComboBox<>(new String[]{"docker", "podman"});

    private GlobalSettingsState state;
    private final MessageBusConnection connection;

    private final JBLabel mcpStatusLabel = new JBLabel();
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
        // OSS Realtime
        mainPanel.add(ossTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(ossCheckbox, "wrap, gapbottom 10, gapleft 15");

        // Secret Detection
        mainPanel.add(secretsTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(secretsCheckbox, "wrap, gapbottom 10, gapleft 15");

        // Containers Realtime
        mainPanel.add(containersTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(containersCheckbox, "wrap, gapbottom 10, gapleft 15");

        // IaC Realtime
        mainPanel.add(iacTitle, "split 2, span");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(iacCheckbox, "wrap, gapbottom 10, gapleft 15");

        // Containers management tool dropdown
        JBLabel containersLabel = new JBLabel(formatTitle(Bundle.message(Resource.IAC_REALTIME_SCANNER_PREFIX)));
        mainPanel.add(containersLabel, "split 2, span, gaptop 10");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(new JBLabel(Bundle.message(Resource.CONTAINERS_TOOL_DESCRIPTION)), "wrap, gapleft 15");
        containersToolCombo.setPreferredSize(new Dimension(
                containersLabel.getPreferredSize().width,
                containersToolCombo.getPreferredSize().height
        ));
        mainPanel.add(containersToolCombo, "wrap, gapleft 15");

        // MCP Section
        mainPanel.add(new JBLabel(formatTitle(Bundle.message(Resource.MCP_SECTION_TITLE))), "split 2, span, gaptop 10");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(new JBLabel(Bundle.message(Resource.MCP_DESCRIPTION)), "wrap, gapleft 15");

        CxLinkLabel installMcpLink = new CxLinkLabel(Bundle.message(Resource.MCP_INSTALL_LINK), e -> installMcp());
        mcpStatusLabel.setVisible(false);
        mcpStatusLabel.setBorder(new EmptyBorder(0, 20, 0, 0));

        mainPanel.add(installMcpLink, "split 2, gapleft 15");
        mainPanel.add(mcpStatusLabel, "wrap, gapleft 15");

        CxLinkLabel editJsonLink = new CxLinkLabel(Bundle.message(Resource.MCP_EDIT_JSON_LINK), e -> openMcpJson());
        mainPanel.add(editJsonLink, "wrap, gapleft 15");
    }

    /**
     * Manual MCP installation invoked by the "Install MCP" link.
     * Provides inline status feedback (successfully saved, already up to date, or auth required).
     */
    private void installMcp() {
        if (mcpInstallInProgress) {
            return;
        }

        ensureState();
        GlobalSettingsSensitiveState sensitive = GlobalSettingsSensitiveState.getInstance();

        if (!state.isAuthenticated()) {
            showMcpStatus(Bundle.message(Resource.MCP_AUTH_REQUIRED), JBColor.RED);
            return;
        }

        String credential = state.isApiKeyEnabled() ? sensitive.getApiKey() : sensitive.getRefreshToken();
        if (credential == null || credential.isBlank()) {
            showMcpStatus(Bundle.message(Resource.MCP_AUTH_REQUIRED), JBColor.RED);
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
            showMcpStatus(Bundle.message(Resource.MCP_AUTH_REQUIRED), JBColor.RED);
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
                || containersCheckbox.isSelected() != state.isContainersRealtime()
                || iacCheckbox.isSelected() != state.isIacRealtime()
                || !Objects.equals(String.valueOf(containersToolCombo.getSelectedItem()), state.getContainersTool());
    }

    @Override
    public void apply() {
        ensureState();
        state.setOssRealtime(ossCheckbox.isSelected());
        state.setSecretDetectionRealtime(secretsCheckbox.isSelected());
        state.setContainersRealtime(containersCheckbox.isSelected());
        state.setIacRealtime(iacCheckbox.isSelected());
        state.setContainersTool(String.valueOf(containersToolCombo.getSelectedItem()));

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
        iacCheckbox.setSelected(state.isIacRealtime());
        containersToolCombo.setSelectedItem(
                state.getContainersTool() == null || state.getContainersTool().isBlank()
                        ? "docker"
                        : state.getContainersTool()
        );
    }

    private void ensureState() {
        if (state == null) {
            state = GlobalSettingsState.getInstance();
        }
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
