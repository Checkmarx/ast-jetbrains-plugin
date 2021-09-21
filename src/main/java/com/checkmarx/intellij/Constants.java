package com.checkmarx.intellij;

import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Locale;

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

    public static final String HELP_HTML = "<html><a href=\"\">%s</a></html>";
    public static final String HELP_HTML_U = "<html><a href=\"\"><u>%s</u></a></html>";

    public static final String NODE_FORMAT = "%d: %s:%d (%s)";
    public static final String PACKAGE_FORMAT = "[%s]: %s";

    public static final String OS_TEMP_DIR = System.getProperty("java.io.tmpdir");
    public static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
    public static final String OS_LINUX = "linux";
    public static final String OS_WINDOWS = "windows";
    public static final List<String> OS_MAC = List.of("mac os x", "darwin", "osx");
    public static final String FILE_NAME_LINUX = "cx-linux";
    public static final String FILE_NAME_MAC = "cx-mac";
    public static final String FILE_NAME_WINDOWS = "cx.exe";

    public static final String API_KEY_CREDENTIALS_KEY = "APIKey";

    public static final String GLOBAL_SETTINGS_STATE_NAME = "CxGlobalSettings";
    public static final String GLOBAL_SETTINGS_STATE_FILE = "cx_global_settings.xml";

    public static final String FIELD_NAME_SERVER_URL = "serverUrl";
    public static final String FIELD_NAME_AUTH_URL = "authUrl";
    public static final String FIELD_NAME_USE_AUTH_URL = "useAuthUrl";
    public static final String FIELD_NAME_TENANT = "tenant";
    public static final String FIELD_NAME_API_KEY = "apiKey";
    public static final String FIELD_NAME_ADDITIONAL_PARAMETERS = "additionalParameters";
}
