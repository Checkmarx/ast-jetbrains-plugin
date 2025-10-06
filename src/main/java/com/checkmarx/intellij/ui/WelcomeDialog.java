package com.checkmarx.intellij.ui;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Resource;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class WelcomeDialog extends DialogWrapper {

    public WelcomeDialog(@Nullable Project project) {
        super(project, false);
        setTitle(Bundle.message(Resource.WELCOME_TITLE));
        setOKButtonText(Bundle.message(Resource.WELCOME_MARK_DONE));
        init();
        // Set size
        getRootPane().setPreferredSize(new Dimension(800, 500));
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(JBUI.scale(20), 0));

        // Left panel
        JPanel leftPanel = new JPanel(new MigLayout("fillx, wrap 1"));
        leftPanel.setBorder(JBUI.Borders.empty(20, 20, 20, 0));

        JBLabel title = new JBLabel(Bundle.message(Resource.WELCOME_TITLE));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        leftPanel.add(title, "gapbottom 10");

        JBLabel subtitle = new JBLabel("<html><div style='width: 350px;'>" + Bundle.message(Resource.WELCOME_SUBTITLE) + "</div></html>");
        subtitle.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
        leftPanel.add(subtitle, "gapbottom 20, wrap");

        // Feature card
        JPanel featureCard = new JPanel(new MigLayout("fillx, wrap 1, insets 15"));
        featureCard.setBorder(BorderFactory.createLineBorder(JBColor.border()));

        JBLabel assistTitle = new JBLabel(Bundle.message(Resource.WELCOME_ASSIST_TITLE));
        assistTitle.setFont(assistTitle.getFont().deriveFont(Font.BOLD));
        featureCard.add(assistTitle, "gapbottom 10");

        featureCard.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_ASSIST_FEATURE_1)));
        featureCard.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_ASSIST_FEATURE_2)));
        featureCard.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_ASSIST_FEATURE_3)));

        leftPanel.add(featureCard, "growx, wrap, gapbottom 20");

        // Main features
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_1)), "gapbottom 5");
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_2)), "gapbottom 5");
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_3)), "gapbottom 5");
        leftPanel.add(new JBLabel("• " + Bundle.message(Resource.WELCOME_MAIN_FEATURE_4)), "gapbottom 20");

        centerPanel.add(leftPanel, BorderLayout.CENTER);

        // Right panel
        JBLabel imageLabel = new JBLabel(CxIcons.WELCOME_SCANNER);
        imageLabel.setVerticalAlignment(SwingConstants.CENTER);
        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(imageLabel, BorderLayout.CENTER);
        rightPanel.setBorder(JBUI.Borders.empty(20));

        centerPanel.add(rightPanel, BorderLayout.EAST);

        return centerPanel;
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
