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
import java.awt.image.BufferedImage;
import java.net.URL;
import javax.imageio.ImageIO;
import java.io.IOException;

/**
 * Utility class providing common UI panels used across tool windows.
 * Centralizes creation of auth panels and shared icon loading to reduce code duplication.
 */
public final class CommonPanels {

    private static final String GRADIENT_CUBE_ICON_PATH = "/icons/gradient_dark.png";
    private static final String REGULAR_CUBE_ICON_PATH = "/icons/regular_dark.png";
    
    // Target dimensions for the promotional images
    private static final int GRADIENT_ICON_MAX_WIDTH = 300;  // Full stretch for DevAssist panel
    private static final int REGULAR_ICON_MAX_WIDTH = 240;   // Larger size for Findings panel

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
     * Loads the gradient cube icon used in DevAssistPromotionalPanel.
     * This is a full-stretch promotional image with gradient styling.
     * Image is scaled to fit the panel while maintaining aspect ratio.
     *
     * @return the gradient cube icon scaled to appropriate size, or null if not found
     */
    public static Icon loadGradientCubeIcon() {
        return loadAndScaleIcon(GRADIENT_CUBE_ICON_PATH, GRADIENT_ICON_MAX_WIDTH);
    }

    /**
     * Loads the regular cube icon used in FindingsPromotionalPanel.
     * This is a compact promotional image with standard styling.
     * Image is scaled to fit the compact panel while maintaining aspect ratio.
     *
     * @return the regular cube icon scaled to appropriate size, or null if not found
     */
    public static Icon loadRegularCubeIcon() {
        return loadAndScaleIcon(REGULAR_CUBE_ICON_PATH, REGULAR_ICON_MAX_WIDTH);
    }

    /**
     * Loads an image from the given path and scales it to the specified max width
     * while maintaining aspect ratio.
     *
     * @param iconPath the resource path to the icon
     * @param maxWidth the maximum width to scale the image to
     * @return the scaled icon, or null if not found or error occurs
     */
    private static Icon loadAndScaleIcon(String iconPath, int maxWidth) {
        URL imageUrl = CommonPanels.class.getResource(iconPath);
        if (imageUrl == null) {
            return null;
        }
        
        try {
            BufferedImage originalImage = ImageIO.read(imageUrl);
            if (originalImage == null) {
                return new ImageIcon(imageUrl);
            }
            
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            // Calculate scaled dimensions maintaining aspect ratio
            if (originalWidth <= maxWidth) {
                return new ImageIcon(originalImage);
            }
            
            double scale = (double) maxWidth / originalWidth;
            int scaledWidth = maxWidth;
            int scaledHeight = (int) (originalHeight * scale);
            
            // Use high-quality scaling
            Image scaledImage = originalImage.getScaledInstance(
                    scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
            
            return new ImageIcon(scaledImage);
        } catch (IOException e) {
            // Fallback to unscaled image if scaling fails
            return new ImageIcon(imageUrl);
        }
    }
}
