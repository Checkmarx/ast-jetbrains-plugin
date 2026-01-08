package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.intellij.ide.BrowserUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Promotional panel displayed in the "Scan Results" tab when the user
 * does not have the One Assist license but has the Dev Assist license.
 * Shows an upsell message encouraging upgrade to full Checkmarx platform.
 */
public class ScanResultsUpsellPanel extends JPanel {

    private static final String LEARN_MORE_URL = "https://checkmarx.com/product/application-security-platform/";

    public ScanResultsUpsellPanel() {
        super(new MigLayout("fill, insets 20, wrap 1", "[center]", "[center]"));
        buildUI();
    }

    private void buildUI() {
        // Title
        JBLabel titleLabel = new JBLabel(Bundle.message(Resource.UPSELL_SCAN_RESULTS_TITLE));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(titleLabel, "growx, gapbottom 15");

        // Description - wrapped text
        String descriptionText = Bundle.message(Resource.UPSELL_SCAN_RESULTS_DESCRIPTION);
        JBLabel descriptionLabel = new JBLabel("<html><div style='text-align: center; width: 300px;'>" 
                + descriptionText + "</div></html>");
        descriptionLabel.setForeground(UIUtil.getLabelForeground());
        descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(descriptionLabel, "growx, gapbottom 20");

        // Learn More button
        JButton learnMoreButton = new JButton(Bundle.message(Resource.UPSELL_SCAN_RESULTS_BUTTON));
        learnMoreButton.setPreferredSize(new Dimension(200, 35));
        learnMoreButton.addActionListener(e -> BrowserUtil.browse(LEARN_MORE_URL));
        add(learnMoreButton, "growx, gapbottom 10");
    }
}

