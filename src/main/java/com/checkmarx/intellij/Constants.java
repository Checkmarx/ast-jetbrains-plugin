package com.checkmarx.intellij;

import org.jetbrains.annotations.NonNls;

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

    public static final String GLOBAL_SETTINGS_ID = "settings.ast";
    public static final String TOOL_WINDOW_ID = "Checkmarx";
    public static final String ACTION_GROUP_ID = "Checkmarx.Toolbar";
    public static final String NOTIFICATION_GROUP_ID = "Checkmarx.Notifications";

    public static final String ADDITIONAL_PARAMETERS_HELP
            = "https://checkmarx.atlassian.net/wiki/spaces/AST/pages/3080194906/scan+create";
    public static final String INTELLIJ_HELP
            = "https://checkmarx.atlassian.net/wiki/spaces/AST/pages/6030557208/JetBrains";

    public static final String FIELD_GAP_BOTTOM = "gapbottom 15";

    public static final String HTML_WRAPPER_FORMAT = "<html>%s</html>";
    public static final String HTML_FONT_YELLOW_FORMAT = "<html><font color=%s>%s</font></html>";
    public static final String HTML_FONT_BLUE_FORMAT = "<html><font color=%s>%s</font></html>";
    public static final String HELP_HTML = "<html><a href=\"\">%s</a></html>";
    public static final String HREF_HTML = "<html><a href=%s>%s</a>";
    public static final String NODE_FORMAT = "%s | %s";
    public static final String FILE_FORMAT = "%s:%d (%s)";
    public static final String SUMMARY_FORMAT = "%s | %s | %s | %s";
    public static final String FIELD_FORMAT = "<html>%s%s</html>";
    public static final String REQUIRED_MARK = "<span style=\"color:#ff0000\">*</span>";
    public static final String VALUE_FORMAT = "<html><b>%s</b>: %s</html>";
    public static final String INPUT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String OUTPUT_DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

    public static final String API_KEY_CREDENTIALS_KEY = "APIKey";

    public static final String GLOBAL_SETTINGS_STATE_NAME = "Checkmarx AST";
    public static final String GLOBAL_SETTINGS_STATE_FILE = "cx_global_settings.xml";

    public static final String FIELD_NAME_SERVER_URL = "serverUrl";
    public static final String FIELD_NAME_AUTH_URL = "authUrl";
    public static final String FIELD_NAME_USE_AUTH_URL = "useAuthUrl";
    public static final String FIELD_NAME_TENANT = "tenant";
    public static final String FIELD_NAME_API_KEY = "apiKey";
    public static final String FIELD_NAME_ADDITIONAL_PARAMETERS = "additionalParameters";

    public static final String SELECTED_PROJECT_PROPERTY = "Checkmarx.SelectedProject";
    public static final String SELECTED_BRANCH_PROPERTY = "Checkmarx.SelectedBranch";
    public static final String SELECTED_SCAN_PROPERTY = "Checkmarx.SelectedScan";

    public static final String SCAN_TYPE_SCA = "sca";
    public static final String SCAN_TYPE_KICS = "kics";

    public static final String SCAN_STATE_CONFIRMED = "confirmed";
    public static final String SCAN_STATE_TO_VERIFY = "to_verify";

    public static final String SCAN_SEVERITY_HIGH = "high";
    public static final String SCAN_SEVERITY_LOW = "low";

    public static final int DEFAULT_COLUMN = 0;
    public static final int FILE_PATH_MAX_LEN = 45;
    public static final String COLLAPSE_CRUMB = "...";

    public static final int LICENSE_NOT_FOUND_EXIT_CODE = 3;
    public static final int LESSON_NOT_FOUND_EXIT_CODE = 4;
}
