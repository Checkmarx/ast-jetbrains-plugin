package com.checkmarx.intellij.devassist.ui;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.application.ApplicationManager;
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
 * Welcome dialog for managing Checkmarx One plugin features and real-time scanner settings.
 */
public class WelcomeDialog extends DialogWrapper {

    private static final int WRAP_WIDTH = 250;
    private static final Dimension PREFERRED_DIALOG_SIZE = new Dimension(720, 460);
    private static final Dimension RIGHT_PANEL_SIZE = new Dimension(420, 420);
    private static final int PANEL_SPACING = 20;

    private final boolean mcpEnabled;
    private final RealTimeSettingsManager settingsManager;

    @Getter
    private JBCheckBox realTimeScannersCheckbox;

    public WelcomeDialog(@Nullable Project project, boolean mcpEnabled) {
        this(project, mcpEnabled, new DefaultRealTimeSettingsManager());
    }

    /**
     * Constructor with dependency injection for testability.
     *
     * @param project         current project (nullable)
     * @param mcpEnabled      whether MCP is enabled for this tenant
     * @param settingsManager wrapper around settings reads/writes
     */
    public WelcomeDialog(@Nullable Project project, boolean mcpEnabled, RealTimeSettingsManager settingsManager) {
        super(project, false);
        this.mcpEnabled = mcpEnabled;
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
        JBLabel title = new JBLabel(Bundle.message(Resource.WELCOME_TITLE));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        leftPanel.add(title, "gapbottom 4");

        // Subtitle wrapped to a fixed width for consistent layout
        JBLabel subtitle = new JBLabel("<html><div style='width:" + WRAP_WIDTH + "px;'>" +
                Bundle.message(Resource.WELCOME_SUBTITLE) + "</div></html>");
        subtitle.setForeground(UIUtil.getLabelForeground());
        leftPanel.add(subtitle);

        // Assist feature card
        leftPanel.add(createFeatureCard(), "gapbottom 8");

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
        JBLabel assistTitle = new JBLabel(Bundle.message(Resource.WELCOME_ASSIST_TITLE));
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
            JBLabel mcpDisabledIcon = new JBLabel(CxIcons.getWelcomeMcpDisableIcon());
            mcpDisabledIcon.setHorizontalAlignment(SwingConstants.CENTER);
            bulletsPanel.add(mcpDisabledIcon, "growx, wrap");
        }
        return bulletsPanel;
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
            return state.isAscaRealtime() && state.isOssRealtime() && state.isSecretDetectionRealtime() && state.isContainersRealtime();
        }

        @Override
        public boolean areAnyEnabled() {
            GlobalSettingsState state = GlobalSettingsState.getInstance();
            return state.isAscaRealtime() || state.isOssRealtime() || state.isSecretDetectionRealtime() || state.isContainersRealtime();
        }

        @Override
        public void setAll(boolean enable) {
            GlobalSettingsState state = GlobalSettingsState.getInstance();

            state.setAscaRealtime(enable);
            state.setOssRealtime(enable);
            state.setSecretDetectionRealtime(enable);
            state.setContainersRealtime(enable);
            state.setUserPreferences(enable, enable, enable, enable, state.getUserPrefIacRealtime());

            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                    .settingsApplied();
        }
    }
}