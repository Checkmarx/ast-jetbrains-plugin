package com.checkmarx.intellij;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * Holds Checkmarx's custom icons.
 */
public final class CxIcons {

    private CxIcons() {
    }

    public static final Icon CHECKMARX_80 = IconLoader.getIcon("/icons/checkmarx-80.png", CxIcons.class);
    public static final Icon COMMENT = IconLoader.getIcon("/icons/comment.svg", CxIcons.class);
    public static final Icon STATE = IconLoader.getIcon("/icons/Flags.svg", CxIcons.class);
    public static final Icon ABOUT = IconLoader.getIcon("/icons/about.svg", CxIcons.class);
    public static final Icon INFO = IconLoader.getIcon("/icons/info.svg", CxIcons.class);

    public static Icon getWelcomeScannerIcon() {return IconLoader.getIcon("/icons/welcomePageScanner.svg", CxIcons.class);}
    public static Icon getWelcomeMcpDisableIcon() {return IconLoader.getIcon("/icons/cxAIError.svg", CxIcons.class);}

    public static final Icon GUTTER_MALICIOUS = IconLoader.getIcon("/icons/devassist/malicious.svg", CxIcons.class);
    public static final Icon GUTTER_SHIELD_QUESTION = IconLoader.getIcon("/icons/devassist/question_mark.svg", CxIcons.class);
    public static final Icon STAR_ACTION = IconLoader.getIcon("/icons/devassist/star-action.svg", CxIcons.class);

    /**
     * Inner static final class, to maintain the constants used in icons for the value 24*24.
     */
    public static final class Regular{

        private Regular() {}

        public static final Icon MALICIOUS = IconLoader.getIcon("/icons/devassist/severity_24/malicious.svg", CxIcons.class);
        public static final Icon CRITICAL = IconLoader.getIcon("/icons/devassist/severity_24/critical.svg", CxIcons.class);
        public static final Icon HIGH = IconLoader.getIcon("/icons/devassist/severity_24/high.svg", CxIcons.class);
        public static final Icon MEDIUM = IconLoader.getIcon("/icons/devassist/severity_24/medium.svg", CxIcons.class);
        public static final Icon LOW = IconLoader.getIcon("/icons/devassist/severity_24/low.svg", CxIcons.class);
        public static final Icon IGNORED = IconLoader.getIcon("/icons/devassist/severity_24/ignored.svg", CxIcons.class);
        public static final Icon OK = IconLoader.getIcon("/icons/devassist/severity_24/ok.svg", CxIcons.class);

    }

    /**
     * Inner static final class, to maintain the constants used in icons for the value 20*20.
     */
    public static final class Medium{

        private Medium() {}

        public static final Icon MALICIOUS = IconLoader.getIcon("/icons/devassist/severity_20/malicious.svg", CxIcons.class);
        public static final Icon CRITICAL = IconLoader.getIcon("/icons/devassist/severity_20/critical.svg", CxIcons.class);
        public static final Icon HIGH = IconLoader.getIcon("/icons/devassist/severity_20/high.svg", CxIcons.class);
        public static final Icon MEDIUM = IconLoader.getIcon("/icons/devassist/severity_20/medium.svg", CxIcons.class);
        public static final Icon LOW = IconLoader.getIcon("/icons/devassist/severity_20/low.svg", CxIcons.class);
        public static final Icon IGNORED = IconLoader.getIcon("/icons/devassist/severity_20/ignored.svg", CxIcons.class);
        public static final Icon OK = IconLoader.getIcon("/icons/devassist/severity_20/ok.svg", CxIcons.class);

    }

    /**
     * Inner static final class, to maintain the constants used in icons for the value 16*16.
     */
    public static final class Small{

        private Small() {}

        public static final Icon MALICIOUS = IconLoader.getIcon("/icons/devassist/malicious.svg", CxIcons.class);
        public static final Icon CRITICAL = IconLoader.getIcon("/icons/devassist/severity_16/critical.svg", CxIcons.class);
        public static final Icon HIGH = IconLoader.getIcon("/icons/devassist/severity_16/high.svg", CxIcons.class);
        public static final Icon MEDIUM = IconLoader.getIcon("/icons/devassist/severity_16/medium.svg", CxIcons.class);
        public static final Icon LOW = IconLoader.getIcon("/icons/devassist/severity_16/low.svg", CxIcons.class);
        public static final Icon IGNORED = IconLoader.getIcon("/icons/devassist/severity_16/ignored.svg", CxIcons.class);
        public static final Icon OK = IconLoader.getIcon("/icons/devassist/severity_16/ok.svg", CxIcons.class);

    }
}
