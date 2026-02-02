package com.checkmarx.intellij.common.window;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Promotional panel displayed in the "Checkmarx One Assist Findings" tab when the user
 * does not have the Dev Assist license. Shows a promotional message encouraging
 * the user to contact their admin for Dev Assist features.
 */
public class DevAssistPromotionalPanel extends JPanel {

    // Row constraints: [image]0px[title]3px[description]32px[contact]push
    private static final String ROW_CONSTRAINTS = "[shrink 100]0[]3[]32[]push";

    public DevAssistPromotionalPanel() {
        super(new MigLayout("fill, insets 10 15 10 15, wrap 1", "[center, grow]", ROW_CONSTRAINTS));

        // Image - gradient cube icon
        add(centered(new JBLabel(CommonPanels.loadGradientCubeIcon())), "growx");

        // Title - Inter Bold 15px (uses default theme colors)
        add(styledLabel(Bundle.message(Resource.UPSELL_DEV_ASSIST_TITLE), Font.BOLD, 15, null), "growx");

        // Description - Inter Regular 13px with line break after "instantly and"
        String desc = Bundle.message(Resource.UPSELL_DEV_ASSIST_DESCRIPTION).replace("instantly and ", "instantly and<br>");
        add(styledLabel("<html><div style='text-align:center'>" + desc + "</div></html>", Font.PLAIN, 13, null), "growx, wmin 100");

        // Contact text - Inter Bold 13px, gray color (#787C87)
        add(styledLabel(Bundle.message(Resource.UPSELL_DEV_ASSIST_CONTACT), Font.BOLD, 13, new JBColor(0x787C87, 0x787C87)), "growx");
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
}
