package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;

/**
 * Promotional panel displayed in the "Checkmarx One Assist Findings" tab split view
 * when the user has at least one license (One Assist OR Dev Assist).
 * Shows an informational message encouraging the user to explore their security findings.
 */
public class FindingsPromotionalPanel extends JPanel {

    // Icon path - can be changed to SVG when design assets are ready
    private static final String CUBE_ICON_PATH = "/icons/cx-one-assist-cube.png";

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
        // Load promotional image at fixed size
        // TODO: Replace with SVG icon when design assets are ready
        Icon cubeIcon = loadPromotionalIcon();
        JBLabel imageLabel = new JBLabel(cubeIcon);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(imageLabel, "growx");

        // Description - wrapped text
        String descriptionText = Bundle.message(Resource.FINDINGS_PROMO_DESCRIPTION);
        JBLabel descriptionLabel = new JBLabel("<html><div style='text-align: center;'>"
                + descriptionText + "</div></html>");
        descriptionLabel.setForeground(UIUtil.getLabelForeground());
        descriptionLabel.setHorizontalAlignment(SwingConstants.CENTER);
        add(descriptionLabel, "growx, wmin 100");

        // Clickable link styled label
        String linkText = Bundle.message(Resource.FINDINGS_PROMO_LINK, ignoredVulnerabilitiesCount);
        JBLabel linkLabel = new JBLabel("<html><a style='color: #589DF6;'>" + linkText + "</a></html>");
        linkLabel.setHorizontalAlignment(SwingConstants.CENTER);
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

    /**
     * Loads the promotional icon. Currently loads PNG, but structured for easy
     * migration to SVG when design assets are available.
     *
     * @return the promotional icon, or null if not found
     */
    private Icon loadPromotionalIcon() {
        URL imageUrl = getClass().getResource(CUBE_ICON_PATH);
        if (imageUrl != null) {
            return new ImageIcon(imageUrl);
        }
        return null;
    }
}

