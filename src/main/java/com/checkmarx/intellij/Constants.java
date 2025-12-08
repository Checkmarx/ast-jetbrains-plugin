package com.checkmarx.intellij;

import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * Non-translatable constants.
 */
@NonNls
public final class Constants {


    private Constants() {
        // forbid instantiation of the class
    }

    public static final String BUNDLE_PATH = "messages.CxBundle";

    public static final String LOGGER_CAT_PREFIX = "CX#";
    public static final String CXONE_ASSIST = "CxOne Assist";

    public static final String GLOBAL_SETTINGS_ID = "settings.ast";
    public static final String TOOL_WINDOW_ID = "Checkmarx";
    public static final String ACTION_GROUP_ID = "Checkmarx.Toolbar";
    public static final String NOTIFICATION_GROUP_ID = "Checkmarx.Notifications";

    public static final String ADDITIONAL_PARAMETERS_HELP
            = "https://checkmarx.com/resource/documents/en/34965-68626-global-flags.html";

    public static final String INTELLIJ_HELP
            = "https://docs.checkmarx.com/en/34965-68734-installing-and-setting-up-the-checkmarx-one-jetbrains-pluging.html";

    public static final String FIELD_GAP_BOTTOM = "gapbottom 15";

    public static final String HTML_WRAPPER_FORMAT = "<html>%s</html>";
    public static final String HTML_FONT_YELLOW_FORMAT = "<html><font color=%s>%s</font></html>";
    public static final String HTML_FONT_BLUE_FORMAT = "<html><font color=%s>%s</font></html>";
    public static final String HTML_BOLD_FORMAT = "<html><b>%s</b></html>";
    public static final String HELP_HTML = "<html><a href=\"\">%s</a></html>";
    public static final String HREF_HTML = "<html><a href=%s>%s</a>";
    public static final String NODE_FORMAT = "%s | %s";
    public static final String FILE_FORMAT = "%s:%d (%s)";
    public static final String SUMMARY_FORMAT = "%s | %s | %s | %s";
    public static final String FIELD_FORMAT = "<html>%s%s:</html>";
    public static final String REQUIRED_MARK = "<span style=\"color:#ff0000\">*</span>";
    public static final String VALUE_FORMAT = "<html><b>%s</b>: %s</html>";
    public static final String INPUT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String OUTPUT_DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

    public static final String GLOBAL_SETTINGS_STATE_NAME = "Checkmarx One";
    public static final String GLOBAL_SETTINGS_STATE_FILE = "cx_global_settings.xml";

    public static final String FIELD_NAME_API_KEY = "apiKey";
    public static final String FIELD_NAME_ADDITIONAL_PARAMETERS = "additionalParameters";
    public static final String FIELD_NAME_ASCA = "ascaCheckBox";

    public static final String SELECTED_PROJECT_PROPERTY = "Checkmarx.SelectedProject";
    public static final String SELECTED_BRANCH_PROPERTY = "Checkmarx.SelectedBranch";
    public static final String SELECTED_SCAN_PROPERTY = "Checkmarx.SelectedScan";
    public static final String RUNNING_SCAN_ID_PROPERTY = "Checkmarx.RunningScanId";

    public static final String SCAN_TYPE_SCA = "sca";
    public static final String SCAN_TYPE_KICS = "kics";
    public static final String SCAN_TYPE_SCS = "scs";

    public static final String SCAN_STATE_CONFIRMED = "confirmed";
    public static final String SCAN_STATE_TO_VERIFY = "to_verify";

    public static final String SCAN_SEVERITY_HIGH = "high";
    public static final String SCAN_SEVERITY_LOW = "low";

    public static final int DEFAULT_COLUMN = 0;
    public static final int FILE_PATH_MAX_LEN = 45;
    public static final String COLLAPSE_CRUMB = "...";

    public static final int LICENSE_NOT_FOUND_EXIT_CODE = 3;
    public static final int LESSON_NOT_FOUND_EXIT_CODE = 4;

    public static final String REMEDIATION_EXAMPLES_USING = " using ";

