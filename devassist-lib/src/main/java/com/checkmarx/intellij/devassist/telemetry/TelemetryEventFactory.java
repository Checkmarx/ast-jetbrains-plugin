package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.devassist.telemetry.TelemetryConstants.*;

/**
 * Builds the analytics events. The common fields are added by {@link TelemetryService}.
 */
public final class TelemetryEventFactory {

    private static final int MAX_STATUS_RESULT_LENGTH = 256;
    private static final String PATH_PLACEHOLDER = "<path>";

    private static final Pattern URL_CREDENTIALS = Pattern.compile("([a-z][a-z0-9+.-]*://)[^\\s/@]+@", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "\\b(api[-_]?key|apikey|token|secret|password)\\s*[:=]\\s*[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTH_HEADER = Pattern.compile("\\b(bearer|basic)\\s+[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile("\\beyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+");
    private static final Pattern WINDOWS_PATH = Pattern.compile("\\b[A-Za-z]:[\\\\/][^\\s,;'\"]*");
    private static final Pattern UNC_PATH = Pattern.compile("\\\\\\\\[^\\s,;'\"]+");
    // The rest of a Windows path that contains spaces, e.g. "Smith\AppData\Local"
    private static final Pattern BACKSLASH_PATH = Pattern.compile("[^\\s,;'\"]*\\\\[^\\s,;'\"]*");
    // At least two segments; the look-behind keeps URLs (https://host/a/b) out
    private static final Pattern UNIX_PATH = Pattern.compile("(?<![\\w:/.~-])~?/(?:[^\\s,;:'\"/]+/)+[^\\s,;:'\"/]*");
    private static final Pattern LONG_OPAQUE = Pattern.compile("[A-Za-z0-9_\\-+/]{32,}={0,2}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TelemetryEventFactory() {
    }

    public static TelemetryEvent.TelemetryEventBuilder scanEvent(String engine, List<String> severities, String repoUrl) {
        Map<String, Integer> counts = countBySeverity(severities);
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return TelemetryEvent.builder()
                .eventType(TYPE_SCAN)
                .subType(SUB_TYPE_SCAN_TRIGGERED)
                .engine(engine)
                .problemSeverity(highestSeverity(counts))
                .totalCount(total)
                .status(STATUS_SUCCESS)
                .statusResult(formatSeverityCounts(counts))
                .repoUrl(repoUrl)
                .apiVersion(API_VERSION)
                .eventAttribute(ATTR_API_VERSION, API_VERSION);
    }

    public static TelemetryEvent.TelemetryEventBuilder scanFailureEvent(String engine, Throwable error, String repoUrl) {
        return scanFailureEventWithReason(engine, describe(error), repoUrl);
    }

    public static TelemetryEvent.TelemetryEventBuilder scanFailureEventWithReason(String engine, String reason, String repoUrl) {
        return TelemetryEvent.builder()
                .eventType(TYPE_SCAN)
                .subType(SUB_TYPE_SCAN_TRIGGERED)
                .engine(engine)
                .status(STATUS_FAILURE)
                .statusResult("Scan failed. Reason: " + reasonOrUnknown(reason) + ", Error Code: N/A")
                .repoUrl(repoUrl)
                .apiVersion(API_VERSION)
                .eventAttribute(ATTR_API_VERSION, API_VERSION);
    }

    public static TelemetryEvent.TelemetryEventBuilder loginEvent(String authMethod, boolean success, String reason,
                                                                 String timestamp) {
        return TelemetryEvent.builder()
                .eventType(TYPE_AUTHENTICATION)
                .subType(SUB_TYPE_AUTHENTICATION)
                .status(success ? STATUS_SUCCESS : STATUS_FAILURE)
                .statusResult(success
                        ? "Authentication successful."
                        : "Authentication failed. Reason: " + reasonOrUnknown(reason) + ", Error Code: N/A")
                .eventAttribute(ATTR_AUTH_METHOD, authMethod)
                .eventAttribute(ATTR_LOGIN_TIMESTAMP, timestamp);
    }

    public static TelemetryEvent.TelemetryEventBuilder installEvent(String installationId, String timestamp) {
        return TelemetryEvent.builder()
                .eventType(TYPE_INSTALL)
                .subType(SUB_TYPE_INSTALLATION)
                .status(STATUS_SUCCESS)
                .statusResult("Plugin installed successfully.")
                .eventAttribute(ATTR_INSTALLATION_ID, installationId)
                .eventAttribute(ATTR_INSTALLATION_TIMESTAMP, timestamp);
    }

    /**
     * @param failureReason null when the prompt reached the AI agent
     */
    public static TelemetryEvent.TelemetryEventBuilder remediationEvent(String engine, String severity, String aiProvider,
                                                                       String vulnerabilityId, String failureReason,
                                                                       String repoUrl, String subType) {
        boolean failed = failureReason != null;
        return TelemetryEvent.builder()
                .eventType(TYPE_CLICK)
                .subType(subType)
                .engine(engine)
                .problemSeverity(severity)
                .aiProvider(aiProvider)
                .status(failed ? STATUS_FAILURE : STATUS_SUCCESS)
                .statusResult(failed
                        ? "Remediation failed. Reason: " + reasonOrUnknown(failureReason) + ", Error Code: N/A"
                        : "Remediation completed successfully.")
                .repoUrl(repoUrl)
                .eventAttribute(ATTR_VULNERABILITY_ID, vulnerabilityId)
                .eventAttribute(ATTR_REMEDIATION_MODE, REMEDIATION_MODE_DEV_ASSIST);
    }

    public static TelemetryEvent.TelemetryEventBuilder clickEvent(String subType, String engine, String severity,
                                                                 String aiProvider, String vulnerabilityId,
                                                                 String repoUrl) {
        return TelemetryEvent.builder()
                .eventType(TYPE_CLICK)
                .subType(subType)
                .engine(engine)
                .problemSeverity(severity)
                .aiProvider(aiProvider)
                .status(STATUS_SUCCESS)
                .statusResult(clickStatusResult(subType))
                .repoUrl(repoUrl)
                .eventAttribute(ATTR_VULNERABILITY_ID, vulnerabilityId);
    }

    private static String clickStatusResult(String subType) {
        if (SUB_TYPE_VIEW_DETAILS.equals(subType)) {
            return STATUS_RESULT_VIEW_DETAILS;
        }
        if (SUB_TYPE_IGNORE.equals(subType)) {
            return STATUS_RESULT_IGNORE;
        }
        if (SUB_TYPE_IGNORE_ALL.equals(subType)) {
            return STATUS_RESULT_IGNORE_ALL;
        }
        return "";
    }

    /**
     * Malicious counts as Critical, unknown values as Info; "OK" is not a finding and returns null.
     */
    public static String toSeverityLabel(String severity) {
        String raw = severity == null ? "unknown" : severity.trim().toLowerCase();
        switch (raw) {
            case "critical":
            case "malicious":
                return SEVERITY_CRITICAL;
            case "high":
                return SEVERITY_HIGH;
            case "medium":
                return SEVERITY_MEDIUM;
            case "low":
                return SEVERITY_LOW;
            case "ok":
                return null;
            default:
                return SEVERITY_INFO;
        }
    }

    public static Map<String, Integer> countBySeverity(List<String> severities) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        SEVERITY_LABELS.forEach(label -> counts.put(label, 0));
        if (severities != null) {
            for (String severity : severities) {
                String label = toSeverityLabel(severity);
                if (label != null) {
                    counts.merge(label, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    public static String highestSeverity(Map<String, Integer> counts) {
        return SEVERITY_LABELS.stream().filter(label -> counts.getOrDefault(label, 0) > 0).findFirst().orElse("");
    }

    public static String formatSeverityCounts(Map<String, Integer> counts) {
        return SEVERITY_LABELS.stream()
                .map(label -> label + "=" + counts.getOrDefault(label, 0))
                .collect(Collectors.joining(", "));
    }

    /**
     * Removes credentials, tokens and local file paths, collapses whitespace and truncates.
     */
    public static String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        String text = URL_CREDENTIALS.matcher(value).replaceAll("$1***@");
        text = SECRET_PAIR.matcher(text).replaceAll("$1=***");
        text = AUTH_HEADER.matcher(text).replaceAll("$1 ***");
        text = JWT.matcher(text).replaceAll("***");
        text = WINDOWS_PATH.matcher(text).replaceAll(PATH_PLACEHOLDER);
        text = UNC_PATH.matcher(text).replaceAll(PATH_PLACEHOLDER);
        text = BACKSLASH_PATH.matcher(text).replaceAll(PATH_PLACEHOLDER);
        text = UNIX_PATH.matcher(text).replaceAll(PATH_PLACEHOLDER);
        text = LONG_OPAQUE.matcher(text).replaceAll("***");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        return text.length() > MAX_STATUS_RESULT_LENGTH ? text.substring(0, MAX_STATUS_RESULT_LENGTH) : text;
    }

    private static String reasonOrUnknown(String reason) {
        String text = sanitizeText(reason);
        return text.isEmpty() ? "unknown" : text;
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }
}
