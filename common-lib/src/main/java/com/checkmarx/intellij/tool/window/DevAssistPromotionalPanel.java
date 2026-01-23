package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Promotional panel displayed in the "Checkmarx One Assist Findings" tab when the user
 * does not have the Dev Assist license. Shows a promotional message encouraging
 * the user to contact their admin for Dev Assist features.
 */
public class DevAssistPromotionalPanel extends JPanel {

    public DevAssistPromotionalPanel() {
        // Compact layout: small insets, minimal gaps between text elements
        // Row constraints: image can shrink, text rows are fixed, extra space goes to bottom
        super(new MigLayout("fill, insets 10 15 10 15, wrap 1", "[center, grow]", "[shrink 100]3[]3[]3[]push"));
        buildUI();
    }

    private void buildUI() {
        // Load promotional image using shared utility
        JBLabel imageLabel = new JBLabel(CommonPanels.loadCubeIcon());
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(imageLabel, "growx");

        // Title - compact font size
        JBLabel titleLabel = new JBLabel(Bundle.message(Resource.UPSELL_DEV_ASSIST_TITLE));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, "growx");

        // Description - wrapped text
        String descriptionText = Bundle.message(Resource.UPSELL_DEV_ASSIST_DESCRIPTION);
        JBLabel descriptionLabel = new JBLabel("<html><div style='text-align: center;'>"
                + descriptionText + "</div></html>");
        descriptionLabel.setForeground(UIUtil.getLabelForeground());
        descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(descriptionLabel, "growx, wmin 100");

        // Contact admin message
        JBLabel contactLabel = new JBLabel(Bundle.message(Resource.UPSELL_DEV_ASSIST_CONTACT));
        contactLabel.setForeground(UIUtil.getLabelDisabledForeground());
        contactLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(contactLabel, "growx");
    }
}