    public static final String SCAN_STATUS_RUNNING = "running";
    public static final String SCAN_STATUS_COMPLETED = "completed";
    public static final String JET_BRAINS_AGENT_NAME = "Jetbrains";

    public static final String MALICIOUS_SEVERITY = "Malicious";
    public static final String CRITICAL_SEVERITY = "Critical";
    public static final String HIGH_SEVERITY = "High";
    public static final String MEDIUM_SEVERITY = "Medium";
    public static final String LOW_SEVERITY = "Low";
    public static final String OK = "OK";
    public static final String UNKNOWN = "Unknown";

    public static final String IGNORE_LABEL = "IGNORED";
    public static final String NOT_IGNORE_LABEL = "NOT_IGNORED";
    public static final String SCA_HIDE_DEV_TEST_DEPENDENCIES = "SCA_HIDE_DEV_TEST_DEPENDENCIES";
    public static final String NOT_EXPLOITABLE_LABEL = "NOT_EXPLOITABLE";
    public static final String PROPOSED_NOT_EXPLOITABLE_LABEL = "PROPOSED_NOT_EXPLOITABLE";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String TO_VERIFY = "TO_VERIFY";
    public static final String URGENT = "URGENT";

    public static final String USE_LOCAL_BRANCH = "scan my local branch";

    /**
     * Inner static final class, to maintain the constants used in authentication.
     */
    public static final class AuthConstants {

        private AuthConstants() {
            throw new UnsupportedOperationException("Cannot instantiate AuthConstants class");
        }

        public static final String OAUTH_IDE_CLIENT_ID = "ide-integration";
        public static final String ALGO_SHA256 = "SHA-256";
        public static final String SCOPE = "openid offline_access";
        public static final String CODE = "code";
        public static final String REFRESH_TOKEN = "refresh_token";
        public static final String REFRESH_TOKEN_EXPIRY = "refresh_expires_in";
        public static final String STATE = "state";
        public static final String CODE_CHALLENGE_METHOD = "S256";
        public static final String ERROR = "error";
        public static final String AUTH_SUCCESS_HTML_FILE_PATH = "auth/auth-success.html";
        public static final String AUTH_ERROR_HTML_FILE_PATH = "auth/auth-error.html";
        public static final String LOCATION = "location";
        public static final int TIME_OUT_SECONDS = 120;
    }

    /**
     * The RealTimeConstants class defines a collection of constant values
     * related to real-time scanning functionalities, including support for
     * different scanning engines and associated configurations.
     */
    public static final class RealTimeConstants {

        private RealTimeConstants() {
            throw new UnsupportedOperationException("Cannot instantiate RealTimeConstants class");
        }

        // Tab Name Constants
        public static final String DEVASSIST_TAB = "CxOne Assist Findings";

        // OSS Scanner Constants
        public static final String ACTIVATE_OSS_REALTIME_SCANNER = "Activate OSS-Realtime";
        public static final String OSS_REALTIME_SCANNER = "Checkmarx Open Source Realtime Scanner (OSS-Realtime)";
        public static final String OSS_REALTIME_SCANNER_START = "Realtime OSS Scanner Engine started";
        public static final String OSS_REALTIME_SCANNER_DISABLED = "Realtime OSS Scanner Engine disabled";
        public static final String OSS_REALTIME_SCANNER_DIRECTORY = "Cx-oss-realtime-scanner";
        public static final String ERROR_OSS_REALTIME_SCANNER = "Failed to handle OSS Realtime scan";

        public static final String ACTIVATE_CONTAINER_REALTIME_SCANNER = "Activate Containers-Realtime";
        public static final String CONTAINER_REALTIME_SCANNER = "Checkmarx Containers Realtime Scanner (Containers-Realtime)";
        public static final String CONTAINER_REALTIME_SCANNER_START = "Realtime Containers Scanner Engine started";
        public static final String CONTAINER_REALTIME_SCANNER_DISABLED = "Realtime Containers Scanner Engine disabled";
        public static final String CONTAINER_REALTIME_SCANNER_DIRECTORY = "Cx-containers-realtime-scanner";
        public static final String ERROR_CONTAINER_REALTIME_SCANNER = "Failed to handle Containers Realtime scan";

