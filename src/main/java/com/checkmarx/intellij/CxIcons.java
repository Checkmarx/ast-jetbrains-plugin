package com.checkmarx.intellij;

import com.intellij.openapi.util.IconLoader;
import javax.swing.*;

/**
 * Holds Checkmarx's custom icons.
 */
public final class CxIcons {

    private CxIcons() {
    }

    public static final Icon CHECKMARX_13 = IconLoader.getIcon("/icons/checkmarx-plugin-13.png", CxIcons.class);
    public static final Icon CHECKMARX_13_COLOR = IconLoader.getIcon("/icons/checkmarx-13.png", CxIcons.class);
    public static final Icon CHECKMARX_80 = IconLoader.getIcon("/icons/checkmarx-80.png", CxIcons.class);
    public static final Icon COMMENT = IconLoader.getIcon("/icons/comment.svg", CxIcons.class);
    public static final Icon STATE = IconLoader.getIcon("/icons/Flags.svg", CxIcons.class);
    public static final Icon ABOUT = IconLoader.getIcon("/icons/about.svg", CxIcons.class);
    public static final Icon INFO = IconLoader.getIcon("/icons/info.svg", CxIcons.class);

    public static Icon getWelcomeScannerIcon() {
        return IconLoader.getIcon("/icons/welcomePageScanner.svg", CxIcons.class);
    }

    public static Icon getWelcomeMcpDisableIcon() {
        return IconLoader.getIcon("/icons/cxAIError.svg", CxIcons.class);
    }

    public static final Icon STAR_ACTION = IconLoader.getIcon("/icons/devassist/star-action.svg", CxIcons.class);

    /**
     * Inner static final class, to maintain the constants used in icons for the value 24*24.
     */
    public static final class Regular {

        private Regular() {
        }

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
    public static final class Medium {

        private Medium() {
        }

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
    public static final class Small {

        private Small() {
        }

        public static final Icon MALICIOUS = IconLoader.getIcon("/icons/devassist/severity_16/malicious.svg", CxIcons.class);
        public static final Icon CRITICAL = IconLoader.getIcon("/icons/devassist/severity_16/critical.svg", CxIcons.class);
        public static final Icon HIGH = IconLoader.getIcon("/icons/devassist/severity_16/high.svg", CxIcons.class);
        public static final Icon MEDIUM = IconLoader.getIcon("/icons/devassist/severity_16/medium.svg", CxIcons.class);
        public static final Icon LOW = IconLoader.getIcon("/icons/devassist/severity_16/low.svg", CxIcons.class);
        public static final Icon IGNORED = IconLoader.getIcon("/icons/devassist/severity_16/ignored.svg", CxIcons.class);
        public static final Icon OK = IconLoader.getIcon("/icons/devassist/severity_16/ok.svg", CxIcons.class);
        public static final Icon UNKNOWN = IconLoader.getIcon("/icons/devassist/severity_16/unknown.svg", CxIcons.class);

    }

    /**
     * Inner static final class for ignored tab icons with theme support.
     */
    public static final class Ignored {

        private Ignored() {
        }

        public static final Icon REVIVE = IconLoader.getIcon("/icons/devassist/ignored/revive.svg", CxIcons.class);
        public static final Icon ENGINE_CHIP_CONTAINERS = IconLoader.getIcon("/icons/devassist/ignored/engine-chip-containers.svg", CxIcons.class);
        public static final Icon ENGINE_CHIP_SCA = IconLoader.getIcon("/icons/devassist/ignored/engine-chip-sca.svg", CxIcons.class);
        public static final Icon ENGINE_CHIP_SECRETS = IconLoader.getIcon("/icons/devassist/ignored/engine-chip-secrets.svg", CxIcons.class);
        public static final Icon ENGINE_CHIP_IAC = IconLoader.getIcon("/icons/devassist/ignored/engine-chip-iac.svg", CxIcons.class);
        public static final Icon ENGINE_CHIP_SAST = IconLoader.getIcon("/icons/devassist/ignored/engine-chip-sast.svg", CxIcons.class);

        // Card icons - Containers
        public static final Icon CARD_CONTAINERS_CRITICAL = IconLoader.getIcon("/icons/devassist/ignored_card/card-containers-critical.svg", CxIcons.class);
        public static final Icon CARD_CONTAINERS_HIGH = IconLoader.getIcon("/icons/devassist/ignored_card/card-containers-high.svg", CxIcons.class);
        public static final Icon CARD_CONTAINERS_MEDIUM = IconLoader.getIcon("/icons/devassist/ignored_card/card-containers-medium.svg", CxIcons.class);
        public static final Icon CARD_CONTAINERS_LOW = IconLoader.getIcon("/icons/devassist/ignored_card/card-containers-low.svg", CxIcons.class);
        public static final Icon CARD_CONTAINERS_MALICIOUS = IconLoader.getIcon("/icons/devassist/ignored_card/card-containers-malicious.svg", CxIcons.class);

        // Card icons - Package (OSS)
        public static final Icon CARD_PACKAGE_CRITICAL = IconLoader.getIcon("/icons/devassist/ignored_card/card-package-critical.svg", CxIcons.class);
        public static final Icon CARD_PACKAGE_HIGH = IconLoader.getIcon("/icons/devassist/ignored_card/card-package-high.svg", CxIcons.class);
        public static final Icon CARD_PACKAGE_MEDIUM = IconLoader.getIcon("/icons/devassist/ignored_card/card-package-medium.svg", CxIcons.class);
        public static final Icon CARD_PACKAGE_LOW = IconLoader.getIcon("/icons/devassist/ignored_card/card-package-low.svg", CxIcons.class);
        public static final Icon CARD_PACKAGE_MALICIOUS = IconLoader.getIcon("/icons/devassist/ignored_card/card-package-malicious.svg", CxIcons.class);

        // Card icons - Secret
        public static final Icon CARD_SECRET_CRITICAL = IconLoader.getIcon("/icons/devassist/ignored_card/card-secret-critical.svg", CxIcons.class);
        public static final Icon CARD_SECRET_HIGH = IconLoader.getIcon("/icons/devassist/ignored_card/card-secret-high.svg", CxIcons.class);
        public static final Icon CARD_SECRET_MEDIUM = IconLoader.getIcon("/icons/devassist/ignored_card/card-secret-medium.svg", CxIcons.class);
        public static final Icon CARD_SECRET_LOW = IconLoader.getIcon("/icons/devassist/ignored_card/card-secret-low.svg", CxIcons.class);
        public static final Icon CARD_SECRET_MALICIOUS = IconLoader.getIcon("/icons/devassist/ignored_card/card-secret-malicious.svg", CxIcons.class);

        // Card icons - Vulnerability (IAC/ASCA)
        public static final Icon CARD_VULNERABILITY_CRITICAL = IconLoader.getIcon("/icons/devassist/ignored_card/card-vulnerability-critical.svg", CxIcons.class);
        public static final Icon CARD_VULNERABILITY_HIGH = IconLoader.getIcon("/icons/devassist/ignored_card/card-vulnerability-high.svg", CxIcons.class);
        public static final Icon CARD_VULNERABILITY_MEDIUM = IconLoader.getIcon("/icons/devassist/ignored_card/card-vulnerability-medium.svg", CxIcons.class);
        public static final Icon CARD_VULNERABILITY_LOW = IconLoader.getIcon("/icons/devassist/ignored_card/card-vulnerability-low.svg", CxIcons.class);
        public static final Icon CARD_VULNERABILITY_MALICIOUS = IconLoader.getIcon("/icons/devassist/ignored_card/card-vulnerability-malicious.svg", CxIcons.class);
    }
}
