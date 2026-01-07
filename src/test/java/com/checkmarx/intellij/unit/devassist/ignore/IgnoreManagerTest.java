package com.checkmarx.intellij.unit.devassist.ignore;

import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
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

    @BeforeEach
    void setUp() {
        project = mock(Project.class);
        ignoreFileManager = mock(IgnoreFileManager.class);
        problemHolder = mock(ProblemHolderService.class);

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
            when(vuln.getSeverity()).thenReturn("LOW");
            when(vuln.getDescription()).thenReturn("desc");

            devAssistUtilsStatic.when(() -> DevAssistUtils.getVulnerabilityDetails(any(), any())).thenReturn(vuln);
            when(ignoreFileManager.normalizePath(anyString())).thenReturn("file");

            IgnoreEntry entry = ignoreManager.convertToIgnoredEntryAsca(issue, "clickId");
            assertNotNull(entry);
            assertEquals("asca-title", entry.getPackageName());
            assertEquals(1077, entry.getRuleId());
            assertEquals("LOW", entry.getSeverity());
            assertEquals("desc", entry.getDescription());
            assertEquals(1, entry.getFiles().size());
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
        assertEquals(2, entry.getFiles().size());
    }
}
