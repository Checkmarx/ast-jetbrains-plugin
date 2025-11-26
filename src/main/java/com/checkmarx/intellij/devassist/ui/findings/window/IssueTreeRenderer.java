package com.checkmarx.intellij.devassist.ui.findings.window;

import com.checkmarx.intellij.*;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders the vulnerability tree.
 * Extends {@link ColoredTreeCellRenderer} to provide custom rendering logic.
 * Handles mouse hover events to show an intention bulb icon.
 * Displays severity icons with counts next to file names.
 * Uses vulnerability severity to determine which icons to display.
 *
 */
public class IssueTreeRenderer extends ColoredTreeCellRenderer {

    private static final Logger LOGGER = Utils.getLogger(IssueTreeRenderer.class);

    private int hoveredRow = -1;
    private final Icon bulbIcon = AllIcons.Actions.IntentionBulb;
    private int currentRow = -1;
    private final List<IconWithCount> severityIconsToDraw = new ArrayList<>();
    private String fileNameText = "";

    private final Map<String, Icon> vulnerabilityToIcon;
    private final Map<String, Icon> vulnerabilityCountToIcon;

    public IssueTreeRenderer(JTree tree, Map<String, Icon> vulnerabilityToIcon, Map<String, Icon> vulnerabilityCountToIcon) {
        this.vulnerabilityToIcon = vulnerabilityToIcon;
        this.vulnerabilityCountToIcon = vulnerabilityCountToIcon;

        tree.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = tree.getRowForLocation(e.getX(), e.getY());
                if (row != hoveredRow) {
                    hoveredRow = row;
                    tree.repaint();
                }
            }
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = tree.getRowForLocation(e.getX(), e.getY());
                if (row == -1) {
                    tree.clearSelection();
                }
            }
        });
    }


    /**
     * Customizes the cell renderer for the vulnerability tree.
     * @param tree the tree instance
     * @param value the node value
     * @param selected whether the node is selected
     * @param expanded whether the node is expanded
     * @param leaf whether the node is a leaf
     * @param row the row index
     * @param hasFocus whether the tree has focus
     */
    @Override
    public void customizeCellRenderer(JTree tree, Object value, boolean selected,
                                      boolean expanded, boolean leaf, int row, boolean hasFocus) {
        currentRow = row;
        severityIconsToDraw.clear();
        fileNameText = "";

        if (!(value instanceof DefaultMutableTreeNode))
            return;

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        Object obj = node.getUserObject();
        Icon icon = null;
        LOGGER.debug("Rendering the result tree");
        if (obj instanceof CxFindingsWindow.FileNodeLabel) {
            CxFindingsWindow.FileNodeLabel info = (CxFindingsWindow.FileNodeLabel) obj;
            if (info.icon != null) {
                setIcon(info.icon);
            }
            fileNameText = info.fileName;
            append(info.fileName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            if (info.problemCount != null && !info.problemCount.isEmpty()) {
                append("  ", SimpleTextAttributes.GRAY_ATTRIBUTES);
                for (Map.Entry<String, Long> entry : info.problemCount.entrySet()) {
                    Long count = entry.getValue();
                    if (count != null && count > 0) {
                        Icon severityIcon = vulnerabilityCountToIcon.get(entry.getKey());
                        if (severityIcon != null) {
                            severityIconsToDraw.add(new IconWithCount(severityIcon, count));
                        }
                    }
                }
            }
        } else if (obj instanceof CxFindingsWindow.ScanDetailWithPath) {
            ScanIssue detail = ((CxFindingsWindow.ScanDetailWithPath) obj).detail;

            icon = vulnerabilityToIcon.getOrDefault(detail.getSeverity(), null);
            if (icon != null)
                setIcon(icon);

            switch (detail.getScanEngine()) {
                case ASCA:
                    append(detail.getTitle() + " ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    break;
                case OSS:
                    append(detail.getSeverity() + "-risk package: " + detail.getTitle() + "@"
                            + detail.getPackageVersion(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    break;
                case CONTAINERS:
                    append(detail.getSeverity() + "-risk container image: " + detail.getTitle() + ":"
                            + detail.getImageTag(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    break;
                default:
                    append(detail.getDescription(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    break;
            }
            append(" " + Constants.CXONE_ASSIST + " ", SimpleTextAttributes.GRAYED_ATTRIBUTES);

            if (detail.getLocations() != null && !detail.getLocations().isEmpty()) {
                var targetLoc = detail.getLocations().get(0);
                int line = targetLoc.getLine();
                Integer column = Math.max(0, targetLoc.getStartIndex());
                String lineColText = "[Ln " + line;
                if (column != null) {
                    lineColText += ", Col " + column;
                }
                lineColText += "]";
                append(lineColText, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
            if (hoveredRow == row) {
                icon = bulbIcon; // show bulb on hover
                setIcon(icon);
            }
        } else if (obj instanceof String) {
            setIcon(null);
            append((String) obj, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
    }

    /**
     *  Paints the component with custom graphics.
     * @param g the <code>Graphics</code> object to protect
     */

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (hoveredRow == currentRow) {
            Graphics2D g2d = (Graphics2D) g.create();
            try {
                g2d.setColor(new Color(211, 211, 211, 40));
                g2d.fillRect(0, 0, getWidth(), getHeight());
            } finally {
                g2d.dispose();
            }
        }
        if (!severityIconsToDraw.isEmpty() && !fileNameText.isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                FontMetrics fm = getFontMetrics(getFont());

                int x = getIpad().left;
                if (getIcon() != null) {
                    x += getIcon().getIconWidth() + getIconTextGap();
                }
                x += fm.stringWidth("__");
                x += fm.stringWidth(fileNameText);
                x += fm.stringWidth("  ");

                int y = (getHeight() - 16) / 2;

                int iconCountSpacing = 5;
                int iconNumberSpacing = 1;

                for (IconWithCount iconWithCount : severityIconsToDraw) {
                    iconWithCount.icon.paintIcon(this, g2, x, y);

                    x += iconWithCount.icon.getIconWidth() + iconNumberSpacing;

                    String countStr = iconWithCount.count.toString();
                    int countWidth = fm.stringWidth(countStr);
                    int countY = y + (iconWithCount.icon.getIconHeight() + fm.getAscent()) / 2 - 2;

                    g2.setColor(new JBColor(Gray._10, Gray._190));
                    g2.setFont(getFont().deriveFont(Font.BOLD));
                    g2.drawString(countStr, x, countY);

                    x += countWidth + iconCountSpacing;
                }

            } finally {
                g2.dispose();
            }
        }
    }


    /**
     * Helper class to hold an icon and its associated count.
     * Used for rendering severity icons with counts.
     */

    private static class IconWithCount {
        final Icon icon;
        final Long count;

        IconWithCount(Icon icon, Long count) {
            this.icon = icon;
            this.count = count;
        }
    }
}

