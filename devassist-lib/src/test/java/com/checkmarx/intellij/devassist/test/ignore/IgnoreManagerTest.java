package com.checkmarx.intellij.devassist.test.ignore;

import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IgnoreManagerTest {

    private Project project;
    private IgnoreFileManager ignoreFileManager;
    private ProblemHolderService problemHolder;
    private IgnoreManager ignoreManager;
    private MockedStatic<IgnoreFileManager> ignoreFileManagerStatic;
    private MockedStatic<ProblemHolderService> problemHolderServiceStatic;
    private MockedStatic<DevAssistUtils> devAssistUtilsStatic;

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
        project = mock(Project.class);
        ignoreFileManager = mock(IgnoreFileManager.class);
        problemHolder = mock(ProblemHolderService.class);

        // Mock project.getBasePath() for triggerRescanForEntry
        when(project.getBasePath()).thenReturn("/test/project");

        ignoreFileManagerStatic = mockStatic(IgnoreFileManager.class);
        ignoreFileManagerStatic.when(() -> IgnoreFileManager.getInstance(project)).thenReturn(ignoreFileManager);

        problemHolderServiceStatic = mockStatic(ProblemHolderService.class);
        problemHolderServiceStatic.when(() -> ProblemHolderService.getInstance(project)).thenReturn(problemHolder);

        devAssistUtilsStatic = mockStatic(DevAssistUtils.class);

        ignoreManager = new IgnoreManager(project);
    }

    @AfterEach
    void tearDown() {
        ignoreFileManagerStatic.close();
        problemHolderServiceStatic.close();
        devAssistUtilsStatic.close();
    }

    @Test
    void testGetIgnoredEntriesReturnsList() {
        when(ignoreFileManager.getAllIgnoreEntries()).thenReturn(Collections.emptyList());
        List<IgnoreEntry> entries = ignoreManager.getIgnoredEntries();
        assertNotNull(entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    void testAddAllIgnoredEntryWithNoIssues() {
        when(problemHolder.getAllIssues()).thenReturn(Collections.emptyMap());
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        ignoreManager.addAllIgnoredEntry(issue, "clickId");
        // Should not throw or update anything
        verify(ignoreFileManager, never()).updateIgnoreData(anyString(), any());
    }

    @Test
    void testAddAllIgnoredEntryWithMatchingIssue() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);
            ScanIssue issue = mock(ScanIssue.class);
            when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
            when(issue.getPackageManager()).thenReturn("npm");
            when(issue.getTitle()).thenReturn("lodash");
            when(issue.getPackageVersion()).thenReturn("4.17.21");
            when(issue.getFilePath()).thenReturn("/path/to/file.js");
            when(issue.getLocations()).thenReturn((List<Location>) List.of(new Location(2, 0, 10)));
            when(issue.getRuleId()).thenReturn(1077);
            when(issue.getSecretValue()).thenReturn(null);

            Map<String, List<ScanIssue>> issues = new HashMap<>();
            issues.put("key", new ArrayList<>(List.of(issue)));
            when(problemHolder.getAllIssues()).thenReturn(issues);

            when(ignoreFileManager.normalizePath(anyString())).thenReturn("file.js");

            ignoreManager.addAllIgnoredEntry(issue, "clickId");
            verify(ignoreFileManager).updateIgnoreData(anyString(), any());
        }
    }

    @Test
    void testConvertToIgnoredEntryIacWithNullClickId() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getTitle()).thenReturn("iac-title");
        IgnoreEntry entry = ignoreManager.convertToIgnoredEntryIac(issue, null);
        assertNull(entry);
    }

    @Test
    void testConvertToIgnoredEntryIacWithEmptyClickId() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getTitle()).thenReturn("iac-title");
        IgnoreEntry entry = ignoreManager.convertToIgnoredEntryIac(issue, "");
        assertNull(entry);
    }

    @Test
    void testConvertToIgnoredEntryIacWithNullVulnerability() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getTitle()).thenReturn("iac-title");
        when(issue.getFilePath()).thenReturn("/file");
        when(issue.getLocations()).thenReturn((List<Location>)List.of(new Location(2, 0, 10)));
        devAssistUtilsStatic.when(() -> DevAssistUtils.getVulnerabilityDetails(any(), any())).thenReturn(null);
        IgnoreEntry entry = ignoreManager.convertToIgnoredEntryIac(issue, "clickId");
        assertNull(entry);
    }

    @Test
    void testConvertToIgnoredEntryIacSuccess() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getTitle()).thenReturn("iac-title");
        when(issue.getFilePath()).thenReturn("/file");
        when(issue.getLocations()).thenReturn((List<Location>)List.of(new Location(2, 0, 10)));
        when(issue.getScanEngine()).thenReturn(ScanEngine.IAC);

        Vulnerability vuln = mock(Vulnerability.class);
        when(vuln.getTitle()).thenReturn("iac-title");
        when(vuln.getSimilarityId()).thenReturn("simid");
        when(vuln.getSeverity()).thenReturn("HIGH");
        when(vuln.getDescription()).thenReturn("desc");

        devAssistUtilsStatic.when(() -> DevAssistUtils.getVulnerabilityDetails(any(), any())).thenReturn(vuln);
        when(ignoreFileManager.normalizePath(anyString())).thenReturn("file");

        IgnoreEntry entry = ignoreManager.convertToIgnoredEntryIac(issue, "clickId");
        assertNotNull(entry);
        assertEquals("iac-title", entry.getPackageName());
        assertEquals("simid", entry.getSimilarityId());
        assertEquals("HIGH", entry.getSeverity());
        assertEquals("desc", entry.getDescription());
        assertEquals(1, entry.getFiles().size());
    }

    @Test
    void testConvertToIgnoredEntryAscaWithNullClickId() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getTitle()).thenReturn("asca-title");
        IgnoreEntry entry = ignoreManager.convertToIgnoredEntryAsca(issue, null);
        assertNull(entry);
    }

    @Test
    void testConvertToIgnoredEntryAscaWithEmptyClickId() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getTitle()).thenReturn("asca-title");
        IgnoreEntry entry = ignoreManager.convertToIgnoredEntryAsca(issue, "");
        assertNull(entry);
    }

    @Test
    void testConvertToIgnoredEntryAscaWithNullVulnerability() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getTitle()).thenReturn("asca-title");
        when(issue.getFilePath()).thenReturn("/file");
        when(issue.getLocations()).thenReturn((List<Location>)List.of(new Location(2, 0, 10)));
        devAssistUtilsStatic.when(() -> DevAssistUtils.getVulnerabilityDetails(any(), any())).thenReturn(null);
        IgnoreEntry entry = ignoreManager.convertToIgnoredEntryAsca(issue, "clickId");
        assertNull(entry);
    }

    @Test
    void testConvertToIgnoredEntryAscaSuccess() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);
            ScanIssue issue = mock(ScanIssue.class);
            when(issue.getTitle()).thenReturn("asca-title");
            when(issue.getFilePath()).thenReturn("/file");
            when(issue.getLocations()).thenReturn((List<Location>) List.of(new Location(2, 0, 10)));
            when(issue.getScanEngine()).thenReturn(ScanEngine.ASCA);
            when(issue.getRuleId()).thenReturn(1077);

            Vulnerability vuln = mock(Vulnerability.class);
            when(vuln.getTitle()).thenReturn("asca-title");
            when(vuln.getRuleId()).thenReturn(1077);
            when(vuln.getSeverity()).thenReturn("LOW");
            when(vuln.getDescription()).thenReturn("desc");
            when(vuln.getProblematicLine()).thenReturn(null);

            devAssistUtilsStatic.when(() -> DevAssistUtils.getVulnerabilityDetails(any(), any())).thenReturn(vuln);
            when(ignoreFileManager.normalizePath(anyString())).thenReturn("file");

            IgnoreEntry entry = ignoreManager.convertToIgnoredEntryAsca(issue, "clickId");
            assertNotNull(entry);
            assertEquals("asca-title", entry.getPackageName());
            assertEquals(1077, entry.getRuleId());
            assertEquals("LOW", entry.getSeverity());
            assertEquals("desc", entry.getDescription());
            assertEquals(1, entry.getFiles().size());
            assertNull(entry.getFiles().get(0).getProblematicLine());
        }
    }

    @Test
    void testConvertToIgnoredEntryForContainers() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);
            ScanIssue issue = mock(ScanIssue.class);
            when(issue.getScanEngine()).thenReturn(ScanEngine.CONTAINERS);
            when(issue.getTitle()).thenReturn("nginx");
            when(issue.getImageTag()).thenReturn("1.19");
            when(issue.getFilePath()).thenReturn("/dockerfile");
            when(issue.getLocations()).thenReturn((List<Location>) List.of(new Location(2, 0, 10)));
            when(issue.getPackageManager()).thenReturn("docker");
            when(issue.getPackageVersion()).thenReturn("1.19");
            when(issue.getRuleId()).thenReturn(4011);
            when(issue.getSecretValue()).thenReturn(null);

            when(ignoreFileManager.normalizePath(anyString())).thenReturn("dockerfile");

            IgnoreEntry entry = ignoreManager.convertToIgnoredEntry(issue, "clickId");
            assertNotNull(entry);
            assertEquals("nginx:1.19", entry.getPackageName());
            assertEquals("nginx", entry.getImageName());
            assertEquals("1.19", entry.getImageTag());
            assertEquals("docker", entry.getPackageManager());
            assertEquals("1.19", entry.getPackageVersion());
            assertEquals(4011, entry.getRuleId());
            assertEquals(1, entry.getFiles().size());
        }
    }
    @Test
    void testConvertToIgnoredEntryForSecrets() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.SECRETS);
        when(issue.getTitle()).thenReturn("AWS_KEY");
        when(issue.getFilePath()).thenReturn("/secretfile");
        when(issue.getLocations()).thenReturn((List<Location>)List.of(new Location(2, 0, 10)));
        when(issue.getPackageManager()).thenReturn("npm");
        when(issue.getPackageVersion()).thenReturn("1.0.0");
        when(issue.getRuleId()).thenReturn(1077);
        when(issue.getSecretValue()).thenReturn("secret123");

        when(ignoreFileManager.normalizePath(anyString())).thenReturn("secretfile");

        IgnoreEntry entry = ignoreManager.convertToIgnoredEntry(issue, "clickId");
        assertNotNull(entry);
        assertEquals("AWS_KEY", entry.getPackageName());
        assertEquals("secret123", entry.getSecretValue());
        assertEquals(1, entry.getFiles().size());
    }

    @Test
    void testConvertToIgnoredEntryForOss() {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        when(issue.getTitle()).thenReturn("lodash");
        when(issue.getFilePath()).thenReturn("/ossfile");
        when(issue.getLocations()).thenReturn((List<Location>)List.of(new Location(1, 0, 10)));
        when(issue.getPackageManager()).thenReturn("npm");
        when(issue.getPackageVersion()).thenReturn("4.17.21");
        when(issue.getRuleId()).thenReturn(1077);
        when(issue.getSecretValue()).thenReturn(null);

        when(ignoreFileManager.normalizePath(anyString())).thenReturn("ossfile");

        IgnoreEntry entry = ignoreManager.convertToIgnoredEntry(issue, "clickId");
        assertNotNull(entry);
        assertEquals("lodash", entry.getPackageName());
        assertEquals("npm", entry.getPackageManager());
        assertEquals("4.17.21", entry.getPackageVersion());
        assertEquals(1077, entry.getRuleId());
        assertEquals(1, entry.getFiles().size());
    }

    // ========== REVIVE FUNCTIONALITY TESTS ==========

    @Test
    void testReviveSingleEntry_success() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setPackageName("test-package");
            entry.setType(ScanEngine.OSS);
            entry.setPackageManager("npm");
            entry.setPackageVersion("1.0.0");

            List<IgnoreEntry.FileReference> files = new ArrayList<>();
            files.add(new IgnoreEntry.FileReference("file1.js", true, 10, ""));
            files.add(new IgnoreEntry.FileReference("file2.js", true, 20, ""));
            entry.setFiles(files);

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(true);
            when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());

            // Act
            ignoreManager.reviveSingleEntry(entry);

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
        }
    }

    @Test
    void testReviveSingleEntry_failure() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setPackageName("non-existent");
            entry.setType(ScanEngine.OSS);
            entry.setFiles(List.of(new IgnoreEntry.FileReference("file.js", true, 1, "")));

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(false);
            when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());

            // Act
            ignoreManager.reviveSingleEntry(entry);

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
            // Should not trigger rescan on failure
        }
    }

    @Test
    void testReviveMultipleEntries_allSuccess() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry1 = new IgnoreEntry();
            entry1.setPackageName("package1");
            entry1.setType(ScanEngine.OSS);
            entry1.setTitle("Package 1");
            entry1.setFiles(List.of(new IgnoreEntry.FileReference("file1.js", true, 1, "")));

            IgnoreEntry entry2 = new IgnoreEntry();
            entry2.setPackageName("package2");
            entry2.setType(ScanEngine.CONTAINERS);
            entry2.setTitle("Package 2");
            entry2.setFiles(List.of(new IgnoreEntry.FileReference("file2.js", true, 2, "")));

            List<IgnoreEntry> entries = Arrays.asList(entry1, entry2);

            when(ignoreFileManager.reviveEntry(any())).thenReturn(true);

            // Act
            ignoreManager.reviveMultipleEntries(entries);

            // Assert
            verify(ignoreFileManager, times(2)).reviveEntry(any());
        }
    }

    @Test
    void testReviveMultipleEntries_partialSuccess() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry1 = new IgnoreEntry();
            entry1.setPackageName("package1");
            entry1.setType(ScanEngine.OSS);
            entry1.setTitle("Package 1");
            entry1.setFiles(List.of(new IgnoreEntry.FileReference("file1.js", true, 1, "")));

            IgnoreEntry entry2 = new IgnoreEntry();
            entry2.setPackageName("package2");
            entry2.setType(ScanEngine.CONTAINERS);
            entry2.setTitle("Package 2");
            entry2.setFiles(List.of(new IgnoreEntry.FileReference("file2.js", true, 2, "")));

            List<IgnoreEntry> entries = Arrays.asList(entry1, entry2);

            // First succeeds, second fails
            when(ignoreFileManager.reviveEntry(entry1)).thenReturn(true);
            when(ignoreFileManager.reviveEntry(entry2)).thenReturn(false);

            // Act
            ignoreManager.reviveMultipleEntries(entries);

            // Assert
            verify(ignoreFileManager, times(2)).reviveEntry(any());
        }
    }

    @Test
    void testReviveMultipleEntries_emptyList() {
        // Arrange
        List<IgnoreEntry> emptyList = Collections.emptyList();

        // Act
        ignoreManager.reviveMultipleEntries(emptyList);

        // Assert
        verify(ignoreFileManager, never()).reviveEntry(any());
    }

    @Test
    void testReviveMultipleEntries_nullList() {
        // Act
        ignoreManager.reviveMultipleEntries(null);

        // Assert
        verify(ignoreFileManager, never()).reviveEntry(any());
    }

    @Test
    void testReviveSingleEntry_withSingleFile() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setPackageName("single-file-package");
            entry.setType(ScanEngine.ASCA);
            entry.setRuleId(123);
            entry.setFiles(List.of(new IgnoreEntry.FileReference("app.java", true, 42, "")));

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(true);
            when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());

            // Act
            ignoreManager.reviveSingleEntry(entry);

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
        }
    }

    @Test
    void testReviveSingleEntry_withMultipleFiles() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setPackageName("multi-file-package");
            entry.setType(ScanEngine.ASCA);
            entry.setRuleId(456);

            List<IgnoreEntry.FileReference> files = new ArrayList<>();
            files.add(new IgnoreEntry.FileReference("file1.java", true, 10, ""));
            files.add(new IgnoreEntry.FileReference("file2.java", true, 20, ""));
            files.add(new IgnoreEntry.FileReference("file3.java", true, 30, ""));
            entry.setFiles(files);

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(true);
            when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());

            // Act
            ignoreManager.reviveSingleEntry(entry);

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
        }
    }

    @Test
    void testReviveMultipleEntries_differentScanEngines() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry ossEntry = new IgnoreEntry();
            ossEntry.setPackageName("lodash");
            ossEntry.setType(ScanEngine.OSS);
            ossEntry.setTitle("OSS Package");
            ossEntry.setFiles(List.of(new IgnoreEntry.FileReference("package.json", true, 1, "")));

            IgnoreEntry ascaEntry = new IgnoreEntry();
            ascaEntry.setPackageName("SQL Injection");
            ascaEntry.setType(ScanEngine.ASCA);
            ascaEntry.setTitle("ASCA Issue");
            ascaEntry.setFiles(List.of(new IgnoreEntry.FileReference("app.java", true, 42, "")));

            IgnoreEntry secretEntry = new IgnoreEntry();
            secretEntry.setPackageName("AWS_KEY");
            secretEntry.setType(ScanEngine.SECRETS);
            secretEntry.setTitle("Secret");
            secretEntry.setFiles(List.of(new IgnoreEntry.FileReference("config.js", true, 5, "")));

            List<IgnoreEntry> entries = Arrays.asList(ossEntry, ascaEntry, secretEntry);

            when(ignoreFileManager.reviveEntry(any())).thenReturn(true);

            // Act
            ignoreManager.reviveMultipleEntries(entries);

            // Assert
            verify(ignoreFileManager, times(3)).reviveEntry(any());
        }
    }

    @Test
    void testReviveSingleEntry_countsActiveFilesCorrectly() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setPackageName("mixed-package");
            entry.setType(ScanEngine.ASCA);
            entry.setRuleId(789);

            List<IgnoreEntry.FileReference> files = new ArrayList<>();
            files.add(new IgnoreEntry.FileReference("active1.java", true, 10, ""));
            files.add(new IgnoreEntry.FileReference("inactive1.java", false, 20, ""));
            files.add(new IgnoreEntry.FileReference("active2.java", true, 30, ""));
            entry.setFiles(files);

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(true);
            when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());

            // Act
            ignoreManager.reviveSingleEntry(entry);

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
            // Should count only 2 active files before reviving
        }
    }

    @Test
    void testReviveMultipleEntries_withNoActiveFiles() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setPackageName("already-inactive");
            entry.setType(ScanEngine.OSS);
            entry.setTitle("Already Inactive");

            List<IgnoreEntry.FileReference> files = new ArrayList<>();
            files.add(new IgnoreEntry.FileReference("file1.js", false, 1, ""));
            files.add(new IgnoreEntry.FileReference("file2.js", false, 2, ""));
            entry.setFiles(files);

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(true);

            // Act
            ignoreManager.reviveMultipleEntries(List.of(entry));

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
            // Should handle entries with 0 active files
        }
    }

    @Test
    void testReviveSingleEntry_forContainerEntry() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setImageName("nginx");
            entry.setImageTag("1.19");
            entry.setType(ScanEngine.CONTAINERS);
            entry.setPackageName("nginx:1.19");
            entry.setFiles(List.of(new IgnoreEntry.FileReference("Dockerfile", true, 1, "")));

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(true);
            when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());

            // Act
            ignoreManager.reviveSingleEntry(entry);

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
        }
    }

    @Test
    void testReviveSingleEntry_forIacEntry() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            IgnoreEntry entry = new IgnoreEntry();
            entry.setPackageName("S3 Bucket Public");
            entry.setSimilarityId("sim-123");
            entry.setType(ScanEngine.IAC);
            entry.setFiles(List.of(new IgnoreEntry.FileReference("main.tf", true, 15, "")));

            when(ignoreFileManager.reviveEntry(entry)).thenReturn(true);
            when(ignoreFileManager.getIgnoreData()).thenReturn(new HashMap<>());

            // Act
            ignoreManager.reviveSingleEntry(entry);

            // Assert
            verify(ignoreFileManager, times(1)).reviveEntry(entry);
        }
    }

    // ========== updateLineNumbersForIgnoredEntries TESTS ==========

    @Test
    void testUpdateLineNumbers_iacSimilarityIdChangedOnLineShift_rekeysEntry() {
        // Arrange: an ignored IaC entry stored with OLD similarity ID at line 10
        String oldSimId = "old-sim-id";
        String newSimId = "new-sim-id";
        String title = "Insecure Port";
        String filePath = "/project/infra/main.tf";
        String relativePath = "infra/main.tf";

        IgnoreEntry ignoredEntry = new IgnoreEntry();
        ignoredEntry.setType(ScanEngine.IAC);
        ignoredEntry.setPackageName(title);
        ignoredEntry.setSimilarityId(oldSimId);
        ignoredEntry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference(relativePath, true, 10, ""))));

        String oldKey = title + ":" + oldSimId + ":" + relativePath;
        Map<String, IgnoreEntry> ignoreData = new HashMap<>();
        ignoreData.put(oldKey, ignoredEntry);
        when(ignoreFileManager.getIgnoreData()).thenReturn(ignoreData);
        when(ignoreFileManager.normalizePath(filePath)).thenReturn(relativePath);

        // New scan result: same vulnerability now at line 11 with a NEW similarity ID (line shift)
        Vulnerability newVuln = new Vulnerability();
        newVuln.setTitle(title);
        newVuln.setSimilarityId(newSimId);
        newVuln.setVulnerabilityId("vuln-id-new");

        ScanIssue newScanIssue = new ScanIssue();
        newScanIssue.setScanEngine(ScanEngine.IAC);
        newScanIssue.setFilePath(filePath);
        newScanIssue.setTitle(title);
        newScanIssue.setVulnerabilities(new ArrayList<>(List.of(newVuln)));
        newScanIssue.setLocations(new ArrayList<>(List.of(new Location(11, 0, 20))));

        @SuppressWarnings("unchecked")
        com.checkmarx.intellij.devassist.common.ScanResult<?> scanResult = mock(com.checkmarx.intellij.devassist.common.ScanResult.class);
        when(scanResult.getIssues()).thenReturn(List.of(newScanIssue));

        // Act
        ignoreManager.updateLineNumbersForIgnoredEntries(scanResult, filePath);

        // Assert: old key removed, new key with updated similarity ID added
        String newKey = title + ":" + newSimId + ":" + relativePath;
        assertFalse(ignoreData.containsKey(oldKey), "Old key should be removed after similarity ID change");
        assertTrue(ignoreData.containsKey(newKey), "New key with updated similarity ID should exist");
        IgnoreEntry updatedEntry = ignoreData.get(newKey);
        assertEquals(newSimId, updatedEntry.getSimilarityId(), "Similarity ID should be updated to new value");
        assertEquals(11, updatedEntry.getFiles().get(0).getLine(), "Line number should be updated to new position");
        verify(ignoreFileManager).saveIgnoreDataToDisk();
    }

    @Test
    void testUpdateLineNumbers_iacVulnerabilityFixed_removesEntry() {
        // Arrange: an ignored IaC entry that no longer appears in new scan results (was fixed)
        String simId = "sim-id-fixed";
        String title = "Exposed Secret";
        String filePath = "/project/main.tf";
        String relativePath = "main.tf";

        IgnoreEntry ignoredEntry = new IgnoreEntry();
        ignoredEntry.setType(ScanEngine.IAC);
        ignoredEntry.setPackageName(title);
        ignoredEntry.setSimilarityId(simId);
        ignoredEntry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference(relativePath, true, 5, ""))));

        String key = title + ":" + simId + ":" + relativePath;
        Map<String, IgnoreEntry> ignoreData = new HashMap<>();
        ignoreData.put(key, ignoredEntry);
        when(ignoreFileManager.getIgnoreData()).thenReturn(ignoreData);
        when(ignoreFileManager.normalizePath(filePath)).thenReturn(relativePath);

        // New scan result: different vulnerability in the file, the original one is gone (fixed)
        Vulnerability otherVuln = new Vulnerability();
        otherVuln.setTitle("Different Rule");
        otherVuln.setSimilarityId("other-sim-id");
        otherVuln.setVulnerabilityId("other-vuln-id");

        ScanIssue otherScanIssue = new ScanIssue();
        otherScanIssue.setScanEngine(ScanEngine.IAC);
        otherScanIssue.setFilePath(filePath);
        otherScanIssue.setTitle("Different Rule");
        otherScanIssue.setVulnerabilities(new ArrayList<>(List.of(otherVuln)));
        otherScanIssue.setLocations(new ArrayList<>(List.of(new Location(3, 0, 15))));

        @SuppressWarnings("unchecked")
        com.checkmarx.intellij.devassist.common.ScanResult<?> scanResult = mock(com.checkmarx.intellij.devassist.common.ScanResult.class);
        when(scanResult.getIssues()).thenReturn(List.of(otherScanIssue));

        // Act
        ignoreManager.updateLineNumbersForIgnoredEntries(scanResult, filePath);

        // Assert: entry removed because vulnerability no longer exists in file
        assertFalse(ignoreData.containsKey(key), "Entry should be removed when vulnerability is no longer present");
        verify(ignoreFileManager).saveIgnoreDataToDisk();
    }

    @Test
    void testUpdateLineNumbers_iacSimilarityIdStable_updatesLineOnly() {
        // Arrange: an ignored IaC entry where the similarity ID stays stable (some engines are stable)
        // but the line number changes – should update line number in-place without re-keying
        String simId = "stable-sim-id";
        String title = "Hardcoded Password";
        String filePath = "/project/k8s.yaml";
        String relativePath = "k8s.yaml";

        IgnoreEntry ignoredEntry = new IgnoreEntry();
        ignoredEntry.setType(ScanEngine.IAC);
        ignoredEntry.setPackageName(title);
        ignoredEntry.setSimilarityId(simId);
        ignoredEntry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference(relativePath, true, 20, ""))));

        Vulnerability vuln = new Vulnerability();
        vuln.setTitle(title);
        vuln.setSimilarityId(simId);
        vuln.setVulnerabilityId("vuln-id-stable");

        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setScanEngine(ScanEngine.IAC);
        scanIssue.setFilePath(filePath);
        scanIssue.setTitle(title);
        scanIssue.setVulnerabilities(new ArrayList<>(List.of(vuln)));
        scanIssue.setLocations(new ArrayList<>(List.of(new Location(21, 0, 30))));

        // The exact key for this entry matches the new scan result
        String key = title + ":" + simId + ":" + relativePath;
        Map<String, IgnoreEntry> ignoreData = new HashMap<>();
        ignoreData.put(key, ignoredEntry);
        when(ignoreFileManager.getIgnoreData()).thenReturn(ignoreData);
        when(ignoreFileManager.normalizePath(filePath)).thenReturn(relativePath);
        when(ignoreFileManager.normalizePath(anyString())).thenReturn(relativePath);

        // Make key lookup return this scan issue (mock createIgnoreKeysForScanIssue side-effect via getVulnerabilityDetails)
        devAssistUtilsStatic.when(() -> DevAssistUtils.getVulnerabilityDetails(eq(scanIssue), anyString())).thenReturn(vuln);

        @SuppressWarnings("unchecked")
        com.checkmarx.intellij.devassist.common.ScanResult<?> scanResult = mock(com.checkmarx.intellij.devassist.common.ScanResult.class);
        when(scanResult.getIssues()).thenReturn(List.of(scanIssue));

        // Act
        ignoreManager.updateLineNumbersForIgnoredEntries(scanResult, filePath);

        // Assert: key unchanged, similarity ID unchanged, line updated
        assertTrue(ignoreData.containsKey(key), "Key should remain unchanged when similarity ID is stable");
        assertEquals(simId, ignoreData.get(key).getSimilarityId(), "Similarity ID should remain the same");
        assertEquals(21, (int) ignoreData.get(key).getFiles().get(0).getLine(), "Line number should be updated to new position");
        verify(ignoreFileManager).saveIgnoreDataToDisk();
    }

    @Test
    void testReviveMultipleEntries_largeList() {
        try (MockedStatic<LocalFileSystem> localFileSystemMock = mockStatic(LocalFileSystem.class)) {
            // Arrange
            LocalFileSystem mockFs = mock(LocalFileSystem.class);
            localFileSystemMock.when(LocalFileSystem::getInstance).thenReturn(mockFs);

            List<IgnoreEntry> entries = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                IgnoreEntry entry = new IgnoreEntry();
                entry.setPackageName("package-" + i);
                entry.setType(ScanEngine.OSS);
                entry.setTitle("Package " + i);
                entry.setFiles(List.of(new IgnoreEntry.FileReference("file" + i + ".js", true, i, "")));
                entries.add(entry);
            }

            when(ignoreFileManager.reviveEntry(any())).thenReturn(true);

            // Act
            ignoreManager.reviveMultipleEntries(entries);

            // Assert
            verify(ignoreFileManager, times(50)).reviveEntry(any());
        }
    }
}
