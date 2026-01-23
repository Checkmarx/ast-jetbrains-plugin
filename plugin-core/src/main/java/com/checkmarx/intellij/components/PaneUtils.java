package com.checkmarx.intellij.components;

import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SideBorder;

import javax.swing.*;
import java.awt.*;

/**
 * Utils for drawing panes.
 */
public class PaneUtils {
    /**
     * Wrap a component in a {@link JScrollPane} without borders.
     *
     * @param component component to wrap
     * @return JScrollPane wrapping the component
     */
    public static JScrollPane inScrollPane(Component component) {
        return ScrollPaneFactory.createScrollPane(component, SideBorder.NONE);
    }

    /**
     * Wrap a component in a {@link JScrollPane} without borders that only scrolls vertically.
     *
     * @param component component to wrap
     * @return JScrollPane wrapping the component
     */
    public static JScrollPane inVerticalScrollPane(Component component) {
        JScrollPane scrollPane = inScrollPane(component);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return scrollPane;
    }
}
