package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.*;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.settings.SettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;
import lombok.Getter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * Welcome dialog displayed after successful authentication.
 * <p>
 * Responsibilities:
 *  - Present plugin welcome title/subtitle.
 *  - Show feature bullet list (core + MCP specific when enabled).
 *  - Provide a single toggle that enables/disables all real-time scanners (only when MCP is enabled).
 * <p>
 * The dialog intentionally hides the native window title bar (handled externally) while keeping the internal bold title label.
 * This class focuses purely on UI assembly & toggle orchestration for OSS / Secrets / Containers / IaC real-time scanners.
 * <p>
 * Testability: real-time setting side effects are abstracted via {@link RealTimeSettingsManager} so
 * unit tests can inject a fake implementation without requiring the IntelliJ Application container.
 */
public class WelcomeDialog extends DialogWrapper {

    /** Preferred wrap width used for subtitle and bullet body text for a compact layout. */
    public static final int WRAP_WIDTH = 300;
    /** Approximate overall dialog preferred dimension (slightly narrower due to compact card). */
    private static final Dimension PREFERRED_DIALOG_SIZE = new Dimension(720, 460);
    /** Scale factor applied to the welcome scanner illustration to reduce visual dominance. */
    private static final double SCANNER_ICON_SCALE = 0.4;

    private final boolean mcpEnabled;
    private final RealTimeSettingsManager settingsManager;

    // UI references mostly for internal state updates & tests.
    @Getter private JLabel toggleIconLabel; // null when MCP disabled (hidden entirely) - Lombok generates getter.
    private JBLabel statusAccessibleLabel; // hidden textual status (accessibility aid)

    /**
     * Primary constructor used in production code; creates a default settings manager.
     */
    public WelcomeDialog(@Nullable Project project, boolean mcpEnabled) {
        this(project, mcpEnabled, new DefaultRealTimeSettingsManager());
    }

    /**
     * Visible-for-tests / advanced injection constructor.
     * @param project current project (nullable)
     * @param mcpEnabled whether MCP server feature flag is enabled
     * @param settingsManager abstraction for manipulating all realtime settings
     */
    public WelcomeDialog(@Nullable Project project, boolean mcpEnabled, RealTimeSettingsManager settingsManager) {
        super(project, false);
        this.mcpEnabled = mcpEnabled;
        this.settingsManager = settingsManager;
        setOKButtonText(Bundle.message(Resource.WELCOME_MARK_DONE));
        init();
        getRootPane().setPreferredSize(PREFERRED_DIALOG_SIZE);
    }

