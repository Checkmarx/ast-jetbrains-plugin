package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.ast.telemetry.TelemetryEvent;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.PluginVersionProvider;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import com.checkmarx.intellij.devassist.aiagents.AiAgent;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("TelemetryService events")
class TelemetryServiceEventsTest {

    private static final String AST_PLUGIN_ID = "com.checkmarx.checkmarx-ast-jetbrains-plugin";
    private static final String REPO = "https://github.com/acme/app";

    private final List<TelemetryEvent> sent = new ArrayList<>();
    private CxWrapper wrapper;
    private GlobalSettingsState settings;
    private MockedStatic<CxWrapperFactory> factoryMock;
    private MockedStatic<ApplicationInfo> appInfoMock;
    private MockedStatic<PluginVersionProvider> versionMock;
    private MockedStatic<PluginManager> pluginManagerMock;
    private MockedStatic<RepositoryUrlResolver> repoMock;
    private MockedStatic<GlobalSettingsState> settingsMock;

    @BeforeEach
    void setUp() throws Exception {
        TelemetryService.setExecutorForTests(Runnable::run);
        TelemetryService.resetLegacyFormat();

        wrapper = mock(CxWrapper.class);
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenAnswer(inv -> {
            sent.add(inv.getArgument(0));
            return "";
        });
        factoryMock = mockStatic(CxWrapperFactory.class);
        factoryMock.when(CxWrapperFactory::build).thenReturn(wrapper);

        ApplicationInfo info = mock(ApplicationInfo.class);
        when(info.getVersionName()).thenReturn("PyCharm");
        when(info.getFullVersion()).thenReturn("2026.2");
        appInfoMock = mockStatic(ApplicationInfo.class);
        appInfoMock.when(ApplicationInfo::getInstance).thenReturn(info);

        versionMock = mockStatic(PluginVersionProvider.class);
        versionMock.when(PluginVersionProvider::getPluginVersion).thenReturn("2.3.5");

        pluginManagerMock = mockStatic(PluginManager.class);
        pluginManagerMock.when(() -> PluginManager.getPluginByClassName(anyString())).thenReturn(PluginId.getId(AST_PLUGIN_ID));

        repoMock = mockStatic(RepositoryUrlResolver.class);
        repoMock.when(() -> RepositoryUrlResolver.resolve(any())).thenReturn(REPO);

        settings = mock(GlobalSettingsState.class);
        when(settings.getAiAgent()).thenReturn("JETBRAINS_AI_ASSISTANT");
        settingsMock = mockStatic(GlobalSettingsState.class);
        settingsMock.when(GlobalSettingsState::getInstance).thenReturn(settings);
    }

    @AfterEach
    void tearDown() {
        settingsMock.close();
        repoMock.close();
        pluginManagerMock.close();
        versionMock.close();
        appInfoMock.close();
        factoryMock.close();
        TelemetryService.setExecutorForTests(null);
        TelemetryService.resetLegacyFormat();
    }

    @Test
    @DisplayName("every event carries the common fields")
    void everyEvent_carriesCommonFields() {
        TelemetryService.logViewDetailsAction(ossIssue("High"));

        TelemetryEvent event = single();
        assertEquals("Jetbrains PyCharm", event.getAgent());
        assertEquals("2026.2", event.getAgentVersion());
        assertEquals("2.3.5", event.getCxPluginVersion());
        assertEquals("IDE", event.getPluginType());
        assertEquals("Checkmarx One Assist", event.getEventAttributes().get("pluginName"));
    }

    @Test
    @DisplayName("events of the standalone plugin are named Checkmarx One Developer Assist")
    void devAssistPlugin_pluginName() {
        pluginManagerMock.when(() -> PluginManager.getPluginByClassName(anyString()))
                .thenReturn(PluginId.getId(TelemetryConstants.DEV_ASSIST_PLUGIN_ID));

        TelemetryService.logViewDetailsAction(ossIssue("High"));

        assertEquals("Checkmarx One Developer Assist", single().getEventAttributes().get("pluginName"));
    }

