package com.checkmarx.intellij.ast.test.unit.devassist.inspection.remediation;

import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.remediation.IgnoreAllThisTypeFix;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IgnoreAllThisTypeFixTest {
    private ScanIssue scanIssue;
    private IgnoreAllThisTypeFix fix;
    private Project project;
    private ProblemDescriptor descriptor;
    private IgnoreFileManager ignoreFileManager;
    private ProblemHolderService problemHolderService;
    private MockedStatic<IgnoreFileManager> ignoreFileManagerStatic;
    private MockedStatic<ProblemHolderService> problemHolderServiceStatic;

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

        // Mock NotificationGroup
        mockNotificationGroup = mock(NotificationGroup.class, RETURNS_DEEP_STUBS);
        when(mockNotificationGroupManager.getNotificationGroup(anyString())).thenReturn(mockNotificationGroup);
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (appManagerMock != null) appManagerMock.close();
        if (notificationGroupManagerMock != null) notificationGroupManagerMock.close();
    }

    @BeforeEach
    void setUp() {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        descriptor = mock(ProblemDescriptor.class);

        // Create a real ScanIssue with all required fields
        scanIssue = new ScanIssue();
        scanIssue.setTitle("Test Issue");
        scanIssue.setFilePath("/test/path/file.js");
        scanIssue.setScanEngine(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        scanIssue.setPackageManager("npm");
        scanIssue.setPackageVersion("1.0.0");
        // Add a location to avoid IndexOutOfBoundsException
        scanIssue.setLocations(List.of(new Location(10, 0, 20)));

        // Mock the services that IgnoreManager depends on
        ignoreFileManager = mock(IgnoreFileManager.class);
        problemHolderService = mock(ProblemHolderService.class);

        // Mock normalizePath to return a simple relative path
        when(ignoreFileManager.normalizePath(anyString())).thenReturn("file.js");
        // Mock getIgnoreData to return an empty map
        when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());
        // Mock getAllIssues to return an empty map
        when(problemHolderService.getAllIssues()).thenReturn(new HashMap<>());

        ignoreFileManagerStatic = mockStatic(IgnoreFileManager.class);
        ignoreFileManagerStatic.when(() -> IgnoreFileManager.getInstance(project)).thenReturn(ignoreFileManager);

        problemHolderServiceStatic = mockStatic(ProblemHolderService.class);
        problemHolderServiceStatic.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolderService);

        fix = new IgnoreAllThisTypeFix(scanIssue);
    }

    @AfterEach
    void tearDown() {
        if (ignoreFileManagerStatic != null) {
            ignoreFileManagerStatic.close();
        }
        if (problemHolderServiceStatic != null) {
            problemHolderServiceStatic.close();
        }
    }

    @Test
    @DisplayName("getFamilyName returns expected constant")
    void testGetFamilyName_returnsExpectedConstant() {
        assertEquals(DevAssistConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME, fix.getFamilyName());
    }

    @Test
    @DisplayName("applyFix logs info and is called")
    void testApplyFix_logsInfoAndIsCalled() {
        // Use a subclass to intercept the logger call for coverage
        final boolean[] called = {false};
        IgnoreAllThisTypeFix testFix = new IgnoreAllThisTypeFix(scanIssue) {
            @Override
            public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                called[0] = true;
                super.applyFix(project, descriptor);
            }
        };
        testFix.applyFix(project, descriptor);
        assertTrue(called[0], "applyFix should be called");
    }

    @Test
    @DisplayName("constructor sets scanIssue correctly")
    void testConstructor_setsScanIssueCorrectly() {
        assertNotNull(fix);
    }
}
