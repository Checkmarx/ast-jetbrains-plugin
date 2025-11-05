package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;

/**
 * Welcome dialog displayed after successful authentication.
 * <p>
 * Presents plugin features and allows enabling/disabling real-time scanners
 * when Multi-component Protection (MCP) is available.
 * <p>
 * The dialog abstracts away settings management for testability using the
 * {@link RealTimeSettingsManager} interface.
 */
public class WelcomeDialog extends DialogWrapper {

    private static final int WRAP_WIDTH = 250;
    private static final Dimension PREFERRED_DIALOG_SIZE = new Dimension(720, 460);

    private final boolean mcpEnabled;
    private final RealTimeSettingsManager settingsManager;

    @Getter
    private JBCheckBox realTimeScannersCheckbox;

    // Cache original icon image for scaling
    private Image rightPanelOriginalImage;
    private int rightPanelOriginalW;
    private int rightPanelOriginalH;

    public WelcomeDialog(@Nullable Project project, boolean mcpEnabled) {
        this(project, mcpEnabled, new DefaultRealTimeSettingsManager());
    }

    /**
     * Constructor with dependency injection for testability.
     *
     * @param project         current project
     * @param mcpEnabled      whether MCP is enabled
     * @param settingsManager manager for real-time settings
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
        JComponent left = createLeftPanel();
        centerPanel.add(left, BorderLayout.WEST);
        centerPanel.add(createRightImagePanel(), BorderLayout.CENTER);
        return centerPanel;
    }

    private JComponent createLeftPanel() {
        JPanel leftPanel = new JPanel(new MigLayout("insets 20 20 20 20, gapy 10, wrap 1", "[grow]"));

        JBLabel title = new JBLabel(Bundle.message(Resource.WELCOME_TITLE));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        leftPanel.add(title, "gapbottom 4");

        JBLabel subtitle = new JBLabel("<html><div style='width:" + WRAP_WIDTH + "px;'>" + Bundle.message(Resource.WELCOME_SUBTITLE) + "</div></html>");
        subtitle.setForeground(UIUtil.getLabelForeground());
        leftPanel.add(subtitle);

        leftPanel.add(createFeatureCard(), "gapbottom 8");

        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_1));
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_2));
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_3));
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_4), "gapbottom 8");

        if (mcpEnabled) {
            initializeRealtimeState();
            configureCheckboxBehavior();
            refreshCheckboxState();
        }
        return leftPanel;
    }

    private JComponent createFeatureCard() {
        JPanel featureCard = new JPanel(new MigLayout("insets 10, gapy 4, wrap 1", "[grow]", "[]push[]"));
        featureCard.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        featureCard.setBackground(JBColor.background());

        featureCard.add(createFeatureCardHeader(), "growx");
        featureCard.add(createFeatureCardBullets(), "growx");
        return featureCard;
    }

    private JComponent createFeatureCardHeader() {
        JPanel header = new JPanel(new MigLayout("insets 0, gapx 6", "[][grow]"));
        header.setOpaque(false);
        if (mcpEnabled) {
            realTimeScannersCheckbox = new JBCheckBox();
            realTimeScannersCheckbox.setToolTipText("Enable all real-time scanners");
            header.add(realTimeScannersCheckbox);
        }
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
            JBLabel mcpDisabledIcon = new JBLabel(
                    JBColor.isBright()
                            ? CxIcons.WELCOME_MCP_DISABLE_LIGHT
                            : CxIcons.WELCOME_MCP_DISABLE_DARK
            );
            mcpDisabledIcon.setHorizontalAlignment(SwingConstants.CENTER);
            mcpDisabledIcon.setToolTipText("Checkmarx MCP is not enabled for this tenant.");
            bulletsPanel.add(mcpDisabledIcon, "growx, wrap");
        }
        return bulletsPanel;
    }

    private JComponent createRightImagePanel() {
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(JBUI.Borders.empty(20));
        Icon original = JBColor.isBright() ? CxIcons.WELCOME_SCANNER_LIGHT : CxIcons.WELCOME_SCANNER_DARK;
        rightPanelOriginalW = original.getIconWidth();
        rightPanelOriginalH = original.getIconHeight();
        Image buf = ImageUtil.createImage(rightPanelOriginalW, rightPanelOriginalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) buf.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        original.paintIcon(null, g2, 0, 0);
        g2.dispose();
        rightPanelOriginalImage = buf; // store original buffer for scaling
        JBLabel imageLabel = new JBLabel();
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        rightPanel.add(imageLabel, BorderLayout.NORTH);

        ComponentAdapter adapter = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension size = rightPanel.getSize();
                if (size.width <= 0 || size.height <= 0 || rightPanelOriginalImage == null) return;
                double ratio = Math.min(
                        Math.min((double) size.width / rightPanelOriginalW, (double) size.height / rightPanelOriginalH),
                        0.7 );
                int targetW = (int) Math.max(1, Math.round(rightPanelOriginalW * ratio));
                int targetH = (int) Math.max(1, Math.round(rightPanelOriginalH * ratio));
                Image scaled = rightPanelOriginalImage.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(scaled));
            }
        };
        rightPanel.addComponentListener(adapter);
        // Perform initial scaling once layout is ready
        SwingUtilities.invokeLater(() -> adapter.componentResized(null));
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
            southPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.border()));
        }
        return southPanel;
    }

    private void configureCheckboxBehavior() {
        if (realTimeScannersCheckbox == null) return;
        realTimeScannersCheckbox.addActionListener(e -> {
            settingsManager.setAll(realTimeScannersCheckbox.isSelected());
            refreshCheckboxState();
        });
    }

    /**
     * Ensures all real-time scanners are enabled by default when MCP is active,
     * unless they have already been explicitly configured.
     */
    private void initializeRealtimeState() {
        if (mcpEnabled && !settingsManager.areAllEnabled()) {
            settingsManager.setAll(true);
        }
    }

