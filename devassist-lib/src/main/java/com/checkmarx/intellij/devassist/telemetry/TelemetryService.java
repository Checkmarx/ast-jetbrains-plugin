package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.PluginVersionProvider;
import com.checkmarx.intellij.common.utils.Utils;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.devassist.telemetry.TelemetryConstants.*;
import static java.lang.String.format;

/**
 * Single entry point for the Dev Assist analytics events: install, login, scan and remediation (AST-184118),
 * plus the click events of the findings (view details, ignore).
 * <p>
 * Every event gets the common fields (agent, agent version, plugin version, plugin type and plugin name) and is sent
 * through the CLI wrapper on a background thread. Sending never throws, never blocks the caller and never shows
 * anything to the user. The events are emitted whatever the tenant flags are, the server decides what to keep.
 * <p>
 * A CLI that does not know the analytics flags rejects them with "unknown flag". In that case the scan and click events
 * are sent in the previous format for the rest of the session, so the existing telemetry keeps working.
 */
public final class TelemetryService {

    private static final Logger LOGGER = Utils.getLogger(TelemetryService.class);

    // AI Provider and Agent constants
    private static final String AI_PROVIDER = AI_PROVIDER_COPILOT;

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

    private static volatile boolean legacyFormat = false;

    /**
     * Sends an event in the format of the CLI that does not know the analytics flags.
     */
    @FunctionalInterface
    interface LegacySender {
        void send(CxWrapper wrapper) throws Exception;
    }

    // Private constructor to prevent instantiation - using static methods
    private TelemetryService() {
    }

    // ------------------------------------------------------------------ install

    /**
     * Creates the installation id on the first start and sends the pending install event when the user is already logged in.
     * Called once at IDE startup.
     */
    public static void startInstallTracking() {
        CompletableFuture.runAsync(() -> {
            try {
                InstallationTracker.ensureInstallationId(getIdeName());
                GlobalSettingsState settings = GlobalSettingsState.getInstance();
                if (settings != null && settings.isAuthenticated()) {
                    flushPendingInstallEvent();
                }
            } catch (Exception e) {
                LOGGER.debug("Telemetry: install tracking failed", e);
            }
        });
    }

    private static void flushPendingInstallEvent() {
        InstallationTracker.flushPendingInstallEvent(builder -> deliver(withCommonFields(builder), null));
    }

    // ------------------------------------------------------------------ login

    /**
     * Logs an interactive login. Call it only for sign-ins done by the user, never for the silent check at startup.
     * A successful login also sends the pending install event. A failed login is sent best-effort: the CLI
     * delivers it only while a valid credential is still stored.
     *
     * @param authMethod {@link TelemetryConstants#AUTH_METHOD_OAUTH} or {@link TelemetryConstants#AUTH_METHOD_API_KEY}
     * @param reason     failure reason, ignored on success
     */
    public static void logLoginEvent(String authMethod, boolean success, String reason) {
        CompletableFuture.runAsync(() -> {
            try {
                deliver(withCommonFields(TelemetryEventFactory.loginEvent(authMethod, success, reason, Instant.now().toString())), null);
                if (success) {
                    flushPendingInstallEvent();
                }
            } catch (Exception e) {
                LOGGER.debug("Telemetry: login event failed", e);
            }
        });
    }

    // ------------------------------------------------------------------ scan

    /**
     * Logs one scan event for a finished scan, also when it found nothing.
     *
     * @param scanResult the result of the scan
     * @param scanEngine the engine that performed the scan
     * @param filePath   the scanned file, used to find the repository URL
     */
    public static void logScanResults(ScanResult<?> scanResult, ScanEngine scanEngine, String filePath) {
        if (Objects.isNull(scanResult) || Objects.isNull(scanEngine)) {
            return;
        }
        List<ScanIssue> issues = scanResult.getIssues();
        List<String> severities = Objects.isNull(issues) ? List.of() : issues.stream()
                .map(issue -> Objects.isNull(issue) ? null : issue.getSeverity())
                .collect(Collectors.toList());
        String engineName = mapScanEngineToTelemetryEngine(scanEngine);

        LOGGER.debug(format("Telemetry: Logging scan results for %s - total issues: %d", engineName, severities.size()));
        sendAsync(() -> TelemetryEventFactory.scanEvent(engineName, severities, RepositoryUrlResolver.resolve(filePath)),
                wrapper -> sendLegacyScan(wrapper, engineName, severities));
    }

    /**
     * Logs a scan that failed.
     */
    public static void logScanFailure(ScanEngine scanEngine, String filePath, Throwable error) {
        if (Objects.isNull(scanEngine)) {
            return;
        }
        String engineName = mapScanEngineToTelemetryEngine(scanEngine);
        sendAsync(() -> TelemetryEventFactory.scanFailureEvent(engineName, error, RepositoryUrlResolver.resolve(filePath)), null);
    }