    @Test
    @DisplayName("click event: finding context and the configured AI agent name")
    void clickEvent_findingContext() {
        TelemetryService.logIgnorePackageAction(ossIssue("Malicious"));

        TelemetryEvent event = single();
        assertEquals("click", event.getEventType());
        assertEquals("Ignore", event.getSubType());
        assertEquals("Oss", event.getEngine());
        assertEquals("Critical", event.getProblemSeverity());
        assertEquals("JetBrains AI Assistant", event.getAiProvider());
        assertEquals("Vulnerability ignored successfully.", event.getStatusResult());
        assertEquals(REPO, event.getRepoUrl());
        assertEquals("lodash@4.17.20", event.getEventAttributes().get("vulnerabilityId"));
    }

    @Test
    @DisplayName("remediation event: provider actually used, success and failure")
    void remediationEvent_successAndFailure() {
        TelemetryService.logRemediation(ossIssue("High"), AiAgent.JETBRAINS_AI_ASSISTANT, null);
        TelemetryService.logRemediation(ossIssue("High"), AiAgent.GITHUB_COPILOT, "chat did not open");

        assertEquals(2, sent.size());
        TelemetryEvent ok = sent.get(0);
        assertEquals("fixWithAIChat", ok.getSubType());
        assertEquals("JetBrains AI Assistant", ok.getAiProvider());
        assertEquals("success", ok.getStatus());
        assertEquals("DevAssist", ok.getEventAttributes().get("remediationMode"));
        TelemetryEvent failed = sent.get(1);
        assertEquals("Copilot", failed.getAiProvider());
        assertEquals("failure", failed.getStatus());
        assertEquals("Remediation failed. Reason: chat did not open, Error Code: N/A", failed.getStatusResult());
    }

    @Test
    @DisplayName("scan: one event with the severity counts")
    void scanEvent_oneEventWithCounts() {
        TelemetryService.logScanResults(scanResult("Critical", "Low", "low"), ScanEngine.SECRETS, "/repo/app.py");

        TelemetryEvent event = single();
        assertEquals("Scan", event.getEventType());
        assertEquals("scantriggerd", event.getSubType());
        assertEquals("Secrets", event.getEngine());
        assertEquals("Critical", event.getProblemSeverity());
        assertEquals(3, event.getTotalCount());
        assertEquals("Critical=1, High=0, Medium=0, Low=2, Info=0", event.getStatusResult());
        assertEquals("v2", event.getEventAttributes().get("apiVersion"));
    }

    @Test
    @DisplayName("clean scan is sent too")
    void cleanScan_isSent() {
        TelemetryService.logScanResults(scanResult(), ScanEngine.OSS, "/repo/pom.xml");

        TelemetryEvent event = single();
        assertEquals(0, event.getTotalCount());
        assertEquals("success", event.getStatus());
        assertEquals("Critical=0, High=0, Medium=0, Low=0, Info=0", event.getStatusResult());
    }

    @Test
    @DisplayName("scan failure reason: local paths are removed")
    void scanFailureReason_pathsRemoved() {
        TelemetryService.logScanFailureReason(ScanEngine.ASCA, "/repo/app.py",
                "engine missing at C:\\Users\\jdoe\\AppData\\Local\\cx\\asca.exe");

        TelemetryEvent event = single();
        assertEquals("failure", event.getStatus());
        assertEquals("Asca", event.getEngine());
        assertEquals("Scan failed. Reason: engine missing at <path>, Error Code: N/A", event.getStatusResult());
    }