    private void refreshCheckboxState() {
        if (realTimeScannersCheckbox == null) return;
        realTimeScannersCheckbox.setSelected(settingsManager.areAllEnabled());
    }

    public JComponent createBullet(Resource res) { return createBullet(res, null); }

    public JComponent createBullet(Resource res, Color customColor) {
        JPanel panel = new JPanel(new MigLayout("insets 0, gapx 6, fillx", "[][grow, fill]"));
        panel.setOpaque(false);
        JBLabel glyph = new JBLabel("\u2022");
        glyph.setFont(new Font("Dialog", Font.BOLD, glyph.getFont().getSize()));
        JBLabel text = new JBLabel("<html><div style='width:" + WRAP_WIDTH + "px;display:inline-block;'>" + Bundle.message(res) + "</div></html>");
        if (customColor != null) {
            glyph.setForeground(customColor);
            text.setForeground(customColor);
        }
        panel.add(glyph, "top");
        panel.add(text, "growx");
        return panel;
    }

    /**
     * Abstracts real-time setting manipulation for testability.
     */
    public interface RealTimeSettingsManager {
        boolean areAllEnabled();
        void setAll(boolean enable);
    }

    /**
     * Default production implementation backed by {@link GlobalSettingsState}.
     */
    private static class DefaultRealTimeSettingsManager implements RealTimeSettingsManager {
        @Override
        public boolean areAllEnabled() {
            GlobalSettingsState s = GlobalSettingsState.getInstance();
            return s.isOssRealtime() && s.isSecretDetectionRealtime() && s.isContainersRealtime() && s.isIacRealtime();
        }
        @Override
        public void setAll(boolean enable) {
            GlobalSettingsState s = GlobalSettingsState.getInstance();
            s.setOssRealtime(enable);
            s.setSecretDetectionRealtime(enable);
            s.setContainersRealtime(enable);
            s.setIacRealtime(enable);
            GlobalSettingsState.getInstance().apply(s);
            ApplicationManager.getApplication().getMessageBus().syncPublisher(SettingsListener.SETTINGS_APPLIED).settingsApplied();
        }
    }
}