    private static void sendLegacyScan(CxWrapper wrapper, String engineName, List<String> severities) throws Exception {
        Map<String, Long> severityMap = severities.stream()
                .collect(Collectors.groupingBy(TelemetryService::normalizeSeverity, Collectors.counting()));
        for (String severity : List.of(Constants.CRITICAL_SEVERITY, Constants.HIGH_SEVERITY,
                Constants.MEDIUM_SEVERITY, Constants.LOW_SEVERITY, Constants.UNKNOWN)) {
            long count = severityMap.getOrDefault(severity, 0L);
            if (count > 0) {
                wrapper.telemetryAIEvent("", "", "", "", "", "", engineName, severity, (int) count);
            }
        }
    }

    // ------------------------------------------------------------------ remediation and clicks

    /**
     * Logs a "Fix with AI" action once the prompt reached the AI agent, or failed to reach it.
     *
     * @param scanIssue     the issue being fixed
     * @param agent         the AI agent that was used
     * @param failureReason why the prompt could not be delivered, null on success
     */
    public static void logRemediation(ScanIssue scanIssue, AiAgent agent, String failureReason) {
        if (Objects.isNull(scanIssue)) {
            LOGGER.warn("Telemetry: Cannot log Fix with CxOne Assist action - scan issue is null");
            return;
        }
        String engine = mapScanEngineToTelemetryEngine(scanIssue.getScanEngine());
        String severity = normalizeSeverity(scanIssue.getSeverity());
        String provider = toAiProviderName(agent);
        String vulnerabilityId = getVulnerabilityId(scanIssue);
        sendAsync(() -> TelemetryEventFactory.remediationEvent(engine, analyticsSeverity(scanIssue), provider, vulnerabilityId, failureReason,
                        RepositoryUrlResolver.resolve(scanIssue.getFilePath()), SUB_TYPE_FIX_WITH_AI_CHAT),
                wrapper -> wrapper.telemetryAIEvent(provider, "", TYPE_CLICK, SUB_TYPE_FIX_WITH_AI_CHAT, engine, severity, "", "", 0));
    }

