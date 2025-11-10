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

    public static Icon getWelcomeScannerIcon() {return IconLoader.getIcon("/icons/welcomePageScanner.svg", CxIcons.class);}
    public static Icon getWelcomeMcpDisableIcon() {return IconLoader.getIcon("/icons/cxAIError.svg", CxIcons.class);}
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

    public static final Icon GUTTER_MALICIOUS = IconLoader.getIcon("/icons/devassist/malicious.svg", CxIcons.class);
    public static final Icon GUTTER_CRITICAL = IconLoader.getIcon("/icons/devassist/critical_severity.svg", CxIcons.class);
    public static final Icon GUTTER_HIGH = IconLoader.getIcon("/icons/devassist/high_severity.svg", CxIcons.class);
    public static final Icon GUTTER_MEDIUM = IconLoader.getIcon("/icons/devassist/medium_severity.svg", CxIcons.class);
    public static final Icon GUTTER_LOW = IconLoader.getIcon("/icons/devassist/low_severity.svg", CxIcons.class);
    public static final Icon GUTTER_SHIELD_QUESTION = IconLoader.getIcon("/icons/devassist/question_mark.svg", CxIcons.class);
    public static final Icon GUTTER_GREEN_SHIELD_CHECK = IconLoader.getIcon("/icons/devassist/green_check.svg", CxIcons.class);
}
