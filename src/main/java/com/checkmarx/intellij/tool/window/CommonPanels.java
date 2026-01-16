package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.settings.global.GlobalSettingsConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.net.URL;

/**
 * Utility class providing common UI panels used across tool windows.
 * Centralizes creation of auth panels and shared icon loading to reduce code duplication.
 */
public final class CommonPanels {

    private static final String CUBE_ICON_PATH = "/icons/cx-one-assist-cube.png";

    private CommonPanels() {
        // Utility class - no instantiation
    }

    /**
     * Creates a centered authentication panel with Checkmarx logo and "Open Settings" button.
     * Used when the user is not authenticated.
     *
     * @param project the current IntelliJ project
     * @return a JPanel containing the auth UI
     */
    public static JPanel createAuthPanel(Project project) {
        JPanel wrapper = new JPanel(new GridBagLayout());

        JPanel panel = new JPanel(new GridLayoutManager(2, 1, JBUI.emptyInsets(), -1, -1));

        GridConstraints constraints = new GridConstraints();
        constraints.setRow(0);
        panel.add(new JBLabel(CxIcons.CHECKMARX_80), constraints);

        JButton openSettingsButton = new JButton(Bundle.message(Resource.OPEN_SETTINGS_BUTTON));
        openSettingsButton.addActionListener(e ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, GlobalSettingsConfigurable.class));

        constraints = new GridConstraints();
        constraints.setRow(1);
        panel.add(openSettingsButton, constraints);

        wrapper.add(panel);
        return wrapper;
    }

    /**
     * Loads the promotional cube icon used in upsell/promotional panels.
     * Currently loads PNG, but structured for easy migration to SVG when design assets are available.
     *
     * @return the promotional cube icon, or null if not found
     */
    public static Icon loadCubeIcon() {
        URL imageUrl = CommonPanels.class.getResource(CUBE_ICON_PATH);
        if (imageUrl != null) {
            return new ImageIcon(imageUrl);
        }
        return null;
    }
}
