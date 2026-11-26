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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.devassist.telemetry.TelemetryConstants.*;
import static java.lang.String.format;

/**
 * Sends the Dev Assist analytics events (install, login, scan, remediation and finding actions) through the CLI wrapper.
 */
public final class TelemetryService {

    private static final Logger LOGGER = Utils.getLogger(TelemetryService.class);

    // AI Provider and Agent constants
    private static final String AI_PROVIDER = AI_PROVIDER_COPILOT;

    // Engine Names
    private static final String ENGINE_OSS = "Oss";
    private static final String ENGINE_SECRETS = "Secrets";
    private static final String ENGINE_IAC = "IaC";
    private static final String ENGINE_ASCA = "Asca";
    private static final String ENGINE_CONTAINERS = "Containers";

    private static final String THREAD_NAME = "Checkmarx-Telemetry";
    private static final int MAX_QUEUED_EVENTS = 200;

    // One thread: each event starts a CLI process, and login, pending failures and install must stay in order.
    private static final Executor DEFAULT_EXECUTOR = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(MAX_QUEUED_EVENTS), runnable -> {
                Thread thread = new Thread(runnable, THREAD_NAME);
                thread.setDaemon(true);
                return thread;
            }, (runnable, pool) -> LOGGER.debug("Telemetry: queue full, event dropped"));

    private static volatile Executor executor = DEFAULT_EXECUTOR;
    private static volatile boolean legacyFormat = false;

    @FunctionalInterface
    interface LegacySender {
        void send(CxWrapper wrapper) throws Exception;
    }

    // Private constructor to prevent instantiation - using static methods
    private TelemetryService() {
    }

    // ------------------------------------------------------------------ install

    /**
     * Creates the installation id if missing. Must run before the first login saves any settings,
     * otherwise a fresh install is taken for an upgrade.
     */
    public static synchronized void ensureInstallationId() {
        try {
            PropertiesComponent properties = PropertiesComponent.getInstance();
            if (StringUtils.isNotBlank(properties.getValue("checkmarx.telemetry.installationId"))) {
                return;
            }
            boolean upgraded = isExistingInstallation(GlobalSettingsState.getInstance());
            properties.setValue("checkmarx.telemetry.installationId", generateInstallationId(getIdeName()));
            properties.setValue("checkmarx.telemetry.installationTimestamp", Instant.now().toString());
            if (!upgraded) {
                properties.setValue("checkmarx.telemetry.installEventPending", "true");
            }
        } catch (Exception e) {
            LOGGER.debug("Telemetry: installation id could not be created", e);
        }
    }

    /**
     * Runs at every project start; sends whatever is still pending when the user is logged in.
     */
    public static void startInstallTracking() {
        runAsync(() -> {
            ensureInstallationId();
            GlobalSettingsState settings = GlobalSettingsState.getInstance();
            if (settings != null && settings.isAuthenticated()) {
                flushPendingLoginFailures();
                flushPendingInstallEvent();
            }
        });
    }

    private static void flushPendingInstallEvent() {
        PropertiesComponent properties = PropertiesComponent.getInstance();
        String installationId = properties.getValue("checkmarx.telemetry.installationId");
        if (!"true".equals(properties.getValue("checkmarx.telemetry.installEventPending")) || StringUtils.isBlank(installationId)) {
            return;
        }
        String timestamp = properties.getValue("checkmarx.telemetry.installationTimestamp");
        if (deliver(withCommonFields(TelemetryEventFactory.installEvent(installationId, timestamp)), null)) {
            properties.unsetValue("checkmarx.telemetry.installEventPending");
        }
    }

    static boolean isExistingInstallation(GlobalSettingsState settings) {
        return settings != null && (settings.isAuthenticated()
                || StringUtils.isNotBlank(settings.getBaseUrl())
                || StringUtils.isNotBlank(settings.getTenant())
                || settings.isMcpStatusChecked()
                || settings.isWelcomeShown()
                || StringUtils.isNotBlank(settings.getAiAgent()));
    }

    static String generateInstallationId(String ideName) {
        String slug = Objects.toString(ideName, "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return (slug.isEmpty() ? "jetbrains" : slug) + "-" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------ login

    /**
     * Logs an interactive login, never the silent check at startup. A failed login cannot be sent without a token,
     * so it is kept and sent after the next successful login.
     */
    public static void logLoginEvent(String authMethod, boolean success, String reason) {
        if (!success) {
            String attemptTime = Instant.now().toString();
            runAsync(() -> recordLoginFailure(authMethod, attemptTime, reason));
            return;
        }
        runAsync(() -> {
            deliver(withCommonFields(TelemetryEventFactory.loginEvent(authMethod, true, null, Instant.now().toString())), null);
            flushPendingLoginFailures();
            flushPendingInstallEvent();
        });
    }

    // Kept as "authMethod<TAB>attemptTime<TAB>reason", newest 10 only. Read and written on the telemetry thread only.
    private static void recordLoginFailure(String authMethod, String attemptTime, String reason) {
        PropertiesComponent properties = PropertiesComponent.getInstance();
        List<String> failures = new ArrayList<>(pendingLoginFailures(properties));
        failures.add(String.join("\t", Objects.toString(authMethod, ""), attemptTime, TelemetryEventFactory.sanitizeText(reason)));
        while (failures.size() > 10) {
            failures.remove(0);
        }
        properties.setList("checkmarx.telemetry.pendingLoginFailures", failures);
        LOGGER.info(format("Telemetry: failed %s login kept, it is sent after the next successful login", authMethod));
    }

    private static void flushPendingLoginFailures() {
        PropertiesComponent properties = PropertiesComponent.getInstance();
        List<String> failures = new ArrayList<>(pendingLoginFailures(properties));
        if (failures.isEmpty()) {
            return;
        }
        while (!failures.isEmpty()) {
            String[] parts = failures.get(0).split("\t", 3);
            if (parts.length == 3
                    && !deliver(withCommonFields(TelemetryEventFactory.loginEvent(parts[0], false, parts[2], parts[1])), null)) {
                break;
            }
            failures.remove(0);
        }
        properties.setList("checkmarx.telemetry.pendingLoginFailures", failures);
    }

    private static List<String> pendingLoginFailures(PropertiesComponent properties) {
        List<String> failures = properties.getList("checkmarx.telemetry.pendingLoginFailures");
        return Objects.isNull(failures) ? List.of() : failures;
    }

    // ------------------------------------------------------------------ scan

    /**
     * Helper method to log scan results based on scan result and scan engine type.
     *
     * @param scanResult the scan result containing issues
     * @param scanEngine the engine type that performed the scan
     * @param filePath   the scanned file
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

    public static void logScanFailure(ScanEngine scanEngine, String filePath, Throwable error) {
        if (Objects.isNull(scanEngine)) {
            return;
        }
        String engineName = mapScanEngineToTelemetryEngine(scanEngine);
        sendAsync(() -> TelemetryEventFactory.scanFailureEvent(engineName, error, RepositoryUrlResolver.resolve(filePath)), null);
    }

    public static void logScanFailureReason(ScanEngine scanEngine, String filePath, String reason) {
        if (Objects.isNull(scanEngine)) {
            return;
        }
        String engineName = mapScanEngineToTelemetryEngine(scanEngine);
        sendAsync(() -> TelemetryEventFactory.scanFailureEventWithReason(engineName, reason, RepositoryUrlResolver.resolve(filePath)), null);
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
     * Logs user action for "Fix with CxOne Assist".
     *
     * @param scanIssue     the scan issue being acted upon
     * @param agent         the AI agent that received the prompt
     * @param failureReason null when the prompt was delivered
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
     * Logs user action for scan issue remediation activities.
     *
     * @param scanIssue     the scan issue being acted upon
     * @param actionSubType the specific action sub-type for telemetry
     * @param actionName    the action name for logging purposes
     */
    private static void logUserAction(ScanIssue scanIssue, String actionSubType, String actionName) {
        if (Objects.isNull(scanIssue)) {
            LOGGER.warn("Telemetry: Cannot log " + actionName + " action - scan issue is null");
            return;
        }
        String engine = mapScanEngineToTelemetryEngine(scanIssue.getScanEngine());
        String severity = normalizeSeverity(scanIssue.getSeverity());
        String provider = getAIAgentProvider();
        String vulnerabilityId = getVulnerabilityId(scanIssue);
        sendAsync(() -> TelemetryEventFactory.clickEvent(actionSubType, engine, analyticsSeverity(scanIssue), provider,
                        vulnerabilityId, RepositoryUrlResolver.resolve(scanIssue.getFilePath())),
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
     * Logs user action for "Ignore this vulnerability".
     *
     * @param scanIssue the scan issue being acted upon
     */
    public static void logIgnorePackageAction(ScanIssue scanIssue) {
        logUserAction(scanIssue, SUB_TYPE_IGNORE, "Ignore");
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

    private static void runAsync(Runnable task) {
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.debug("Telemetry: failed to send event", e);
            }
        });
    }

    private static void sendAsync(Supplier<TelemetryEvent.TelemetryEventBuilder> eventSupplier, LegacySender legacySender) {
        runAsync(() -> deliver(withCommonFields(eventSupplier.get()), legacySender));
    }

    /**
     * Returns true only when the CLI accepted the event. Never throws. A CLI without the analytics flags
     * switches the session to the legacy format.
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

    static String analyticsSeverity(ScanIssue issue) {
        String label = TelemetryEventFactory.toSeverityLabel(issue.getSeverity());
        return label == null ? "" : label;
    }

    private static boolean isUnknownFlagError(CxException e) {
        String message = Objects.isNull(e.getMessage()) ? "" : e.getMessage().toLowerCase();
        return message.contains(UNKNOWN_FLAG_ERROR) && ANALYTICS_FLAGS.stream().anyMatch(message::contains);
    }

    static void resetLegacyFormat() {
        legacyFormat = false;
    }

    static boolean isLegacyFormat() {
        return legacyFormat;
    }

    /**
     * Tests pass a direct executor to run events on the calling thread; null restores the default.
     */
    static void setExecutorForTests(Executor testExecutor) {
        executor = Objects.isNull(testExecutor) ? DEFAULT_EXECUTOR : testExecutor;
    }

    static TelemetryEvent withCommonFields(TelemetryEvent.TelemetryEventBuilder builder) {
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
     * ASCA rule id, IaC similarity id, Secrets title, Containers image:tag, OSS package@version.
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
            if (settingsState != null) {
                return toAiProviderName(AiAgent.fromSettingsValue(settingsState.getAiAgent()));
            }
            return AI_PROVIDER;
        } catch (Exception e) {
            LOGGER.debug("Telemetry: Could not determine AI agent provider, using default", e);
            return AI_PROVIDER;
        }
    }
}
