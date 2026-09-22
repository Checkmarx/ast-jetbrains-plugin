package com.checkmarx.intellij.cxdevassist.ui;

import com.checkmarx.intellij.common.components.CxLinkLabel;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.CxIcons;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.cxdevassist.settings.RealtimeScannersSettingsConfigurable;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Welcome dialog for managing Checkmarx Developer Assist plugin features
 */
public class CxDevAssistWelcomeDialog extends DialogWrapper {

    private static final int WRAP_WIDTH = 250;
    private static final Dimension PREFERRED_DIALOG_SIZE = new Dimension(720, 460);
    private static final Dimension RIGHT_PANEL_SIZE = new Dimension(420, 420);
    private static final int PANEL_SPACING = 20;

    private final boolean mcpEnabled;
    private final RealTimeSettingsManager settingsManager;
    @Nullable
    private final Project project;
    @Nullable
    private final String aiAgentNotice;

    @Getter
    private JBCheckBox realTimeScannersCheckbox;

    public CxDevAssistWelcomeDialog(@Nullable Project project, boolean mcpEnabled) {
        this(project, mcpEnabled, (String) null);
    }

    public CxDevAssistWelcomeDialog(@Nullable Project project, boolean mcpEnabled, @Nullable String aiAgentNotice) {
        this(project, mcpEnabled, aiAgentNotice, new DefaultRealTimeSettingsManager());
    }

    /**
     * Constructor with dependency injection for testability (no {@code aiAgentNotice}).
     */
    public CxDevAssistWelcomeDialog(@Nullable Project project, boolean mcpEnabled, RealTimeSettingsManager settingsManager) {
        this(project, mcpEnabled, null, settingsManager);
    }

    /**
     * Constructor with dependency injection for testability.
     *
     * @param project         current project (nullable)
     * @param mcpEnabled      whether MCP is enabled for this tenant
     * @param aiAgentNotice   notice about the resolved AI agent to show in the welcome dialog
     *                        (from {@code AiAgentLoginResolver}), or {@code null} if none applies
     * @param settingsManager wrapper around settings reads/writes
     */
    public CxDevAssistWelcomeDialog(@Nullable Project project, boolean mcpEnabled, @Nullable String aiAgentNotice,
                                     RealTimeSettingsManager settingsManager) {
        super(project, false);
        this.project = project;
        this.mcpEnabled = mcpEnabled;
        this.aiAgentNotice = aiAgentNotice;
        this.settingsManager = settingsManager;
        setOKButtonText(Bundle.message(Resource.WELCOME_CLOSE_BUTTON));
        init();
        setTitle("Checkmarx");
        getRootPane().setPreferredSize(PREFERRED_DIALOG_SIZE);
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(createLeftPanel(), BorderLayout.WEST);
        centerPanel.add(createRightImagePanel(), BorderLayout.CENTER);
        return centerPanel;
    }

