package com.checkmarx.intellij.devassist.test.remediation;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.remediation.CopilotIntegration;
import com.checkmarx.intellij.devassist.remediation.RemediationManager;
import com.checkmarx.intellij.devassist.remediation.prompts.DevAssistFixPrompts;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.QUICK_FIX;
import static org.mockito.Mockito.*;

@DisplayName("RemediationManager unit tests covering all branches")
public class RemediationManagerTest {

    static MockedStatic<ApplicationManager> appManagerMock;
    static Application mockApp;
    static MockedStatic<NotificationGroupManager> notificationGroupManagerMock;
    static NotificationGroupManager mockNotificationGroupManager;
    static NotificationGroup mockNotificationGroup;

    @BeforeAll
    static void setupStaticMocks() {
        // Mock ApplicationManager.getApplication()
        mockApp = mock(Application.class, RETURNS_DEEP_STUBS);
        appManagerMock = mockStatic(ApplicationManager.class, CALLS_REAL_METHODS);
        appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

        // Mock NotificationGroupManager.getInstance()
        mockNotificationGroupManager = mock(NotificationGroupManager.class, RETURNS_DEEP_STUBS);
        notificationGroupManagerMock = mockStatic(NotificationGroupManager.class, CALLS_REAL_METHODS);
        notificationGroupManagerMock.when(NotificationGroupManager::getInstance).thenReturn(mockNotificationGroupManager);

        // Mock NotificationGroup and Notification if needed
        mockNotificationGroup = mock(NotificationGroup.class, RETURNS_DEEP_STUBS);
        when(mockNotificationGroupManager.getNotificationGroup(anyString())).thenReturn(mockNotificationGroup);
//        when(mockNotificationGroup.createNotification(anyString(), anyString(), any(NotificationType.class), any(NotificationListener.class)))
//            .thenReturn(mock(Notification.class));
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (appManagerMock != null) appManagerMock.close();
        if (notificationGroupManagerMock != null) notificationGroupManagerMock.close();
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_OSS_CopySuccess")
    void testFixWithCxOneAssist_OSS_CopySuccess() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistFixPrompts> fixPrompts = mockStatic(DevAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> DevAssistFixPrompts.buildSCARemediationPrompt(
                    anyString(), anyString(), anyString(), anyString())).thenReturn("prompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);

            fixPrompts.verify(() -> DevAssistFixPrompts.buildSCARemediationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getPackageManager()), eq(issue.getSeverity())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("prompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_OSS_CopyFailure")
    void testFixWithCxOneAssist_OSS_CopyFailure() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistFixPrompts> fixPrompts = mockStatic(DevAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> DevAssistFixPrompts.buildSCARemediationPrompt(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("prompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(false);

            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);

            fixPrompts.verify(() -> DevAssistFixPrompts.buildSCARemediationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getPackageManager()), eq(issue.getSeverity())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("prompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_ASCA_Branch")
    void testFixWithCxOneAssist_ASCA_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();
        manager.fixWithCxOneAssist(project, issue, QUICK_FIX);
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_DefaultBranch")
    void testFixWithCxOneAssist_DefaultBranch() {
        Project project = mock(Project.class);
        RemediationManager manager = new RemediationManager();
        for (ScanEngine engine : new ScanEngine[]{ScanEngine.SECRETS, ScanEngine.CONTAINERS, ScanEngine.IAC}) {
            ScanIssue issue = buildScanIssue(engine);
            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);
        }
    }

    @Test
    @DisplayName("testViewDetails_OSS_CopySuccess")
    void testViewDetails_OSS_CopySuccess() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            viewPrompts.when(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(anyString(), anyString(), anyString(), any()))
                    .thenReturn("viewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.viewDetails(project, issue, QUICK_FIX);

            viewPrompts.verify(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getSeverity()), eq(issue.getVulnerabilities())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("viewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testViewDetails_OSS_CopyFailure")
    void testViewDetails_OSS_CopyFailure() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            viewPrompts.when(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(anyString(), anyString(), anyString(), any()))
                    .thenReturn("viewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(false);

            manager.viewDetails(project, issue, QUICK_FIX);

            viewPrompts.verify(() -> ViewDetailsPrompts.buildSCAExplanationPrompt(
                    eq(issue.getTitle()), eq(issue.getPackageVersion()), eq(issue.getSeverity()), eq(issue.getVulnerabilities())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("viewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testViewDetails_ASCA_Branch")
    void testViewDetails_ASCA_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();
        manager.viewDetails(project, issue, QUICK_FIX);
    }

    @Test
    @DisplayName("testViewDetails_SECRETS_Branch")
    void testViewDetails_SECRETS_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.SECRETS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            viewPrompts.when(() -> ViewDetailsPrompts.buildSecretsExplanationPrompt(anyString(), anyString(), anyString()))
                    .thenReturn("secretsViewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.viewDetails(project, issue, QUICK_FIX);

            viewPrompts.verify(() -> ViewDetailsPrompts.buildSecretsExplanationPrompt(
                    eq(issue.getTitle()), eq(issue.getDescription()), eq(issue.getSeverity())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("secretsViewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testViewDetails_CONTAINERS_Branch")
    void testViewDetails_CONTAINERS_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildContainerScanIssue();
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            viewPrompts.when(() -> ViewDetailsPrompts.buildContainersExplanationPrompt(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("containersViewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.viewDetails(project, issue, QUICK_FIX);

            viewPrompts.verify(() -> ViewDetailsPrompts.buildContainersExplanationPrompt(
                    eq("Dockerfile"), eq("nginx"), eq("nginx:latest"), eq("High")));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("containersViewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testViewDetails_IAC_NullActionId_ReturnsEarly")
    void testViewDetails_IAC_NullActionId_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.IAC);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            manager.viewDetails(project, issue, null);
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    @Test
    @DisplayName("testViewDetails_IAC_NullVulnerability_ReturnsEarly")
    void testViewDetails_IAC_NullVulnerability_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.IAC);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(null);

            manager.viewDetails(project, issue, QUICK_FIX);

            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_SECRETS_Branch")
    void testFixWithCxOneAssist_SECRETS_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.SECRETS);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistFixPrompts> fixPrompts = mockStatic(DevAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> DevAssistFixPrompts.buildSecretRemediationPrompt(anyString(), anyString(), anyString()))
                    .thenReturn("secretPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);

            fixPrompts.verify(() -> DevAssistFixPrompts.buildSecretRemediationPrompt(
                    eq(issue.getTitle()), eq(issue.getDescription()), eq(issue.getSeverity())));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("secretPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_CONTAINERS_Branch")
    void testFixWithCxOneAssist_CONTAINERS_Branch() {
        Project project = mock(Project.class);
        ScanIssue issue = buildContainerScanIssue();
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistFixPrompts> fixPrompts = mockStatic(DevAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> DevAssistFixPrompts.buildContainersRemediationPrompt(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("containersPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);

            fixPrompts.verify(() -> DevAssistFixPrompts.buildContainersRemediationPrompt(
                    eq("Dockerfile"), eq("nginx"), eq("nginx:latest"), eq("High")));
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("containersPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_IAC_NullActionId_ReturnsEarly")
    void testFixWithCxOneAssist_IAC_NullActionId_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.IAC);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            manager.fixWithCxOneAssist(project, issue, null);
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    @Test
    @DisplayName("testFixWithCxOneAssist_IAC_NullVulnerability_ReturnsEarly")
    void testFixWithCxOneAssist_IAC_NullVulnerability_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.IAC);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(null);

            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);

            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    // ===== ASCA null actionId =====

    @Test
    @DisplayName("fixWithCxOneAssist_ASCA_NullActionId_ReturnsEarly")
    void testFixWithCxOneAssist_ASCA_NullActionId_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            manager.fixWithCxOneAssist(project, issue, null);
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    @Test
    @DisplayName("viewDetails_ASCA_NullActionId_ReturnsEarly")
    void testViewDetails_ASCA_NullActionId_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            manager.viewDetails(project, issue, null);
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    // ===== Copilot success path — clipboard skipped =====

    @Test
    @DisplayName("fixWithCxOneAssist_OSS_CopilotSucceeds_ClipboardNotCalled")
    void testFixWithCxOneAssist_OSS_CopilotSucceeds_ClipboardNotCalled() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.OSS);
        RemediationManager manager = new RemediationManager();

        CopilotIntegration.IntegrationResult successResult = mock(CopilotIntegration.IntegrationResult.class);
        when(successResult.isSuccess()).thenReturn(true);

        try (MockedStatic<CopilotIntegration> copilotMock = mockStatic(CopilotIntegration.class);
             MockedStatic<DevAssistFixPrompts> fixPrompts = mockStatic(DevAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> DevAssistFixPrompts.buildSCARemediationPrompt(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("prompt");
            copilotMock.when(() -> CopilotIntegration.openCopilotWithPromptDetailed(anyString(), any(), any()))
                    .thenReturn(successResult);

            manager.fixWithCxOneAssist(project, issue, "actionId");

            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    // ===== IAC with valid vulnerability =====

    @Test
    @DisplayName("fixWithCxOneAssist_IAC_WithValidVulnerability_BuildsPrompt")
    void testFixWithCxOneAssist_IAC_WithValidVulnerability_BuildsPrompt() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.IAC);
        RemediationManager manager = new RemediationManager();
        Vulnerability vuln = new Vulnerability();
        vuln.setTitle("IacVuln");
        vuln.setDescription("IacDesc");
        vuln.setSeverity("Medium");
        vuln.setExpectedValue("true");
        vuln.setActualValue("false");

        try (MockedStatic<DevAssistFixPrompts> fixPrompts = mockStatic(DevAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(vuln);
            fixPrompts.when(() -> DevAssistFixPrompts.buildIACRemediationPrompt(
                    any(), any(), any(), any(), any(), any(), nullable(Integer.class)))
                    .thenReturn("iacPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.fixWithCxOneAssist(project, issue, "vuln-001");

            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("iacPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("viewDetails_IAC_WithValidVulnerability_BuildsExplanation")
    void testViewDetails_IAC_WithValidVulnerability_BuildsExplanation() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.IAC);
        RemediationManager manager = new RemediationManager();
        Vulnerability vuln = new Vulnerability();
        vuln.setTitle("IacVuln");
        vuln.setDescription("IacDesc");
        vuln.setSeverity("Medium");
        vuln.setExpectedValue("true");
        vuln.setActualValue("false");

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(vuln);
            viewPrompts.when(() -> ViewDetailsPrompts.buildIACExplanationPrompt(
                    any(), any(), any(), any(), any(), any()))
                    .thenReturn("iacViewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.viewDetails(project, issue, "vuln-001");

            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(eq("iacViewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    // ===== ASCA with valid vulnerability =====

    @Test
    @DisplayName("fixWithCxOneAssist_ASCA_WithValidVulnerability_BuildsPromptAndCopies")
    void testFixWithCxOneAssist_ASCA_WithValidVulnerability_BuildsPromptAndCopies() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();
        Vulnerability vuln = new Vulnerability();
        vuln.setTitle("AscaVuln");
        vuln.setDescription("AscaDesc");
        vuln.setSeverity("High");
        vuln.setRemediationAdvise("Use parameterized queries.");

        try (MockedStatic<DevAssistFixPrompts> fixPrompts = mockStatic(DevAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(vuln);
            fixPrompts.when(() -> DevAssistFixPrompts.buildASCARemediationPrompt(
                    any(), any(), any(), any(), nullable(Integer.class))).thenReturn("ascaPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.fixWithCxOneAssist(project, issue, "vuln-asca-001");

            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(
                    eq("ascaPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("viewDetails_ASCA_WithValidVulnerability_BuildsExplanation")
    void testViewDetails_ASCA_WithValidVulnerability_BuildsExplanation() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();
        Vulnerability vuln = new Vulnerability();
        vuln.setTitle("AscaVuln");
        vuln.setDescription("AscaDesc");
        vuln.setSeverity("High");

        try (MockedStatic<ViewDetailsPrompts> viewPrompts = mockStatic(ViewDetailsPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(vuln);
            viewPrompts.when(() -> ViewDetailsPrompts.buildASCAExplanationPrompt(any(), any(), any()))
                    .thenReturn("ascaViewPrompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.viewDetails(project, issue, "vuln-asca-001");

            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(
                    eq("ascaViewPrompt"), anyString(), anyString(), eq(project)));
        }
    }

    @Test
    @DisplayName("fixWithCxOneAssist_ASCA_NullVulnerability_ReturnsEarly")
    void testFixWithCxOneAssist_ASCA_NullVulnerability_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(null);
            manager.fixWithCxOneAssist(project, issue, "vuln-asca-001");
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    @Test
    @DisplayName("viewDetails_ASCA_NullVulnerability_ReturnsEarly")
    void testViewDetails_ASCA_NullVulnerability_ReturnsEarly() {
        Project project = mock(Project.class);
        ScanIssue issue = buildScanIssue(ScanEngine.ASCA);
        RemediationManager manager = new RemediationManager();

        try (MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            devAssist.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(issue), anyString())).thenReturn(null);
            manager.viewDetails(project, issue, "vuln-asca-001");
            devAssist.verify(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()), never());
        }
    }

    private static ScanIssue buildScanIssue(ScanEngine engine) {
        ScanIssue issue = new ScanIssue();
        issue.setSeverity("High");
        issue.setTitle("VulnTitle");
        issue.setDescription("Desc");
        issue.setRemediationAdvise("Advise");
        issue.setPackageVersion("1.0.0");
        issue.setPackageManager("npm");
        issue.setCve("CVE-123");
        issue.setScanEngine(engine);
        issue.setFilePath("/path/file");
        return issue;
    }

    private static ScanIssue buildContainerScanIssue() {
        ScanIssue issue = buildScanIssue(ScanEngine.CONTAINERS);
        issue.setFileType("Dockerfile");
        issue.setTitle("nginx");
        issue.setImageTag("nginx:latest");
        return issue;
    }
}