    /** Exposes current aggregate status text for tests (accessibility label contents). */
    public String getAggregateStatusText() { return statusAccessibleLabel != null ? statusAccessibleLabel.getText() : ""; }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(JBUI.scale(20), 0));

        JPanel leftPanel = new JPanel(new MigLayout("fillx, wrap 1"));
        leftPanel.setBorder(JBUI.Borders.empty(20, 20, 20, 0));

        // Internal title label
        JBLabel title = new JBLabel(Bundle.message(Resource.WELCOME_TITLE));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        leftPanel.add(title, "gapbottom 10");

        // Subtitle (wrapped) - use same foreground as other labels
        JBLabel subtitle = new JBLabel("<html><div style='width:" + WRAP_WIDTH + "px;'>" + Bundle.message(Resource.WELCOME_SUBTITLE) + "</div></html>");
        subtitle.setForeground(UIUtil.getLabelForeground());
        leftPanel.add(subtitle, "gapbottom 16, wrap");

        // Feature card replicating VS Code design
        JPanel featureCard = new JPanel(new MigLayout("insets 10, gapx 6, wrap 1", "[grow]"));
        featureCard.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        featureCard.setBackground(JBColor.background());

        // Header with optional toggle icon + assist title
        JPanel header = new JPanel(new MigLayout("insets 0, gapx 6", "[][grow]"));
        header.setOpaque(false);

        if (mcpEnabled) {
            toggleIconLabel = new JLabel();
            toggleIconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleIconLabel.setToolTipText("Toggle all real-time scanners");
            header.add(toggleIconLabel);
        } else {
            toggleIconLabel = null; // explicit for clarity
        }

        statusAccessibleLabel = new JBLabel("");
        statusAccessibleLabel.setVisible(false); // purely informational

        JBLabel assistTitle = new JBLabel(Bundle.message(Resource.WELCOME_ASSIST_TITLE));
        assistTitle.setFont(assistTitle.getFont().deriveFont(Font.BOLD));
        header.add(assistTitle, "growx, pushx");
        featureCard.add(header, "growx");

        // Bulleted list inside card
        JPanel bulletsPanel = new JPanel(new MigLayout("insets 0, wrap 1", "[grow]"));
        bulletsPanel.setOpaque(false);
        bulletsPanel.add(createBullet(Resource.WELCOME_ASSIST_FEATURE_1));
        bulletsPanel.add(createBullet(Resource.WELCOME_ASSIST_FEATURE_2));
        bulletsPanel.add(createBullet(Resource.WELCOME_ASSIST_FEATURE_3));
        if (mcpEnabled) {
            bulletsPanel.add(createBullet(Resource.WELCOME_MCP_INSTALLED_INFO, JBColor.GREEN));
        } else {
            JBLabel mcpDisabledIcon = new JBLabel(CxIcons.WELCOME_MCP_DISABLE);
            mcpDisabledIcon.setToolTipText("Checkmarx MCP is not enabled for this tenant.");
            bulletsPanel.add(mcpDisabledIcon);
        }
        featureCard.add(bulletsPanel, "growx");
        featureCard.add(statusAccessibleLabel);
        leftPanel.add(featureCard, "growx, wrap, gapbottom 16");

        // Main features list below card
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_1), "gapbottom 4");
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_2), "gapbottom 4");
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_3), "gapbottom 4");
        leftPanel.add(createBullet(Resource.WELCOME_MAIN_FEATURE_4), "gapbottom 12");

        // Initialize + configure toggle (only when visible)
        initializeRealtimeState();
        if (mcpEnabled) {
            configureToggleBehavior();
            refreshToggleIcon();
        }

        centerPanel.add(leftPanel, BorderLayout.CENTER);
        centerPanel.add(buildRightImagePanel(), BorderLayout.EAST);
        return centerPanel;
    }

    /** Right side scaled illustration panel. */
    private JComponent buildRightImagePanel() {
        Icon original = CxIcons.WELCOME_SCANNER;
        int ow = original.getIconWidth();
        int oh = original.getIconHeight();
        int tw = (int) Math.max(1, Math.round(ow * SCANNER_ICON_SCALE));
        int th = (int) Math.max(1, Math.round(oh * SCANNER_ICON_SCALE));

        Image buf = UIUtil.createImage(ow, oh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) buf.getGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // ensure smooth edges
        original.paintIcon(null, g2, 0, 0);
        g2.dispose();
        Image scaled = buf.getScaledInstance(tw, th, Image.SCALE_SMOOTH);
        JBLabel imageLabel = new JBLabel(new ImageIcon(scaled));
        imageLabel.setPreferredSize(new Dimension(tw, th));

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(imageLabel, BorderLayout.CENTER);
        rightPanel.setBorder(JBUI.Borders.empty(20));
        return rightPanel;
    }

    /** Attach click handler for toggle icon. */
    private void configureToggleBehavior() {
        if (toggleIconLabel == null) return; // hidden when MCP disabled
        toggleIconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean allEnabled = settingsManager.areAllEnabled();
                settingsManager.setAll(!allEnabled);
                refreshToggleIcon();
            }
        });
    }

    /** Ensures all realtime scanners start enabled when MCP active (unless already all enabled). */
    private void initializeRealtimeState() {
        if (mcpEnabled && !settingsManager.areAllEnabled()) {
            settingsManager.setAll(true);
        }
    }

    /** Update icon + tooltip + accessibility label to reflect current aggregate realtime status. */
    private void refreshToggleIcon() {
        if (toggleIconLabel == null) return; // nothing to refresh when hidden
        boolean allEnabled = settingsManager.areAllEnabled();
        toggleIconLabel.setIcon(allEnabled ? CxIcons.WELCOME_CHECK : CxIcons.WELCOME_UNCHECK);
        toggleIconLabel.setToolTipText(allEnabled
                ? "Real-time scanners are currently enabled. Click to disable all scanners."
                : "Real-time scanners are currently disabled. Click to enable all scanners.");
        statusAccessibleLabel.setText(allEnabled ? "Scanners enabled" : "Scanners disabled");
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel southPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton markDoneButton = new JButton(Bundle.message(Resource.WELCOME_MARK_DONE));
        markDoneButton.setIcon(CxIcons.WELCOME_DOUBLE_CHECK);
        markDoneButton.addActionListener(e -> close(OK_EXIT_CODE));
        southPanel.add(markDoneButton);
        southPanel.setBorder(JBUI.Borders.empty(0, 10, 10, 10));
        return southPanel;
    }

    /** Convenience overload using default foreground color. */
    public JComponent createBullet(Resource res) { return createBullet(res, null); }

    /** Public for tests: creates a two-column panel with a Unicode bullet + wrapped text. */
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

    /** Strategy interface abstracting realtime setting manipulation for testability. */
    public interface RealTimeSettingsManager {
        boolean areAllEnabled();
        void setAll(boolean enable);
    }

    /** Default production implementation backed by {@link GlobalSettingsState}. */
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
            ApplicationManager.getApplication().getMessageBus()
                    .syncPublisher(SettingsListener.SETTINGS_APPLIED)
                    .settingsApplied();
        }
    }
}
