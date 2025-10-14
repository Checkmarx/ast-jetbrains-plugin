package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.components.CxLinkLabel;
import com.checkmarx.intellij.settings.SettingsComponent;
import com.checkmarx.intellij.settings.SettingsListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.Disposable;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * UI component shown under Tools > Checkmarx One > CxOne Assist.
 * Provides realtime feature toggles and container management tool selection.
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

    // Containers management tool
    private final JBLabel containersToolTitle = new JBLabel(Bundle.message(Resource.CONTAINERS_TOOL_TITLE));
    private final JComboBox<String> containersToolCombo = new JComboBox<>(new String[]{"docker", "podman"});

    private GlobalSettingsState state;

    private final MessageBusConnection connection;

    public CxOneAssistComponent() {
        buildUI();
        reset();
        // Subscribe to global settings applied events so UI reflects external changes (e.g. auto-enable after MCP)
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
        Dimension labelWidth = containersLabel.getPreferredSize();
        containersToolCombo.setPreferredSize(new Dimension(labelWidth.width, containersToolCombo.getPreferredSize().height));
        mainPanel.add(containersToolCombo, "wrap, gapleft 15");

        // MCP Section
        mainPanel.add(new JBLabel(formatTitle(Bundle.message(Resource.MCP_SECTION_TITLE))), "split 2, span, gaptop 10");
        mainPanel.add(new JSeparator(), "growx, wrap");
        mainPanel.add(new JBLabel(Bundle.message(Resource.MCP_DESCRIPTION)), "wrap, gapleft 15");
        CxLinkLabel installMcpLink = new CxLinkLabel(Bundle.message(Resource.MCP_INSTALL_LINK), e -> {
            // TODO: Add action to install MCP
        });
        mainPanel.add(installMcpLink, "wrap, gapleft 15");
        CxLinkLabel editJsonLink = new CxLinkLabel(Bundle.message(Resource.MCP_EDIT_JSON_LINK), e -> {
            // TODO: Add action to edit settings.json
        });
        mainPanel.add(editJsonLink, "wrap, gapleft 15");
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
        // Notify listeners (e.g., RealtimeScannerManager)
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
