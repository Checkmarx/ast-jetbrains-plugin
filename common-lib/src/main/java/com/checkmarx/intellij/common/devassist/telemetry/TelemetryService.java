package com.checkmarx.intellij.common.devassist.telemetry;

import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.devassist.common.ScanResult;
import com.checkmarx.intellij.common.devassist.model.ScanIssue;
import com.checkmarx.intellij.common.devassist.utils.ScanEngine;
import com.checkmarx.intellij.common.settings.global.CxWrapperFactory;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Telemetry service for handling AI interactions and scan detection logs.
 * This service provides methods to log user actions (like "Fix with CxOne Assist" and "View Details")
 * and scan completion results by severity levels.
 */
public final class TelemetryService {

    private static final Logger LOGGER = Utils.getLogger(TelemetryService.class);

    // AI Provider and Agent constants
    private static final String AI_PROVIDER = "Copilot";

    // Event Types
    private static final String EVENT_TYPE_CLICK = "click";

    // Sub Types
    private static final String SUB_TYPE_FIX_WITH_AI_CHAT = "fixWithAIChat";
    private static final String SUB_TYPE_VIEW_DETAILS = "viewDetails";
    private static final String SUB_TYPE_IGNORE_PACKAGE = "ignorePackage";
    private static final String SUB_TYPE_IGNORE_ALL = "ignoreAll";

    // Engine Names
    private static final String ENGINE_OSS = "Oss";
    private static final String ENGINE_SECRETS = "Secrets";
    private static final String ENGINE_IAC = "IaC";
    private static final String ENGINE_ASCA = "Asca";
    private static final String ENGINE_CONTAINERS = "Containers";

    // Private constructor to prevent instantiation - using static methods
    private TelemetryService() {}

    /**
     * Sets user event data for logs.
     * This method is called when users interact with remediation options.
     *
     * @param eventType the type of event (e.g., "click")
     * @param subType the specific action (e.g., "fixWithAIChat", "viewDetails")
     * @param engine the scan engine type
     * @param problemSeverity the severity of the issue
     */
    public static void setUserEventDataForLogs(String eventType, String subType, String engine, String problemSeverity) {
        CompletableFuture.runAsync(() -> {
            try {
                String agent = getAgentName();

                LOGGER.debug(format("Telemetry: Logging user event - eventType: %s, subType: %s, engine: %s, severity: %s",
                        eventType, subType, engine, problemSeverity));

                CxWrapperFactory.build().telemetryAIEvent(
                        AI_PROVIDER,          // aiProvider
                        agent,                // agent
                        eventType,            // eventType
                        subType,              // subType
                        engine,               // engine
                        problemSeverity,      // problemSeverity
                        "",                   // scanType
                        "",                   // status
                        0                     // totalCount
                );


            } catch (Exception e) {
                LOGGER.warn(format("Telemetry: Failed to log user event telemetry for %s", subType), e);
            }
        });
    }

