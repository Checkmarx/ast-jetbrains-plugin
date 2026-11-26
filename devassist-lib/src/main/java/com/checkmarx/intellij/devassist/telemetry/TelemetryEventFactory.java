package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.devassist.telemetry.TelemetryConstants.*;

/**
 * Builds the event specific part of Dev Assist analytics events.
 * The common fields (agent, versions, plugin type and name) are added by {@link TelemetryService} when the event is sent.
 */
public final class TelemetryEventFactory {

    private static final int MAX_STATUS_RESULT_LENGTH = 256;

    private static final Pattern URL_CREDENTIALS = Pattern.compile("([a-z][a-z0-9+.-]*://)[^\\s/@]+@", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_PAIR = Pattern.compile(
            "\\b(api[-_]?key|apikey|token|secret|password)\\s*[:=]\\s*[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTH_HEADER = Pattern.compile("\\b(bearer|basic)\\s+[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile("\\beyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+");
    private static final Pattern LONG_OPAQUE = Pattern.compile("[A-Za-z0-9_\\-+/]{32,}={0,2}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TelemetryEventFactory() {
    }

    /**
     * One event per scan. A scan without findings is sent too, because engagement metrics need every scan.
     *
     * @param engine     engine name (Oss, Secrets, IaC, Asca, Containers)
     * @param severities severity of every finding
     * @param repoUrl    sanitized repository URL, may be empty
     */
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
        return TelemetryEvent.builder()
                .eventType(TYPE_SCAN)
                .subType(SUB_TYPE_SCAN_TRIGGERED)
                .engine(engine)
                .status(STATUS_FAILURE)
                .statusResult("Scan failed. Reason: " + describe(error) + ", Error Code: N/A")
                .repoUrl(repoUrl)
                .apiVersion(API_VERSION)
                .eventAttribute(ATTR_API_VERSION, API_VERSION);
    }

    /**
     * Login event, sent only for interactive sign-ins.
     *
     * @param authMethod {@link TelemetryConstants#AUTH_METHOD_OAUTH} or {@link TelemetryConstants#AUTH_METHOD_API_KEY}
     * @param reason     failure reason, ignored on success
     * @param timestamp  ISO-8601 login time
     */
    public static TelemetryEvent.TelemetryEventBuilder loginEvent(String authMethod, boolean success, String reason,
                                                                 String timestamp) {
        return TelemetryEvent.builder()
                .eventType(TYPE_AUTHENTICATION)
                .subType(SUB_TYPE_AUTHENTICATION)
                .status(success ? STATUS_SUCCESS : STATUS_FAILURE)
                .statusResult(success
                        ? "Authentication successful."
                        : "Authentication failed. Reason: " + sanitizeText(reason == null ? "unknown" : reason) + ", Error Code: N/A")
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
     * "Fix with AI" event, sent once the prompt reached (or failed to reach) the AI assistant.
     *
     * @param aiProvider    the assistant that was actually used
     * @param failureReason null on success
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
                        ? "Remediation failed. Reason: " + sanitizeText(failureReason) + ", Error Code: N/A"
                        : "Remediation completed successfully.")
                .repoUrl(repoUrl)
                .eventAttribute(ATTR_VULNERABILITY_ID, vulnerabilityId)
                .eventAttribute(ATTR_REMEDIATION_MODE, REMEDIATION_MODE_DEV_ASSIST);
    }

    /**
     * Other click events: view details, ignore, ignore all. They carry the same finding context as the
     * "Fix with AI" event (engine, severity, vulnerability id, repository) so the analytics can tell which finding
     * the action was taken on. They have no remediation mode, because nothing is remediated.
     *
     * @param vulnerabilityId identifier of the finding, see {@link TelemetryService#getVulnerabilityId}
     */
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
     * Maps a scanner severity to a telemetry label. Malicious counts as Critical, unknown values are reported as Info,
     * and "OK" (no vulnerability) is not counted, so it returns null.
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

    /**
     * Count of findings for every severity label, in label order.
     */
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

    /**
     * Highest severity with at least one finding, empty for a clean scan.
     */
    public static String highestSeverity(Map<String, Integer> counts) {
        return SEVERITY_LABELS.stream().filter(label -> counts.getOrDefault(label, 0) > 0).findFirst().orElse("");
    }

    /**
     * "Critical=1, High=0, Medium=2, Low=0, Info=0"
     */
    public static String formatSeverityCounts(Map<String, Integer> counts) {
        return SEVERITY_LABELS.stream()
                .map(label -> label + "=" + counts.getOrDefault(label, 0))
                .collect(Collectors.joining(", "));
    }

    /**
     * Makes free text safe to send: removes anything that looks like a token, key or credential,
     * collapses whitespace and truncates.
     */
    public static String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        String text = URL_CREDENTIALS.matcher(value).replaceAll("$1***@");
        text = SECRET_PAIR.matcher(text).replaceAll("$1=***");
        text = AUTH_HEADER.matcher(text).replaceAll("$1 ***");
        text = JWT.matcher(text).replaceAll("***");
        text = LONG_OPAQUE.matcher(text).replaceAll("***");
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        return text.length() > MAX_STATUS_RESULT_LENGTH ? text.substring(0, MAX_STATUS_RESULT_LENGTH) : text;
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        return sanitizeText(message == null || message.isBlank() ? error.getClass().getSimpleName() : message);
    }
}
