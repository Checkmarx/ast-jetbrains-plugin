package com.checkmarx.intellij.unit.devassist.remediation;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.RemediationManager;
import com.checkmarx.intellij.devassist.remediation.prompts.CxOneAssistFixPrompts;
import com.checkmarx.intellij.devassist.remediation.prompts.ViewDetailsPrompts;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static com.checkmarx.intellij.Constants.RealTimeConstants.QUICK_FIX;
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

        try (MockedStatic<CxOneAssistFixPrompts> fixPrompts = mockStatic(CxOneAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(
                    anyString(), anyString(), anyString(), anyString())).thenReturn("prompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(true);

            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);

            fixPrompts.verify(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(
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

        try (MockedStatic<CxOneAssistFixPrompts> fixPrompts = mockStatic(CxOneAssistFixPrompts.class);
             MockedStatic<DevAssistUtils> devAssist = mockStatic(DevAssistUtils.class)) {
            fixPrompts.when(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("prompt");
            devAssist.when(() -> DevAssistUtils.copyToClipboardWithNotification(anyString(), anyString(), anyString(), any()))
                    .thenReturn(false);

            manager.fixWithCxOneAssist(project, issue, QUICK_FIX);

            fixPrompts.verify(() -> CxOneAssistFixPrompts.buildSCARemediationPrompt(
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
}
