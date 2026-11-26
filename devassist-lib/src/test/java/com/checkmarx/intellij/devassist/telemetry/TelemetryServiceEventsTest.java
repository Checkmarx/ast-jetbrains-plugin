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
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.extensions.PluginId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("TelemetryService events")
class TelemetryServiceEventsTest {

    private static final String AST_PLUGIN_ID = "com.checkmarx.checkmarx-ast-jetbrains-plugin";
    private static final String REPO = "https://github.com/acme/app";

    private static final String INSTALLATION_ID = "checkmarx.telemetry.installationId";
    private static final String INSTALLATION_TIMESTAMP = "checkmarx.telemetry.installationTimestamp";
    private static final String INSTALL_PENDING = "checkmarx.telemetry.installEventPending";
    private static final String LOGIN_FAILURES = "checkmarx.telemetry.pendingLoginFailures";

    private final List<TelemetryEvent> sent = new ArrayList<>();
    private final Map<String, Object> stored = new HashMap<>();
    private CxWrapper wrapper;
    private GlobalSettingsState settings;
    private MockedStatic<CxWrapperFactory> factoryMock;
    private MockedStatic<ApplicationInfo> appInfoMock;
    private MockedStatic<PluginVersionProvider> versionMock;
    private MockedStatic<PluginManager> pluginManagerMock;
    private MockedStatic<RepositoryUrlResolver> repoMock;
    private MockedStatic<GlobalSettingsState> settingsMock;
    private MockedStatic<PropertiesComponent> propertiesMock;

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

