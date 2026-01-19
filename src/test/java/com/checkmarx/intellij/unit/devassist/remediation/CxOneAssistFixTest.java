package com.checkmarx.intellij.unit.devassist.remediation;

import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CxOneAssistFixTest {

    private Project project;
    private ProblemDescriptor descriptor;

    static MockedStatic<ApplicationManager> appManagerMock;
    static Application mockApp;
    static MockedStatic<NotificationGroupManager> notificationGroupManagerMock;
    static NotificationGroupManager mockNotificationGroupManager;
    static NotificationGroup mockNotificationGroup;

    @BeforeEach
    void setUp() {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        descriptor = mock(ProblemDescriptor.class);
    }

    @BeforeAll
    static void setupStaticMocks() throws Exception {
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
       // when(mockNotificationGroup.createNotification(anyString(), anyString(), any(), any())).thenReturn(mock(Notification.class));
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (appManagerMock != null) appManagerMock.close();
        if (notificationGroupManagerMock != null) notificationGroupManagerMock.close();
    }


    @Test
    @DisplayName("Constructor stores scanIssue reference")
    void testConstructor_functionality() {
        ScanIssue issue = new ScanIssue();
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertSame(issue, fix.getScanIssue());
    }

    @Test
    @DisplayName("getFamilyName returns expected constant")
    void testGetFamilyName_functionality() {
        CxOneAssistFix fix = new CxOneAssistFix(new ScanIssue());
        assertEquals(DevAssistConstants.FIX_WITH_CXONE_ASSIST, fix.getFamilyName());
    }

    @Test
    @DisplayName("getIcon returns star action icon")
    void testGetIcon_functionality() {
        CxOneAssistFix fix = new CxOneAssistFix(new ScanIssue());
        Icon icon = fix.getIcon(0);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("applyFix OSS branch executes without exception")
    void testApplyFix_ossBranch_functionality() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.OSS);
        issue.setTitle("OSS Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix routes to ASCA remediation branch")
    void testApplyFix_ascaBranch_functionality() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);
        issue.setTitle("ASCA Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix with other engine does nothing in default case")
    void testApplyFix_otherEngineDefaultNoAction_functionality() {
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.IAC); // engine not explicitly handled
        issue.setTitle("IAC Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix with null scanEngine throws NPE (current behavior)")
    void testApplyFix_nullScanEngineThrowsNpe_functionality() {
        ScanIssue issue = new ScanIssue(); // scanEngine left null
        issue.setTitle("Null Engine Title");
        CxOneAssistFix fix = new CxOneAssistFix(issue);
        assertThrows(NullPointerException.class, () -> fix.applyFix(project, descriptor));
    }
}
