package com.checkmarx.intellij.devassist.telemetry;

import java.util.List;

public final class TelemetryConstants {

    private TelemetryConstants() {
    }

    public static final String TYPE_INSTALL = "install";
    public static final String TYPE_AUTHENTICATION = "authentication";
    public static final String TYPE_SCAN = "Scan";
    public static final String TYPE_CLICK = "click";

    // Event sub types. "scantriggerd" is the spelling used in the design document.
    public static final String SUB_TYPE_INSTALLATION = "installation";
    public static final String SUB_TYPE_AUTHENTICATION = "authentication";
    public static final String SUB_TYPE_SCAN_TRIGGERED = "scantriggerd";
    public static final String SUB_TYPE_FIX_WITH_AI_CHAT = "fixWithAIChat";
    public static final String SUB_TYPE_VIEW_DETAILS = "viewDetails";
    public static final String SUB_TYPE_IGNORE = "Ignore";
    public static final String SUB_TYPE_IGNORE_ALL = "ignoreAll";

    public static final String STATUS_RESULT_VIEW_DETAILS = "Vulnerability details opened successfully.";
    public static final String STATUS_RESULT_IGNORE = "Vulnerability ignored successfully.";
    public static final String STATUS_RESULT_IGNORE_ALL = "All vulnerabilities of this type ignored successfully.";

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";

    public static final String PLUGIN_TYPE_IDE = "IDE";
    public static final String API_VERSION = "v2";
    public static final String REMEDIATION_MODE_DEV_ASSIST = "DevAssist";

    public static final String AUTH_METHOD_OAUTH = "OAuth";
    public static final String AUTH_METHOD_API_KEY = "APIKey";

    public static final String ATTR_PLUGIN_NAME = "pluginName";
    public static final String ATTR_API_VERSION = "apiVersion";
    public static final String ATTR_INSTALLATION_ID = "installationId";
    public static final String ATTR_INSTALLATION_TIMESTAMP = "installationTimestamp";
    public static final String ATTR_AUTH_METHOD = "authMethod";
    public static final String ATTR_LOGIN_TIMESTAMP = "loginTimestamp";
    public static final String ATTR_VULNERABILITY_ID = "vulnerabilityId";
    public static final String ATTR_REMEDIATION_MODE = "remediationMode";

    public static final String PLUGIN_NAME_CXONE = "Checkmarx One Assist";
    public static final String PLUGIN_NAME_DEV_ASSIST = "Checkmarx One Developer Assist";
    public static final String DEV_ASSIST_PLUGIN_ID = "com.checkmarx.devassist-jetbrains-plugin";

    public static final String SEVERITY_CRITICAL = "Critical";
    public static final String SEVERITY_HIGH = "High";
    public static final String SEVERITY_MEDIUM = "Medium";
    public static final String SEVERITY_LOW = "Low";
    public static final String SEVERITY_INFO = "Info";
    public static final List<String> SEVERITY_LABELS = List.of(
            SEVERITY_CRITICAL, SEVERITY_HIGH, SEVERITY_MEDIUM, SEVERITY_LOW, SEVERITY_INFO);

    public static final String AI_PROVIDER_COPILOT = "Copilot";

    public static final String UNKNOWN_FLAG_ERROR = "unknown flag";
    public static final List<String> ANALYTICS_FLAGS = List.of("--agent-version", "--cx-plugin-version", "--plugin-type",
            "--status-result", "--repo-url", "--api-version", "--event-attributes");
}
