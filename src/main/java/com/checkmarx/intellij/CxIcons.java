package com.checkmarx.intellij;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Holds Checkmarx's custom icons.
 */
public final class CxIcons {

    private CxIcons() {
    }

    public static final Icon CHECKMARX_13 = IconLoader.getIcon("/icons/checkmarx-mono-13.svg", CxIcons.class);
    public static final Icon CHECKMARX_80 = IconLoader.getIcon("/icons/checkmarx-80.svg", CxIcons.class);
}
