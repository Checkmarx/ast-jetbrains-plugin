package com.checkmarx.intellij.common.window;

import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.resources.Bundle;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Promotional panel displayed in the "Checkmarx One Assist Findings" tab split view
 * when the user has at least one license (One Assist OR Dev Assist).
 * Shows an informational message encouraging the user to explore their security findings.
 */
public class FindingsPromotionalPanel extends JPanel {

    private int ignoredVulnerabilitiesCount = 0;
    private Runnable onLinkClickAction;

    public FindingsPromotionalPanel() {
        // Compact layout: small insets, minimal gaps between text elements
        // Row constraints: image can shrink, text rows are fixed, extra space goes to bottom
        super(new MigLayout("fill, insets 10 15 10 15, wrap 1", "[center, grow]", "[shrink 100]3[]3[]push"));
        buildUI();
    }

    public FindingsPromotionalPanel(int ignoredCount, Runnable onLinkClick) {
        // Compact layout: small insets, minimal gaps between text elements
        super(new MigLayout("fill, insets 10 15 10 15, wrap 1", "[center, grow]", "[shrink 100]3[]3[]push"));
        this.ignoredVulnerabilitiesCount = ignoredCount;
        this.onLinkClickAction = onLinkClick;
        buildUI();
    }

    private void buildUI() {
        // Load regular promotional image for Findings panel
        JBLabel imageLabel = new JBLabel(CommonPanels.loadRegularCubeIcon());
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(imageLabel, "growx");

        // Description - wrapped text
        String descriptionText = Bundle.message(Resource.FINDINGS_PROMO_DESCRIPTION);
        JBLabel descriptionLabel = new JBLabel("<html><div style='text-align: left;'>"
                + descriptionText + "</div></html>");
        descriptionLabel.setForeground(UIUtil.getLabelForeground());
        descriptionLabel.setHorizontalAlignment(SwingConstants.LEFT);
        add(descriptionLabel, "growx, wmin 100");

        // Clickable link styled label - only show when there are ignored vulnerabilities
        if (ignoredVulnerabilitiesCount > 0) {
            String linkText = Bundle.message(Resource.FINDINGS_PROMO_LINK, ignoredVulnerabilitiesCount);
            JBLabel linkLabel = new JBLabel("<html><a style='color: #589DF6;'>" + linkText + "</a></html>");
            linkLabel.setHorizontalAlignment(SwingConstants.LEFT);
            linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            if (onLinkClickAction != null) {
                linkLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        onLinkClickAction.run();
                    }
                });
            }

            add(linkLabel, "growx");
        }
    }
}

