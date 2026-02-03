package com.checkmarx.intellij.ast.ui.layout;

import javax.swing.*;
import java.awt.*;

/**
 * A FlowLayout subclass that properly calculates preferred size when components wrap to multiple rows.
 * Standard FlowLayout returns preferred size based on a single row, causing clipping when components wrap.
 * This layout correctly reports the height needed for all wrapped rows.
 */
public class WrapLayout extends FlowLayout {

    /**
     * Creates a new WrapLayout with default alignment (LEFT) and gaps.
     */
    public WrapLayout() {
        super(LEFT);
    }

    /**
     * Creates a new WrapLayout with specified alignment.
     *
     * @param align the alignment value (LEFT, CENTER, RIGHT, LEADING, TRAILING)
     */
    public WrapLayout(int align) {
        super(align);
    }

    /**
     * Creates a new WrapLayout with specified alignment and gaps.
     *
     * @param align the alignment value
     * @param hgap  the horizontal gap between components
     * @param vgap  the vertical gap between components
     */
    public WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return calculateLayoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = calculateLayoutSize(target, false);
        minimum.width -= (getHgap() + 1);
        return minimum;
    }

    /**
     * Calculates the layout size by simulating component placement and tracking row wrapping.
     *
     * @param target    the container being laid out
     * @param preferred true for preferred size, false for minimum size
     * @return the calculated dimension
     */
    private Dimension calculateLayoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = getTargetWidth(target);
            if (targetWidth == 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (getHgap() * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension dim = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            int nmembers = target.getComponentCount();
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                    // Check if component fits in current row
                    if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                        // Start new row
                        addRow(dim, rowWidth, rowHeight);
                        rowWidth = 0;
                        rowHeight = 0;
                    }

                    // Add horizontal gap if not first component in row
                    if (rowWidth != 0) {
                        rowWidth += getHgap();
                    }

                    rowWidth += d.width;
                    rowHeight = Math.max(rowHeight, d.height);
                }
            }

            // Add last row
            addRow(dim, rowWidth, rowHeight);

            dim.width += horizontalInsetsAndGap;
            dim.height += insets.top + insets.bottom + getVgap() * 2;

            // Ensure we fit within scroll pane viewport if present
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid()) {
                dim.width = Math.min(dim.width, targetWidth);
            }

            return dim;
        }
    }

    /**
     * Gets the target width for layout calculations.
     * Uses the container's current width if available, otherwise uses parent's width.
     */
    private int getTargetWidth(Container target) {
        int width = target.getWidth();
        if (width == 0) {
            Container parent = target.getParent();
            if (parent != null) {
                width = parent.getWidth();
            }
        }
        return width;
    }

    /**
     * Adds a row's dimensions to the total layout size.
     */
    private void addRow(Dimension dim, int rowWidth, int rowHeight) {
        dim.width = Math.max(dim.width, rowWidth);
        if (dim.height > 0) {
            dim.height += getVgap();
        }
        dim.height += rowHeight;
    }
}