    @Test
    @DisplayName("old CLI: scan falls back to one legacy event per severity")
    void unknownFlag_scanFallsBackToLegacy() throws Exception {
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenThrow(new CxException(1, "Error: unknown flag: --agent-version"));

        TelemetryService.logScanResults(scanResult("Critical", "Critical", "High"), ScanEngine.SECRETS, "/repo/app.py");

        verify(wrapper).telemetryAIEvent("", "", "", "", "", "", "Secrets", "Critical", 2);
        verify(wrapper).telemetryAIEvent("", "", "", "", "", "", "Secrets", "High", 1);
        assertTrue(TelemetryService.isLegacyFormat());
    }

    @Test
    @DisplayName("successful login: login event, then the kept failures, then the install event")
    void loginSuccess_sendsLoginFailuresThenInstall() {
        TelemetryInstallationState state = new TelemetryInstallationState();
        state.setInstallationId("pycharm-abc");
        state.setInstallationTimestamp("2026-11-26T08:00:00Z");
        state.setInstallEventPending(true);
        state.setPendingLoginFailures(new ArrayList<>(List.of(
                new PendingLoginFailure("APIKey", java.time.Instant.now().toString(), "invalid key"))));

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            TelemetryService.logLoginEvent(TelemetryConstants.AUTH_METHOD_OAUTH, true, null);
        }

        assertEquals(List.of("authentication/success", "authentication/failure", "install/success"),
                sent.stream().map(e -> e.getEventType() + "/" + e.getStatus()).collect(Collectors.toList()));
        assertEquals("OAuth", sent.get(0).getEventAttributes().get("authMethod"));
        assertEquals("APIKey", sent.get(1).getEventAttributes().get("authMethod"));
        assertTrue(state.getPendingLoginFailures().isEmpty());
        assertFalse(state.isInstallEventPending());
    }

    @Test
    @DisplayName("start-up: pending install event is sent only when logged in")
    void startInstallTracking_sendsOnlyWhenAuthenticated() {
        TelemetryInstallationState state = new TelemetryInstallationState();
        state.setInstallationId("pycharm-abc");
        state.setInstallEventPending(true);

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            when(settings.isAuthenticated()).thenReturn(false);
            TelemetryService.startInstallTracking();
            assertTrue(sent.isEmpty());

            when(settings.isAuthenticated()).thenReturn(true);
            TelemetryService.startInstallTracking();
        }

        assertEquals("install", single().getEventType());
    }

    @Test
    @DisplayName("ensureInstallationId creates the id of a fresh install and never throws")
    void ensureInstallationId_freshInstall() {
        TelemetryInstallationState state = new TelemetryInstallationState();
        when(settings.getAiAgent()).thenReturn("");

        try (MockedStatic<TelemetryInstallationState> stateMock = mockStatic(TelemetryInstallationState.class)) {
            stateMock.when(TelemetryInstallationState::getInstance).thenReturn(state);

            TelemetryService.ensureInstallationId();

            assertTrue(state.getInstallationId().startsWith("pycharm-"));
            assertTrue(state.isInstallEventPending());

            stateMock.when(TelemetryInstallationState::getInstance).thenThrow(new IllegalStateException("no application"));
            assertDoesNotThrow(TelemetryService::ensureInstallationId);
        }
    }

    private TelemetryEvent single() {
        assertEquals(1, sent.size(), "exactly one event expected");
        return sent.get(0);
    }

    private static ScanIssue ossIssue(String severity) {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        when(issue.getSeverity()).thenReturn(severity);
        when(issue.getTitle()).thenReturn("lodash");
        when(issue.getPackageVersion()).thenReturn("4.17.20");
        when(issue.getFilePath()).thenReturn("/repo/package.json");
        return issue;
    }

    @SuppressWarnings("unchecked")
    private static ScanResult<Object> scanResult(String... severities) {
        List<ScanIssue> issues = new ArrayList<>();
        for (String severity : severities) {
            ScanIssue issue = mock(ScanIssue.class);
            when(issue.getSeverity()).thenReturn(severity);
            issues.add(issue);
        }
        ScanResult<Object> result = mock(ScanResult.class);
        when(result.getIssues()).thenReturn(issues);
        return result;
    }
}
