package com.checkmarx.intellij.devassist.telemetry;

import java.util.List;

/**
 * Values sent in Dev Assist analytics events (AST-184118).
 * They follow the technical design and match the VS Code and JavaScript wrapper implementations.
 */
public final class TelemetryConstants {

    private TelemetryConstants() {
    }

    // Event types
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
    // "Ignore this vulnerability". Same value as the VS Code extension.
    public static final String SUB_TYPE_IGNORE = "Ignore";
    public static final String SUB_TYPE_IGNORE_ALL = "ignoreAll";

    // Status results of the finding actions other than Fix with AI
    public static final String STATUS_RESULT_VIEW_DETAILS = "Vulnerability details opened successfully.";
    public static final String STATUS_RESULT_IGNORE = "Vulnerability ignored successfully.";
    public static final String STATUS_RESULT_IGNORE_ALL = "All vulnerabilities of this type ignored successfully.";

    // Status
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";

    // Common fields
    public static final String PLUGIN_TYPE_IDE = "IDE";
    public static final String API_VERSION = "v2";
    public static final String REMEDIATION_MODE_DEV_ASSIST = "DevAssist";

    // Authentication methods
    public static final String AUTH_METHOD_OAUTH = "OAuth";
    public static final String AUTH_METHOD_API_KEY = "APIKey";

    // Event attribute keys
    public static final String ATTR_PLUGIN_NAME = "pluginName";
    public static final String ATTR_API_VERSION = "apiVersion";
    public static final String ATTR_INSTALLATION_ID = "installationId";
    public static final String ATTR_INSTALLATION_TIMESTAMP = "installationTimestamp";
    public static final String ATTR_AUTH_METHOD = "authMethod";
    public static final String ATTR_LOGIN_TIMESTAMP = "loginTimestamp";
    public static final String ATTR_VULNERABILITY_ID = "vulnerabilityId";
    public static final String ATTR_REMEDIATION_MODE = "remediationMode";

    // Plugin names, sent in the pluginName attribute so analytics can tell the two plugins apart.
    // Same values as the VS Code extensions.
    public static final String PLUGIN_NAME_CXONE = "Checkmarx One Assist";
    public static final String PLUGIN_NAME_DEV_ASSIST = "Checkmarx One Developer Assist";
    public static final String DEV_ASSIST_PLUGIN_ID = "com.checkmarx.devassist-jetbrains-plugin";

    // Severity labels used in scan events, highest first
    public static final String SEVERITY_CRITICAL = "Critical";
    public static final String SEVERITY_HIGH = "High";
    public static final String SEVERITY_MEDIUM = "Medium";
    public static final String SEVERITY_LOW = "Low";
    public static final String SEVERITY_INFO = "Info";
    public static final List<String> SEVERITY_LABELS = List.of(
            SEVERITY_CRITICAL, SEVERITY_HIGH, SEVERITY_MEDIUM, SEVERITY_LOW, SEVERITY_INFO);

    // AI provider names
    public static final String AI_PROVIDER_COPILOT = "Copilot";

    // CLI error text of a CLI that is too old to know the analytics flags
    public static final String UNKNOWN_FLAG_ERROR = "unknown flag";
}
