package com.checkmarx.intellij.unit.devassist.remediation;

import com.checkmarx.intellij.CxIcons;
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
import com.intellij.openapi.util.Iconable;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import javax.swing.*;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IgnoreAllThisTypeFixTest {

    private Project project;
    private ProblemDescriptor descriptor;
    private ScanIssue scanIssue;
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
        scanIssue = new ScanIssue();
        scanIssue.setTitle("Sample Title");
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
    @DisplayName("Constructor creates instance without error")
    void testConstructor_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertNotNull(fix);
    }

    @Test
    @DisplayName("Constructor stores scanIssue reference (reflection check)")
    void testConstructor_storesScanIssue_functionality() throws Exception {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        Field f = IgnoreAllThisTypeFix.class.getDeclaredField("scanIssue");
        f.setAccessible(true);
        Object stored = f.get(fix);
        assertSame(scanIssue, stored);
    }

    @Test
    @DisplayName("getFamilyName returns expected constant string")
    void testGetFamilyName_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertEquals(DevAssistConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME, fix.getFamilyName());
    }

    @Test
    @DisplayName("getFamilyName is non-null")
    void testGetFamilyName_nonNull_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertNotNull(fix.getFamilyName());
    }

    @Test
    @DisplayName("getIcon returns STAR_ACTION icon for visibility flag")
    void testGetIcon_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        Icon icon = fix.getIcon(Iconable.ICON_FLAG_VISIBILITY);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("getIcon returns same icon for combined read+visibility flags")
    void testGetIcon_withFlags_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        int flags = Iconable.ICON_FLAG_READ_STATUS | Iconable.ICON_FLAG_VISIBILITY;
        Icon icon = fix.getIcon(flags);
        assertNotNull(icon);
        assertEquals(CxIcons.STAR_ACTION, icon);
    }

    @Test
    @DisplayName("applyFix executes without throwing when title present")
    void testApplyFix_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix executes without throwing when title is null")
    void testApplyFix_nullTitle_functionality() {
        scanIssue.setTitle(null); // simulate missing title
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(scanIssue);
        assertDoesNotThrow(() -> fix.applyFix(project, descriptor));
    }

    @Test
    @DisplayName("applyFix throws NullPointerException when scanIssue is null (edge case)")
    void testApplyFix_nullScanIssue_functionality() {
        IgnoreAllThisTypeFix fix = new IgnoreAllThisTypeFix(null); // allowed by constructor signature
        assertThrows(NullPointerException.class, () -> fix.applyFix(project, descriptor));
    }
}
