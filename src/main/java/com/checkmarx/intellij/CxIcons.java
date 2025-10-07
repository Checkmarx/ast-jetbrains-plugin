package com.checkmarx.intellij;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Holds Checkmarx's custom icons.
 */
public final class CxIcons {

    private CxIcons() {
    }

    public static final Icon CHECKMARX_13 = IconLoader.getIcon("/icons/checkmarx-mono-13.png", CxIcons.class);
    public static final Icon CHECKMARX_13_COLOR = IconLoader.getIcon("/icons/checkmarx-13.png", CxIcons.class);
    public static final Icon CHECKMARX_80 = IconLoader.getIcon("/icons/checkmarx-80.png", CxIcons.class);
    public static final Icon CRITICAL = IconLoader.getIcon("/icons/critical.svg", CxIcons.class);
    public static final Icon HIGH = IconLoader.getIcon("/icons/high.svg", CxIcons.class);
    public static final Icon MEDIUM = IconLoader.getIcon("/icons/medium.svg", CxIcons.class);
    public static final Icon LOW = IconLoader.getIcon("/icons/low.svg", CxIcons.class);
    public static final Icon INFO = IconLoader.getIcon("/icons/info.svg", CxIcons.class);
    public static final Icon COMMENT = IconLoader.getIcon("/icons/comment.svg", CxIcons.class);
    public static final Icon STATE = IconLoader.getIcon("/icons/Flags.svg", CxIcons.class);
    public static final Icon ABOUT = IconLoader.getIcon("/icons/about.svg", CxIcons.class);
    public static final Icon WELCOME_SCANNER = IconLoader.getIcon("/icons/welcomePageScanner.svg", CxIcons.class);
    public static final Icon WELCOME_DOUBLE_CHECK = IconLoader.getIcon("/icons/double-check.svg", CxIcons.class);
    public static final Icon WELCOME_MCP_DISABLE = IconLoader.getIcon("/icons/cxAIError.svg", CxIcons.class);
    public static final Icon WELCOME_CHECK = IconLoader.getIcon("/icons/tabler-icon-check.svg", CxIcons.class);
    public static final Icon WELCOME_UNCHECK = IconLoader.getIcon("/icons/tabler-icon-uncheck.svg", CxIcons.class);
}