    /**
     * Builds the left-side content area: title, subtitle, feature card and main bullets.
     */
    private JComponent createLeftPanel() {
        JPanel leftPanel = new JPanel(new MigLayout("insets 20 20 20 20, gapy 10, wrap 1", "[grow]"));

        // Title
        JBLabel title = new JBLabel(Bundle.message(Resource.DEVASSIST_PLUGIN_WELCOME_TITLE));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        leftPanel.add(title, "gapbottom 4");

        // Subtitle wrapped to a fixed width for consistent layout
        JBLabel subtitle = new JBLabel("<html><div style='width:" + WRAP_WIDTH + "px;'>" +
                Bundle.message(Resource.DEVASSIST_PLUGIN_WELCOME_SUBTITLE) + "</div></html>");
        subtitle.setForeground(UIUtil.getLabelForeground());
        leftPanel.add(subtitle);

        if (mcpEnabled && aiAgentNotice != null) {
            leftPanel.add(createAgentNoticeCard(aiAgentNotice), "growx, gapbottom 8");
        }

        // Assist feature card
        leftPanel.add(createFeatureCard(), "growx, gapbottom 8");

        // Main bullets
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_1));
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_2));
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_3));
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_4), "gapbottom 8");

        // MCP-specific controls
        if (mcpEnabled) {
            initializeRealtimeState();
        }
        configureCheckboxBehavior();
        refreshCheckboxState();
        return leftPanel;
    }

    /**
     * A simple card with a header (includes the MCP toggle when available) and feature bullets.
     */
    private JComponent createFeatureCard() {
        JPanel featureCard = new JPanel(new MigLayout("insets 10, gapy 4, wrap 1", "[grow]", "[]push[]"));
        featureCard.setBorder(BorderFactory.createLineBorder(JBColor.border()));

        // Subtle, theme-aware background differing slightly from the dialog panel
        Color base = UIUtil.getPanelBackground();
        Color subtleBg = JBColor.isBright() ? ColorUtil.darker(base, 1) : ColorUtil.brighter(base, 1);
        featureCard.setOpaque(true);
        featureCard.setBackground(subtleBg);

        featureCard.add(createFeatureCardHeader(subtleBg), "growx");
        featureCard.add(createFeatureCardBullets(), "growx");
        return featureCard;
    }

    private JComponent createFeatureCardHeader(Color backgroundColor) {
        JPanel header = new JPanel(new MigLayout("insets 0, gapx 6", "[][grow]"));
        header.setOpaque(false);
        realTimeScannersCheckbox = new JBCheckBox();
        realTimeScannersCheckbox.setEnabled(mcpEnabled);
        realTimeScannersCheckbox.setOpaque(false);
        realTimeScannersCheckbox.setContentAreaFilled(false);
        realTimeScannersCheckbox.setBackground(backgroundColor);
        header.add(realTimeScannersCheckbox);
        JBLabel assistTitle = new JBLabel(Bundle.message(Resource.DEVASSIST_PLUGIN_WELCOME_CHECK_TITLE));
        assistTitle.setFont(assistTitle.getFont().deriveFont(Font.BOLD));
        header.add(assistTitle, "growx, pushx");
        return header;
    }

    private JComponent createFeatureCardBullets() {
        JPanel bulletsPanel = new JPanel(new MigLayout("insets 0, wrap 1", "[grow]"));
        bulletsPanel.setOpaque(false);
        bulletsPanel.add(createBullet(Resource.WELCOME_ASSIST_FEATURE_1));
        bulletsPanel.add(createBullet(Resource.WELCOME_ASSIST_FEATURE_2));
        bulletsPanel.add(createBullet(Resource.WELCOME_ASSIST_FEATURE_3));
        if (mcpEnabled) {
            bulletsPanel.add(createBullet(Resource.WELCOME_MCP_INSTALLED_INFO));
        } else {
            // Show a theme-aware MCP disabled info icon
            JBLabel mcpDisabledIcon = new JBLabel(CxIcons.getDEVASSIST_WELCOME_AI_ERROR());
            mcpDisabledIcon.setHorizontalAlignment(SwingConstants.CENTER);
            bulletsPanel.add(mcpDisabledIcon, "growx, wrap");
        }
        return bulletsPanel;
    }

    /**
     * Notice card shown when {@code AiAgentLoginResolver} found the configured AI agent missing
     * on login - either because no supported agent is installed at all, or because a different,
     * detected agent was automatically selected in its place.
     */
    private JComponent createAgentNoticeCard(String noticeMessage) {
        JPanel panel = new JPanel(new MigLayout("insets 10, gapx 8, gapy 4", "[][grow]"));
        panel.setBorder(BorderFactory.createLineBorder(JBColor.border()));

        Color base = UIUtil.getPanelBackground();
        Color subtleBg = JBColor.isBright() ? ColorUtil.darker(base, 1) : ColorUtil.brighter(base, 1);
        panel.setOpaque(true);
        panel.setBackground(subtleBg);

        JBLabel warningIcon = new JBLabel(AllIcons.General.Warning);
        panel.add(warningIcon, "top, spany 3");

        String agentTitle =  Bundle.message(noticeMessage.contains("automatically")
                ? Resource.WELCOME_AGENT_SWITCHED_TITLE
                : Resource.WELCOME_AGENT_NOT_CONNECTED_TITLE);

        JBLabel title = new JBLabel(agentTitle);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        panel.add(title, "wrap, growx");

        JBLabel message = new JBLabel("<html><div style='width:" + WRAP_WIDTH + "px;'>" +
                noticeMessage + "</div></html>");
        panel.add(message, "wrap, growx");

        CxLinkLabel goToSettingsLink = new CxLinkLabel(
                Bundle.message(Resource.WELCOME_AGENT_NOT_CONNECTED_LINK),
                e -> openCxOneAssistSettings());
        panel.add(goToSettingsLink, "growx");

        return panel;
    }

    /**
     * Closes this dialog and opens the Settings dialog directly to the Checkmarx One Assist page.
     */
    private void openCxOneAssistSettings() {
        close(CANCEL_EXIT_CODE);
        ApplicationManager.getApplication().invokeLater(() ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, RealtimeScannersSettingsConfigurable.class));
    }

    // Builds the right-side panel that hosts an image

    private JComponent createRightImagePanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(JBUI.Borders.empty(PANEL_SPACING));
        rightPanel.setPreferredSize(RIGHT_PANEL_SIZE);
        rightPanel.setMinimumSize(RIGHT_PANEL_SIZE);
        rightPanel.setMaximumSize(RIGHT_PANEL_SIZE);

        // Load the original icon
        Icon originalIcon = CxIcons.getWelcomeScannerIcon();
        JBLabel imageLabel = new JBLabel(originalIcon);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.TOP);
        rightPanel.add(imageLabel, BorderLayout.NORTH);

        return rightPanel;
    }

    @Override
    protected Action[] createActions() {
        Action okAction = getOKAction();
        okAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
        return new Action[]{okAction};
    }

    @Override
    protected JComponent createSouthPanel() {
        JComponent southPanel = super.createSouthPanel();
        if (southPanel != null) {
            southPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()),
                    JBUI.Borders.empty(12, 16, 0, 16)
            ));
        }
        return southPanel;
    }

    /**
     * Wires the MCP checkbox to update all real-time flags via the settings manager.
     */
    private void configureCheckboxBehavior() {
        if (realTimeScannersCheckbox == null) return;
        realTimeScannersCheckbox.addActionListener(e -> {
            boolean anyCurrentlyEnabled = settingsManager.areAnyEnabled();
            settingsManager.setAll(!anyCurrentlyEnabled);

            refreshCheckboxState();
        });
    }

    /**
     * Initializes real-time scanner state based on user preferences.
     */
    private void initializeRealtimeState() {
        if (!mcpEnabled) {
            return;
        }

        GlobalSettingsState state = GlobalSettingsState.getInstance();

        if (state.getUserPreferencesSet()) {
            boolean settingsChanged = state.applyUserPreferencesToRealtimeSettings();
            if (settingsChanged) {
                ApplicationManager.getApplication().getMessageBus()
                        .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                        .settingsApplied();
            }
        }

        boolean anyEnabled = settingsManager.areAnyEnabled();

        if (!anyEnabled && !state.getUserPreferencesSet()) {
            settingsManager.setAll(true);
        }

        SwingUtilities.invokeLater(this::refreshCheckboxState);
    }

    /**
     * Syncs the MCP checkbox UI state with current settings.
     */
    private void refreshCheckboxState() {
        if (realTimeScannersCheckbox == null) return;
        boolean anyEnabled = settingsManager.areAnyEnabled();
        realTimeScannersCheckbox.setSelected(anyEnabled);
        updateCheckboxTooltip();
    }

    /**
     * Updates the checkbox tooltip based on current scanner state.
     */
    private void updateCheckboxTooltip() {
        if (realTimeScannersCheckbox == null) {
            return;
        }

        if (!mcpEnabled) {
            realTimeScannersCheckbox.setToolTipText("Checkmarx MCP is not enabled for this tenant.");
            return;
        }

        boolean allEnabled = settingsManager.areAllEnabled();
        boolean anyEnabled = settingsManager.areAnyEnabled();

        String tooltipText;
        if (allEnabled) {
            tooltipText = "Disable all real-time scanners";
        } else if (anyEnabled) {
            tooltipText = "Some scanners are enabled. Click to enable all real-time scanners";
        } else {
            tooltipText = "Enable all real-time scanners";
        }
        realTimeScannersCheckbox.setToolTipText(tooltipText);
    }

    /**
     * Builds a single bullet row with a glyph and a wrapped text label.
     */
    public JComponent createBullet(Resource res) {
        JPanel panel = new JPanel(new MigLayout("insets 0, gapx 6, fillx", "[][grow, fill]"));
        panel.setOpaque(false);

        JBLabel glyph = new JBLabel("\u2022");
        glyph.setFont(new Font("Dialog", Font.BOLD, glyph.getFont().getSize()));

        JBLabel text = new JBLabel("<html><div style='width:" + WRAP_WIDTH + "px;display:inline-block;'>" +
                Bundle.message(res) + "</div></html>");

        panel.add(glyph, "top");
        panel.add(text, "growx");
        return panel;
    }

    /**
     * Abstraction over real-time settings to allow testing.
     */
    public interface RealTimeSettingsManager {
        boolean areAllEnabled();

        boolean areAnyEnabled();

        void setAll(boolean enable);
    }

    /**
     * Production implementation using GlobalSettingsState.
     */
    private static class DefaultRealTimeSettingsManager implements RealTimeSettingsManager {
        @Override
        public boolean areAllEnabled() {
            GlobalSettingsState state = GlobalSettingsState.getInstance();
            return state.isAscaRealtime() && state.isOssRealtime() && state.isSecretDetectionRealtime() && state.isContainersRealtime() && state.isIacRealtime();
        }

        @Override
        public boolean areAnyEnabled() {
            GlobalSettingsState state = GlobalSettingsState.getInstance();
            return state.isAscaRealtime() || state.isOssRealtime() || state.isSecretDetectionRealtime() || state.isContainersRealtime() || state.isIacRealtime();
        }

        @Override
        public void setAll(boolean enable) {
            GlobalSettingsState state = GlobalSettingsState.getInstance();

            state.setAscaRealtime(enable);
            state.setOssRealtime(enable);
            state.setSecretDetectionRealtime(enable);
            state.setContainersRealtime(enable);
            state.setIacRealtime(enable);
            state.setUserPreferences(enable, enable, enable, enable, enable);

            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                    .settingsApplied();
        }
    }
}