package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.intellij.ide.BrowserUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Promotional panel displayed in the "Scan Results" tab when the user
 * does not have the One Assist license but has the Dev Assist license.
 * Shows an upsell message encouraging upgrade to full Checkmarx platform.
 */
public class ScanResultsUpsellPanel extends JPanel {

    private static final String LEARN_MORE_URL = "https://docs.checkmarx.com/en/34965-68736-using-the-checkmarx-one-jetbrains-plugin.html";

    // Button styling per Figma: 400x32px, #0081E1 blue, 8px radius, white text
    private static final JBColor BTN_BG = new JBColor(0x0081E1, 0x0081E1);
    private static final int BTN_WIDTH = 400, BTN_HEIGHT = 32, BTN_RADIUS = 8;

    public ScanResultsUpsellPanel() {
        super(new MigLayout("insets 20, wrap 1, alignx center, aligny center", "[center]"));

        // Title - Inter Bold 15px (uses default theme colors)
        add(styledLabel(Bundle.message(Resource.UPSELL_SCAN_RESULTS_TITLE), Font.BOLD, 15, null), "gapbottom 8");

        // Description - Inter Regular 13px, gray color (#606572 light / #ADADAD dark), line break after first sentence
        String desc = Bundle.message(Resource.UPSELL_SCAN_RESULTS_DESCRIPTION).replaceFirst("\\. ", ".<br>");
        add(styledLabel("<html><div style='text-align:center'>" + desc + "</div></html>", Font.PLAIN, 13,
                new JBColor(0x606572, 0xADADAD)), "gapbottom 12");

        // Button - 400x32px, blue background, 8px rounded corners, opens docs URL
        add(createButton(), "width " + JBUI.scale(BTN_WIDTH) + "!, height " + JBUI.scale(BTN_HEIGHT) + "!");
    }

    /** Centers a label horizontally. */
    private JBLabel centered(JBLabel label) {
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    /** Creates a styled, centered label with Inter font. */
    private JBLabel styledLabel(String text, int style, int size, JBColor color) {
        JBLabel label = centered(new JBLabel(text));
        label.setFont(new Font("Inter", style, JBUI.scale(size)));
        if (color != null) label.setForeground(color);
        return label;
    }

    /** Creates the "Learn More" button with custom blue rounded styling per Figma. */
    private JButton createButton() {
        JButton btn = new JButton(Bundle.message(Resource.UPSELL_SCAN_RESULTS_BUTTON)) {
            @Override
            protected void paintComponent(Graphics g) {
                // Draw rounded blue background
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BTN_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), JBUI.scale(BTN_RADIUS) * 2, JBUI.scale(BTN_RADIUS) * 2);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Inter", Font.BOLD, JBUI.scale(13)));
        btn.setForeground(new JBColor(0xFFFFFF, 0xFFFFFF)); // White text
        btn.setContentAreaFilled(false);  // Disable default background
        btn.setBorderPainted(false);      // No border
        btn.setFocusPainted(false);       // No focus ring
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> BrowserUtil.browse(LEARN_MORE_URL));
        return btn;
    }
}