        PropertiesComponent properties = mock(PropertiesComponent.class);
        when(properties.getValue(anyString())).thenAnswer(inv -> stored.get(inv.<String>getArgument(0)));
        doAnswer(inv -> stored.put(inv.getArgument(0), inv.getArgument(1))).when(properties).setValue(anyString(), anyString());
        doAnswer(inv -> stored.remove(inv.<String>getArgument(0))).when(properties).unsetValue(anyString());
        when(properties.getList(anyString())).thenAnswer(inv -> stored.get(inv.<String>getArgument(0)));
        doAnswer(inv -> stored.put(inv.getArgument(0), new ArrayList<>(inv.<Collection<String>>getArgument(1))))
                .when(properties).setList(anyString(), any());
        propertiesMock = mockStatic(PropertiesComponent.class);
        propertiesMock.when(PropertiesComponent::getInstance).thenReturn(properties);
    }

    @AfterEach
    void tearDown() {
        propertiesMock.close();
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
        stored.put(INSTALLATION_ID, "pycharm-abc");
        stored.put(INSTALLATION_TIMESTAMP, "2026-11-26T08:00:00Z");
        stored.put(INSTALL_PENDING, "true");
        stored.put(LOGIN_FAILURES, new ArrayList<>(List.of("APIKey\t2026-11-26T09:00:00Z\tinvalid key")));

        TelemetryService.logLoginEvent(TelemetryConstants.AUTH_METHOD_OAUTH, true, null);

        assertEquals(List.of("authentication/success", "authentication/failure", "install/success"),
                sent.stream().map(e -> e.getEventType() + "/" + e.getStatus()).collect(Collectors.toList()));
        assertEquals("OAuth", sent.get(0).getEventAttributes().get("authMethod"));
        TelemetryEvent failure = sent.get(1);
        assertEquals("APIKey", failure.getEventAttributes().get("authMethod"));
        assertEquals("2026-11-26T09:00:00Z", failure.getEventAttributes().get("loginTimestamp"));
        assertEquals("Authentication failed. Reason: invalid key, Error Code: N/A", failure.getStatusResult());
        TelemetryEvent install = sent.get(2);
        assertEquals("pycharm-abc", install.getEventAttributes().get("installationId"));
        assertEquals("2026-11-26T08:00:00Z", install.getEventAttributes().get("installationTimestamp"));
        assertEquals(List.of(), failures());
        assertFalse(stored.containsKey(INSTALL_PENDING));
    }

    @Test
    @DisplayName("failed login: kept with a sanitized reason, not sent")
    void loginFailure_isKeptAndNotSent() {
        TelemetryService.logLoginEvent(TelemetryConstants.AUTH_METHOD_OAUTH, false, "apikey=abc123secret rejected");

        assertTrue(sent.isEmpty());
        List<String> kept = failures();
        assertEquals(1, kept.size());
        assertTrue(kept.get(0).startsWith("OAuth\t"), kept.get(0));
        assertFalse(kept.get(0).contains("abc123secret"), kept.get(0));
    }

    @Test
    @DisplayName("failed logins: only the newest 10 are kept")
    void loginFailures_keepsNewestTen() {
        for (int i = 0; i < 12; i++) {
            TelemetryService.logLoginEvent(TelemetryConstants.AUTH_METHOD_API_KEY, false, "attempt " + i);
        }

        List<String> kept = failures();
        assertEquals(10, kept.size());
        assertTrue(kept.get(0).endsWith("\tattempt 2"), kept.get(0));
        assertTrue(kept.get(9).endsWith("\tattempt 11"), kept.get(9));
    }

    @Test
    @DisplayName("kept failures: sending stops at the first one not delivered, the rest wait for the next login")
    void loginFailures_stopAtFirstNotDelivered() throws Exception {
        stored.put(LOGIN_FAILURES, new ArrayList<>(List.of("OAuth\t2026-11-26T09:00:00Z\tfirst",
                "OAuth\t2026-11-26T09:01:00Z\tsecond", "OAuth\t2026-11-26T09:02:00Z\tthird")));
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenAnswer(inv -> {
            TelemetryEvent event = inv.getArgument(0);
            if (event.getStatusResult().contains("second")) {
                throw new CxException(1, "Failed logging the data");
            }
            sent.add(event);
            return "";
        });

        TelemetryService.logLoginEvent(TelemetryConstants.AUTH_METHOD_OAUTH, true, null);

        assertEquals(2, sent.size());
        assertEquals(List.of("OAuth\t2026-11-26T09:01:00Z\tsecond", "OAuth\t2026-11-26T09:02:00Z\tthird"), failures());
    }

    @Test
    @DisplayName("start-up: pending install event is sent only when logged in, and only once")
    void startInstallTracking_sendsOnlyWhenAuthenticated() {
        stored.put(INSTALLATION_ID, "pycharm-abc");
        stored.put(INSTALL_PENDING, "true");

        when(settings.isAuthenticated()).thenReturn(false);
        TelemetryService.startInstallTracking();
        assertTrue(sent.isEmpty());

        when(settings.isAuthenticated()).thenReturn(true);
        TelemetryService.startInstallTracking();
        TelemetryService.startInstallTracking();

        assertEquals("install", single().getEventType());
    }

    @Test
    @DisplayName("install event not delivered: stays pending")
    void installNotDelivered_staysPending() throws Exception {
        stored.put(INSTALLATION_ID, "pycharm-abc");
        stored.put(INSTALL_PENDING, "true");
        when(settings.isAuthenticated()).thenReturn(true);
        when(wrapper.telemetryAIEvent(any(TelemetryEvent.class))).thenThrow(new CxException(1, "Failed logging the data"));

        TelemetryService.startInstallTracking();

        assertEquals("true", stored.get(INSTALL_PENDING));
    }

    @Test
    @DisplayName("ensureInstallationId: a fresh install gets an id and a pending install event")
    void ensureInstallationId_freshInstall() {
        when(settings.getAiAgent()).thenReturn("");

        TelemetryService.ensureInstallationId();

        assertTrue(((String) stored.get(INSTALLATION_ID)).startsWith("pycharm-"));
        assertNotNull(stored.get(INSTALLATION_TIMESTAMP));
        assertEquals("true", stored.get(INSTALL_PENDING));
    }

    @Test
    @DisplayName("ensureInstallationId: an upgrade gets an id but no install event")
    void ensureInstallationId_upgrade() {
        when(settings.getAiAgent()).thenReturn("");
        when(settings.getBaseUrl()).thenReturn("https://ast.checkmarx.net");

        TelemetryService.ensureInstallationId();

        assertNotNull(stored.get(INSTALLATION_ID));
        assertFalse(stored.containsKey(INSTALL_PENDING));
    }

    @Test
    @DisplayName("ensureInstallationId: an existing id is kept, and it never throws")
    void ensureInstallationId_existingIdAndNoApplication() {
        stored.put(INSTALLATION_ID, "pycharm-existing");

        TelemetryService.ensureInstallationId();
        assertEquals("pycharm-existing", stored.get(INSTALLATION_ID));

        propertiesMock.when(PropertiesComponent::getInstance).thenThrow(new IllegalStateException("no application"));
        assertDoesNotThrow(TelemetryService::ensureInstallationId);
    }

    @Test
    @DisplayName("installation id: IDE name in lowercase words, then a UUID")
    void generateInstallationId_format() {
        assertTrue(TelemetryService.generateInstallationId("IntelliJ IDEA")
                .matches("intellij-idea-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        assertTrue(TelemetryService.generateInstallationId(null).startsWith("jetbrains-"));
        assertTrue(TelemetryService.generateInstallationId("***").startsWith("jetbrains-"));
    }

    @SuppressWarnings("unchecked")
    private List<String> failures() {
        return (List<String>) stored.get(LOGIN_FAILURES);
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
