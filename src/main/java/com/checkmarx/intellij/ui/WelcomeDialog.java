package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.*;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.service.AscaService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Welcome dialog for Checkmarx IntelliJ plugin.
 * Mirrors VS Code welcome webview: feature card with toggle for realtime scanners
 * and MCP status bullet.
 */
public class WelcomeDialog extends DialogWrapper {

    private static final Logger LOG = Utils.getLogger(WelcomeDialog.class);

    private final boolean mcpEnabled;
    private JLabel toggleIconLabel;
    private JBLabel statusAccessibleLabel; // hidden textual status (accessibility aid)

    public WelcomeDialog(@Nullable Project project, boolean mcpEnabled) {
        super(project, false);
        this.mcpEnabled = mcpEnabled;
        setTitle(Bundle.message(Resource.WELCOME_TITLE));
        setOKButtonText(Bundle.message(Resource.WELCOME_MARK_DONE));
        init();
        getRootPane().setPreferredSize(new Dimension(800, 500));
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(JBUI.scale(20), 0));

        JPanel leftPanel = new JPanel(new MigLayout("fillx, wrap 1"));
        leftPanel.setBorder(JBUI.Borders.empty(20, 20, 20, 0));

        // Title
        JBLabel title = new JBLabel(Bundle.message(Resource.WELCOME_TITLE));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        leftPanel.add(title, "gapbottom 10");

        // Subtitle (wrapped)
        JBLabel subtitle = new JBLabel("<html><div style='width: 350px;'>" + Bundle.message(Resource.WELCOME_SUBTITLE) + "</div></html>");
        subtitle.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        leftPanel.add(subtitle, "gapbottom 20, wrap");

        // Feature card replicating VS Code design
        JPanel featureCard = new JPanel(new MigLayout("insets 12, gapx 8, wrap 1", "[grow]"));
        featureCard.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        featureCard.setBackground(JBColor.background());

        // Header with toggle icon + title horizontally
        JPanel header = new JPanel(new MigLayout("insets 0, gapx 8", "[][grow]")); // icon + title
        header.setOpaque(false);

        toggleIconLabel = new JLabel();
        toggleIconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleIconLabel.setToolTipText("Toggle all real-time scanners");

        statusAccessibleLabel = new JBLabel("");
        statusAccessibleLabel.setVisible(false); // purely informational

        JBLabel assistTitle = new JBLabel(Bundle.message(Resource.WELCOME_ASSIST_TITLE));
        assistTitle.setFont(assistTitle.getFont().deriveFont(Font.BOLD));

        header.add(toggleIconLabel);
        header.add(assistTitle, "growx, pushx");
        featureCard.add(header, "growx");

        // Bulleted list inside card
        JPanel bulletsPanel = new JPanel(new MigLayout("insets 0, wrap 1", "[grow]"));
        bulletsPanel.setOpaque(false);
        bulletsPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_ASSIST_FEATURE_1)));
        bulletsPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_ASSIST_FEATURE_2)));
        bulletsPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_ASSIST_FEATURE_3)));
        if (mcpEnabled) {
            JBLabel mcpInstalled = new JBLabel("• " + Bundle.message(Resource.WELCOME_MCP_INSTALLED_INFO));
            mcpInstalled.setForeground(JBColor.GREEN);
            bulletsPanel.add(mcpInstalled);
        }else {
            // Show disabled MCP image (icon only)
            JBLabel mcpDisabledIcon = new JBLabel(CxIcons.WELCOME_MCP_DISABLE);
            mcpDisabledIcon.setToolTipText("Checkmarx MCP is not enabled for this tenant.");
            bulletsPanel.add(mcpDisabledIcon);
        }
        featureCard.add(bulletsPanel, "growx");
        featureCard.add(statusAccessibleLabel);

        leftPanel.add(featureCard, "growx, wrap, gapbottom 20");

        // Main features list below card
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_1)), "gapbottom 5");
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_2)), "gapbottom 5");
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_3)), "gapbottom 5");
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_4)), "gapbottom 20");

        // Initialize state + icon
        initializeRealtimeState();
        configureToggleBehavior();
        refreshToggleIcon();

        centerPanel.add(leftPanel, BorderLayout.CENTER);

        // Right panel with scanner image
        JBLabel imageLabel = new JBLabel(CxIcons.WELCOME_SCANNER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(imageLabel, BorderLayout.CENTER);
        rightPanel.setBorder(JBUI.Borders.empty(20));
        centerPanel.add(rightPanel, BorderLayout.EAST);

        return centerPanel;
    }

    private void configureToggleBehavior() {
        toggleIconLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GlobalSettingsState state = GlobalSettingsState.getInstance();
                boolean allEnabled = areAllRealtimeEnabled(state);
                setAllRealtime(!allEnabled);
                refreshToggleIcon();
            }
        });
    }

    private void initializeRealtimeState() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        boolean allEnabled = areAllRealtimeEnabled(state);
        if (mcpEnabled && !allEnabled) {
            setAllRealtime(true);
        }
    }

    private void refreshToggleIcon() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        boolean allEnabled = areAllRealtimeEnabled(state);
        toggleIconLabel.setIcon(allEnabled ? CxIcons.WELCOME_CHECK : CxIcons.WELCOME_UNCHECK);
        toggleIconLabel.setToolTipText(allEnabled
                ? "Real-time scanners are currently enabled. Click to disable all scanners."
                : "Real-time scanners are currently disabled. Click to enable all scanners.");
        statusAccessibleLabel.setText(allEnabled ? "Scanners enabled" : "Scanners disabled");
    }

    private static boolean areAllRealtimeEnabled(GlobalSettingsState s) {
        return s.isOssRealtime() && s.isSecretDetectionRealtime() && s.isContainersRealtime() && s.isIacRealtime() && s.isAsca();
    }

    private void setAllRealtime(boolean enable) {
        GlobalSettingsState s = GlobalSettingsState.getInstance();
        boolean prevAsca = s.isAsca();
        s.setOssRealtime(enable);
        s.setSecretDetectionRealtime(enable);
        s.setContainersRealtime(enable);
        s.setIacRealtime(enable);
        s.setAsca(enable);
        GlobalSettingsState.getInstance().apply(s);

        if (enable && !prevAsca && s.isAsca()) {
            // Attempt ASCA installation asynchronously (best effort)
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    try {
                        new AscaService().installAsca();
                    } catch (Exception ex) {
                        LOG.warn("ASCA auto-install from WelcomeDialog failed", ex);
                    }
                    return null;
                }
            }.execute();
        }
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
}