        // Secrets Scanner Constants
        public static final String ACTIVATE_SECRETS_REALTIME_SCANNER = "Activate Secrets-Realtime";
        public static final String SECRETS_REALTIME_SCANNER = "Checkmarx Secrets Realtime Scanner (Secrets-Realtime)";
        public static final String SECRETS_REALTIME_SCANNER_START = "Realtime Secrets Scanner Engine started";
        public static final String SECRETS_REALTIME_SCANNER_DISABLED = "Realtime Secrets Scanner Engine disabled";
        public static final String SECRETS_REALTIME_SCANNER_DIRECTORY = "Cx-secrets-realtime-scanner";
        public static final String ERROR_SECRETS_REALTIME_SCANNER = "Failed to handle Secrets Realtime scan";

        //Dev Assist Fixes Constants
        public static final String FIX_WITH_CXONE_ASSIST = "Copy fix prompt";
        public static final String VIEW_DETAILS_FIX_NAME = "View details";
        public static final String IGNORE_THIS_VULNERABILITY_FIX_NAME = "Ignore this vulnerability";
        public static final String IGNORE_ALL_OF_THIS_TYPE_FIX_NAME = "Ignore all of this type";

        public static final List<String> MANIFEST_FILE_PATTERNS = List.of(
                "**/Directory.Packages.props",
                "**/packages.config",
                "**/pom.xml",
                "**/package.json",
                "**/requirements.txt",
                "**/go.mod",
                "**/*.csproj"
        );

        public  static  final List<String> CONTAINERS_FILE_PATTERNS= List.of(
                "**/dockerfile",
                "**/dockerfile-*",
                "**/dockerfile.*",
                "**/docker-compose.yml",
                "**/docker-compose.yaml",
                "**/docker-compose-*.yml",
                "**/docker-compose-*.yaml"
        );

        public static final List<String>CONTAINER_HELM_EXTENSION= List.of( "yml",
                "yaml");


        public static final List<String>CONTAINER_HELM_EXCLUDED_FILES= List.of( "chart.yml",
                "chart.yaml");

        //Tooltip description constants
        public static final String RISK_PACKAGE = "risk package";
        public static final String SEVERITY_PACKAGE = "Severity Package";
        public static final String PACKAGE_DETECTED = "package detected";
        public static final String THEME = "THEME";
        // Dev Assist Remediation
        public static final String CX_AGENT_NAME = "Checkmarx One Assist";
        // Files generated by the agent (Copilot)
        public static final List<String> AGENT_DUMMY_FILES = List.of("/Dummy.txt", "/");
        public static final String DEV_ASSIST_COPY_PROMPT = "Remediation prompt copied to the clipboard!, please paste in the Github Copilot Chat (Agent Mode).";
        public static final String RISK_IMAGE="risk image";
    }

    /**
     * Constant class to hold image paths.
     */
    public static final class ImagePaths {

        private ImagePaths() {
            throw new UnsupportedOperationException("Cannot instantiate ImagePaths class");
        }

        public static final String DEV_ASSIST_PNG = "/icons/devassist/tooltip/cxone_assist";
        public static final String CRITICAL_PNG = "/icons/devassist/tooltip/critical";
        public static final String HIGH_PNG = "/icons/devassist/tooltip/high";
        public static final String MEDIUM_PNG = "/icons/devassist/tooltip/medium";
        public static final String LOW_PNG = "/icons/devassist/tooltip/low";
        public static final String MALICIOUS_PNG = "/icons/devassist/tooltip/malicious";
        public static final String PACKAGE_PNG = "/icons/devassist/tooltip/package";

        // Vulnerability Severity Count Icons
        public static final String CRITICAL_16_PNG = "/icons/devassist/tooltip/severity_count/critical";
        public static final String HIGH_16_PNG = "/icons/devassist/tooltip/severity_count/high";
        public static final String MEDIUM_16_PNG = "/icons/devassist/tooltip/severity_count/medium";
        public static final String LOW_16_PNG = "/icons/devassist/tooltip/severity_count/low";
    }

}