    /**
     * Logs a click on a finding action.
     */
    private static void logUserAction(ScanIssue scanIssue, String actionSubType, String actionName) {
        if (Objects.isNull(scanIssue)) {
            LOGGER.warn("Telemetry: Cannot log " + actionName + " action - scan issue is null");
            return;
        }
        String engine = mapScanEngineToTelemetryEngine(scanIssue.getScanEngine());
        String severity = normalizeSeverity(scanIssue.getSeverity());
        String provider = getAIAgentProvider();
        sendAsync(() -> TelemetryEventFactory.clickEvent(actionSubType, engine, analyticsSeverity(scanIssue), provider,
                        RepositoryUrlResolver.resolve(scanIssue.getFilePath())),
                wrapper -> wrapper.telemetryAIEvent(provider, "", TYPE_CLICK, actionSubType, engine, severity, "", "", 0));
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
     * Logs user action for "Ignore Package".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logIgnorePackageAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_IGNORE_PACKAGE, "Ignore Package");
    }

    /**
     * Logs user action for "Ignore All".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logIgnoreAllAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_IGNORE_ALL, "Ignore All");
    }

    // ------------------------------------------------------------------ delivery

    private static void sendAsync(Supplier<TelemetryEvent.TelemetryEventBuilder> eventSupplier, LegacySender legacySender) {
        CompletableFuture.runAsync(() -> {
            try {
                deliver(withCommonFields(eventSupplier.get()), legacySender);
            } catch (Exception e) {
                LOGGER.debug("Telemetry: failed to send event", e);
            }
        });
    }

    /**
     * Sends the event and returns true only when the CLI accepted it. Never throws.
     * When the CLI does not know the analytics flags, the legacy sender is used from then on.
     */
    static boolean deliver(TelemetryEvent event, LegacySender legacySender) {
        try {
            if (!legacyFormat) {
                CxWrapper wrapper = CxWrapperFactory.build();
                try {
                    wrapper.telemetryAIEvent(event);
                    LOGGER.info(format("Telemetry: sent event type=%s subType=%s engine=%s status=%s",
                            event.getEventType(), event.getSubType(), event.getEngine(), event.getStatus()));
                    return true;
                } catch (CxException e) {
                    if (!isUnknownFlagError(e)) {
                        LOGGER.debug(format("Telemetry: event %s/%s was not delivered: %s", event.getEventType(), event.getSubType(),
                                TelemetryEventFactory.sanitizeText(e.getMessage())));
                        return false;
                    }
                    legacyFormat = true;
                    LOGGER.info("Telemetry: the CLI does not support the analytics flags, using the previous event format");
                }
            }
            if (Objects.isNull(legacySender)) {
                return false;
            }
            legacySender.send(CxWrapperFactory.build());
            return true;
        } catch (Exception e) {
            LOGGER.debug(format("Telemetry: event %s/%s failed", event.getEventType(), event.getSubType()), e);
            return false;
        }
    }

    /**
     * Severity label the analytics service accepts: Critical, High, Medium, Low or Info (Malicious counts as Critical).
     */
    static String analyticsSeverity(ScanIssue issue) {
        String label = TelemetryEventFactory.toSeverityLabel(issue.getSeverity());
        return label == null ? "" : label;
    }

    private static boolean isUnknownFlagError(CxException e) {
        return Objects.nonNull(e.getMessage()) && e.getMessage().toLowerCase().contains(UNKNOWN_FLAG_ERROR);
    }

    /**
     * For unit tests: makes the next event use the analytics flags again.
     */
    static void resetLegacyFormat() {
        legacyFormat = false;
    }

    static boolean isLegacyFormat() {
        return legacyFormat;
    }

    private static TelemetryEvent withCommonFields(TelemetryEvent.TelemetryEventBuilder builder) {
        return builder
                .agent(getAgentName())
                .agentVersion(getAgentVersion())
                .cxPluginVersion(PluginVersionProvider.getPluginVersion())
                .pluginType(PLUGIN_TYPE_IDE)
                .eventAttribute(ATTR_PLUGIN_NAME, getPluginName())
                .build();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Identifier of the finding: ASCA rule id, IaC similarity id, Secrets title, Containers image:tag, OSS package@version.
     */
    static String getVulnerabilityId(ScanIssue issue) {
        String title = Objects.isNull(issue.getTitle()) ? "" : issue.getTitle();
        if (Objects.isNull(issue.getScanEngine())) {
            return title;
        }
        switch (issue.getScanEngine()) {
            case ASCA:
                return Objects.nonNull(issue.getRuleId()) ? String.valueOf(issue.getRuleId()) : title;
            case IAC:
                return Objects.nonNull(issue.getSimilarityId()) && !issue.getSimilarityId().isBlank() ? issue.getSimilarityId() : title;
            case CONTAINERS:
                return Objects.nonNull(issue.getImageTag()) && !issue.getImageTag().isBlank() ? title + ":" + issue.getImageTag() : title;
            case OSS:
                return Objects.nonNull(issue.getPackageVersion()) && !issue.getPackageVersion().isBlank()
                        ? title + "@" + issue.getPackageVersion() : title;
            default:
                return title;
        }
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
        String ideName = getIdeName();
        return ideName.isEmpty() ? Constants.JET_BRAINS_AGENT_NAME : Constants.JET_BRAINS_AGENT_NAME + " " + ideName;
    }

    private static String getIdeName() {
        try {
            ApplicationInfo appInfo = ApplicationInfo.getInstance();
            if (appInfo != null && appInfo.getVersionName() != null) {
                return appInfo.getVersionName().trim();
            }
        } catch (Exception e) {
            LOGGER.debug("Telemetry: Could not determine specific IDE name, using default", e);
        }
        return "";
    }

    private static String getAgentVersion() {
        try {
            ApplicationInfo appInfo = ApplicationInfo.getInstance();
            return appInfo != null && appInfo.getFullVersion() != null ? appInfo.getFullVersion() : "";
        } catch (Exception e) {
            LOGGER.debug("Telemetry: Could not determine the IDE version", e);
            return "";
        }
    }

    /**
     * Which plugin sends the event: CxOne or DevAssist. Both plugins run in the same IDEs, so the agent alone cannot tell them apart.
     * Empty when it cannot be determined.
     */
    private static String getPluginName() {
        try {
            PluginId pluginId = PluginManager.getPluginByClassName(TelemetryService.class.getName());
            if (Objects.isNull(pluginId)) {
                return "";
            }
            return DEV_ASSIST_PLUGIN_ID.equals(pluginId.getIdString()) ? PLUGIN_NAME_DEV_ASSIST : PLUGIN_NAME_CXONE;
        } catch (Exception e) {
            LOGGER.debug("Telemetry: Could not determine the plugin name", e);
            return "";
        }
    }

    /**
     * Name of the AI agent that received the prompt, as shown in the analytics.
     */
    static String toAiProviderName(AiAgent agent) {
        if (Objects.isNull(agent)) {
            return AI_PROVIDER;
        }
        return agent == AiAgent.GITHUB_COPILOT ? AI_PROVIDER_COPILOT : agent.getAgentName();
    }

    /**
     * Get AI Agent from the settings
     *
     * @return String - Ai Agent Provider
     */
    private static String getAIAgentProvider() {
        try {
            GlobalSettingsState settingsState = GlobalSettingsState.getInstance();
            if (settingsState != null && (settingsState.getAiAgent() != null && !settingsState.getAiAgent().isEmpty())) {
                return settingsState.getAiAgent();
            }
            return AI_PROVIDER;
        } catch (Exception e) {
            LOGGER.debug("Telemetry: Could not determine AI agent provider, using default", e);
            return AI_PROVIDER;
        }
    }
}