    /**
     * Sets user event data for detection logs.
     * This method is called when scan operations complete to track detection statistics.
     *
     * @param scanType the type of scan (e.g., "secrets", "iac", "asca", "oss")
     * @param status the severity level status
     * @param totalCount the number of issues found for this severity level
     */
    public static void setUserEventDataForDetectionLogs(String scanType, String status, int totalCount) {
        if (totalCount <= 0) {
            LOGGER.debug(format("Telemetry: No issues to log for scan type: %s, status: %s", scanType, status));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                LOGGER.debug(format("Telemetry: Logging detection results - scanType: %s, status: %s, count: %d",
                        scanType, status, totalCount));

                CxWrapperFactory.build().telemetryAIEvent(
                        "",          // aiProvider
                        "",                   // agent
                        "",                   // eventType
                        "",                   // subType
                        "",                   // engine
                        "",                   // problemSeverity
                        scanType,             // scanType
                        status,               // status
                        totalCount            // totalCount
                );


            } catch (Exception e) {
                LOGGER.warn(format("Telemetry: Failed to log detection telemetry for %s", scanType), e);
            }
        });
    }

    /**
     * Logs user action for scan issue remediation activities.
     *
     * @param scanIssue the scan issue being acted upon
     * @param actionSubType the specific action sub-type for telemetry
     * @param actionName the action name for logging purposes
     */
    private static void logUserAction(ScanIssue scanIssue, String actionSubType, String actionName) {
        if (Objects.isNull(scanIssue)) {
            LOGGER.warn("Telemetry: Cannot log " + actionName + " action - scan issue is null");
            return;
        }

        String engine = mapScanEngineToTelemetryEngine(scanIssue.getScanEngine());
        String severity = normalizeSeverity(scanIssue.getSeverity());

        setUserEventDataForLogs(EVENT_TYPE_CLICK, actionSubType, engine, severity);
    }

    /**
     * Logs user action for "Fix with CxOne Assist".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logFixWithCxOneAssistAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_FIX_WITH_AI_CHAT, "Fix with CxOne Assist");
    }

    /**
     * Logs user action for "View Details".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logViewDetailsAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_VIEW_DETAILS, "View Details");
    }

    /**
     * Logs user action for "Ignore Package" (for future implementation).
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logIgnorePackageAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_IGNORE_PACKAGE, "Ignore Package");
    }

    /**
     * Logs user action for "Ignore All" (for future implementation).
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logIgnoreAllAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_IGNORE_ALL, "Ignore All");
    }

    /**
     * Logs scan results for telemetry.
     *
     * @param engineName the engine name (e.g., "Oss", "Secrets", "IaC", "Asca", "Containers")
     * @param scanIssues list of scan issues found during the scan
     */
    public static void logScanResults(String engineName, List<ScanIssue> scanIssues) {
        if (Objects.isNull(scanIssues) || scanIssues.isEmpty()) {
            LOGGER.debug(format("Telemetry: No scan issues to log for engine: %s", engineName));
            return;
        }

        // Count issues by severity
        Map<String, Long> severityMap = scanIssues.stream()
                .collect(Collectors.groupingBy(
                        issue -> normalizeSeverity(issue.getSeverity()),
                        Collectors.counting()
                ));

        LOGGER.debug(format("Telemetry: Logging scan detection results for %s - total issues: %d",
                engineName, scanIssues.size()));

        // Log each severity level separately
        for (String severity : List.of(Constants.CRITICAL_SEVERITY, Constants.HIGH_SEVERITY,
                Constants.MEDIUM_SEVERITY, Constants.LOW_SEVERITY, Constants.UNKNOWN)) {
            Long count = severityMap.getOrDefault(severity, 0L);

            if (count > 0) {
                setUserEventDataForDetectionLogs(engineName, severity, count.intValue());
            }
        }
    }

    /**
     * Helper method to log scan results based on scan result and scan engine type.
     *
     * @param scanResult the scan result containing issues
     * @param scanEngine the engine type that performed the scan
     */
    public static void logScanResults(ScanResult<?> scanResult, ScanEngine scanEngine) {
        if (Objects.isNull(scanResult) || Objects.isNull(scanEngine)) {
            return;
        }

        List<ScanIssue> scanIssues = scanResult.getIssues();
        if (Objects.isNull(scanIssues) || scanIssues.isEmpty()) {
            return;
        }

        String engineName = mapScanEngineToTelemetryEngine(scanEngine);
        logScanResults(engineName, scanIssues);
    }

    /**
     * Maps ScanEngine enum to telemetry engine string.
     */
    private static String mapScanEngineToTelemetryEngine(ScanEngine scanEngine) {
        if (Objects.isNull(scanEngine)) {
            return ENGINE_OSS; // default fallback
        }

        switch (scanEngine) {
            case OSS:
                return ENGINE_OSS;
            case SECRETS:
                return ENGINE_SECRETS;
            case IAC:
                return ENGINE_IAC;
            case ASCA:
                return ENGINE_ASCA;
            case CONTAINERS:
                return ENGINE_CONTAINERS;
            default:
                return ENGINE_OSS; // default fallback
        }
    }


    /**
     * Normalizes severity strings to match the existing Constants format.
     * Uses Constants.CRITICAL_SEVERITY, Constants.HIGH_SEVERITY, etc.
     */
    private static String normalizeSeverity(String severity) {
        if (Objects.isNull(severity) || severity.trim().isEmpty()) {
            return Constants.UNKNOWN;
        }

        String normalizedSeverity = severity.toLowerCase().trim();
        switch (normalizedSeverity) {
            case "critical":
                return Constants.CRITICAL_SEVERITY;
            case "high":
                return Constants.HIGH_SEVERITY;
            case "medium":
                return Constants.MEDIUM_SEVERITY;
            case "low":
                return Constants.LOW_SEVERITY;
            case "malicious":
                return Constants.MALICIOUS_SEVERITY;
            default:
                return Constants.UNKNOWN;
        }
    }

    /**
     * Gets the agent name based on the current IDE.
     * Uses Constants.JET_BRAINS_AGENT_NAME as base.
     */
    private static String getAgentName() {
        try {
            ApplicationInfo appInfo = ApplicationInfo.getInstance();
            if (appInfo != null) {
                String appName = appInfo.getVersionName();
                if (appName != null) {
                    return Constants.JET_BRAINS_AGENT_NAME + " " + appName.trim();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Telemetry: Could not determine specific IDE name, using default", e);
        }

        return Constants.JET_BRAINS_AGENT_NAME;
    }
}