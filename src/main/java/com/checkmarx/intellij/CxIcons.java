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
    public static final Icon COMMENT = IconLoader.getIcon("/icons/comment.svg", CxIcons.class);
    public static final Icon STATE = IconLoader.getIcon("/icons/Flags.svg", CxIcons.class);
    public static final Icon ABOUT = IconLoader.getIcon("/icons/about.svg", CxIcons.class);
    public static final Icon WELCOME_SCANNER_LIGHT = IconLoader.getIcon("/icons/welcomePageScanner_light.svg", CxIcons.class);
    public static final Icon WELCOME_SCANNER_DARK = IconLoader.getIcon("/icons/welcomePageScanner_dark.svg", CxIcons.class);
    public static final Icon WELCOME_MCP_DISABLE_LIGHT = IconLoader.getIcon("/icons/cxAIError_light.svg", CxIcons.class);
    public static final Icon WELCOME_MCP_DISABLE_DARK = IconLoader.getIcon("/icons/cxAIError_dark.svg", CxIcons.class);

    public static Icon getMaliciousIcon() {
        return IconLoader.getIcon("/icons/malicious.svg", CxIcons.class);
    }
    public static Icon getCriticalIcon() {
        return IconLoader.getIcon("/icons/critical.svg", CxIcons.class);
    }
    public static Icon getHighIcon() {
        return IconLoader.getIcon("/icons/high.svg", CxIcons.class);
    }
    public static Icon getMediumIcon() {
        return IconLoader.getIcon("/icons/medium.svg", CxIcons.class);
    }
    public static Icon getLowIcon() {
        return IconLoader.getIcon("/icons/low.svg", CxIcons.class);
    }
    public static Icon getInfoIcon() {
        return IconLoader.getIcon("/icons/info.svg", CxIcons.class);
    }

}
