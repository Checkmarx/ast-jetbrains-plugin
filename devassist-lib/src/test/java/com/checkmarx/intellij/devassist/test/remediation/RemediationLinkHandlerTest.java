package com.checkmarx.intellij.devassist.test.remediation;

import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.remediation.RemediationLinkHandler;
import com.checkmarx.intellij.devassist.remediation.RemediationManager;
import com.checkmarx.intellij.devassist.telemetry.TelemetryService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RemediationLinkHandlerTest {

    private RemediationLinkHandler handler;
    private RemediationManager mockRemediationManager;
    private Editor mockEditor;
    private Project mockProject;

    // ---- Reflection helpers ----

    private static RemediationLinkHandler newInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (RemediationLinkHandler) unsafe.allocateInstance(RemediationLinkHandler.class);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        handler = newInstanceWithoutConstructor();
        mockRemediationManager = mock(RemediationManager.class);
        setField(handler, "remediationManager", mockRemediationManager);

        mockEditor = mock(Editor.class);
        mockProject = mock(Project.class);
    }

    // ===== Early-return paths (no platform mocking needed) =====

    @Test
    void handleLink_WhenProjectIsNull_ReturnsFalse() {
        when(mockEditor.getProject()).thenReturn(null);
        assertFalse(handler.handleLink("copyfixprompt:issue-id:OSS", mockEditor));
    }

    @Test
    void handleLink_WhenLinkHasNoSeparator_ReturnsFalse() {
        when(mockEditor.getProject()).thenReturn(mockProject);
        assertFalse(handler.handleLink("nocolon", mockEditor));
    }

    @Test
    void handleLink_WhenScanIssueIdIsEmpty_ReturnsFalse() {
        when(mockEditor.getProject()).thenReturn(mockProject);
        // "action::engine" → split by ":" → ["action","","engine"] → issueId=""
        assertFalse(handler.handleLink("copyfixprompt::OSS", mockEditor));
    }

    @Test
    void handleLink_WhenEngineNameIsEmpty_ReturnsFalse() {
        when(mockEditor.getProject()).thenReturn(mockProject);
        // trailing colon: "action:id:" → split → ["action","id",""] → engineName=""
        assertFalse(handler.handleLink("copyfixprompt:issue-id:", mockEditor));
    }

    // ===== Paths that reach getScanIssue =====

    @Test
    void handleLink_WhenVirtualFileIsNull_ReturnsFalse() {
        when(mockEditor.getProject()).thenReturn(mockProject);
        Document mockDoc = mock(Document.class);
        when(mockEditor.getDocument()).thenReturn(mockDoc);

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class)) {
            FileDocumentManager mockFdm = mock(FileDocumentManager.class);
            fdmMock.when(FileDocumentManager::getInstance).thenReturn(mockFdm);
            when(mockFdm.getFile(mockDoc)).thenReturn(null);

            assertFalse(handler.handleLink("copyfixprompt:issue-id:OSS", mockEditor));
        }
    }

    @Test
    void handleLink_WhenScanIssueNotFound_ReturnsFalse() {
        when(mockEditor.getProject()).thenReturn(mockProject);
        Document mockDoc = mock(Document.class);
        when(mockEditor.getDocument()).thenReturn(mockDoc);
        VirtualFile mockVFile = mock(VirtualFile.class);
        when(mockVFile.getPath()).thenReturn("/project/file.java");
        ProblemHolderService mockPhs = mock(ProblemHolderService.class);
        when(mockProject.getService(ProblemHolderService.class)).thenReturn(mockPhs);
        when(mockPhs.getScanIssueByFile(anyString())).thenReturn(Collections.emptyList());

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class)) {
            FileDocumentManager mockFdm = mock(FileDocumentManager.class);
            fdmMock.when(FileDocumentManager::getInstance).thenReturn(mockFdm);
            when(mockFdm.getFile(mockDoc)).thenReturn(mockVFile);

            assertFalse(handler.handleLink("copyfixprompt:issue-id:OSS", mockEditor));
        }
    }

    @Test
    void handleLink_WithFixAction_DelegatesToRemediationManagerAndReturnsTrue() {
        ScanIssue mockIssue = buildMatchingIssue("issue-123", ScanEngine.OSS);
        setupEditorWithIssue(mockIssue, "/project/file.java");

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class);
             MockedStatic<TelemetryService> telemetryMock = mockStatic(TelemetryService.class)) {
            stubFdm(fdmMock, "/project/file.java");

            assertTrue(handler.handleLink("copyfixprompt:issue-123:OSS", mockEditor));
            verify(mockRemediationManager).fixWithCxOneAssist(eq(mockProject), eq(mockIssue), eq("issue-123"));
        }
    }

    @Test
    void handleLink_WithViewDetailsAction_DelegatesToRemediationManagerAndReturnsTrue() {
        ScanIssue mockIssue = buildMatchingIssue("issue-456", ScanEngine.ASCA);
        setupEditorWithIssue(mockIssue, "/project/Main.java");

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class);
             MockedStatic<TelemetryService> telemetryMock = mockStatic(TelemetryService.class)) {
            stubFdm(fdmMock, "/project/Main.java");

            assertTrue(handler.handleLink("viewdetails:issue-456:ASCA", mockEditor));
            verify(mockRemediationManager).viewDetails(eq(mockProject), eq(mockIssue), eq("issue-456"));
        }
    }

    @Test
    void handleLink_WithIgnoreThisType_DelegatesToIgnoreManagerAndReturnsTrue() {
        ScanIssue mockIssue = buildMatchingIssue("issue-789", ScanEngine.OSS);
        setupEditorWithIssue(mockIssue, "/project/pom.xml");
        IgnoreManager mockIgnoreManager = mock(IgnoreManager.class);

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class);
             MockedStatic<TelemetryService> telemetryMock = mockStatic(TelemetryService.class);
             MockedStatic<IgnoreManager> ignoreMock = mockStatic(IgnoreManager.class)) {
            stubFdm(fdmMock, "/project/pom.xml");
            ignoreMock.when(() -> IgnoreManager.getInstance(any())).thenReturn(mockIgnoreManager);

            assertTrue(handler.handleLink("ignorethis:issue-789:OSS", mockEditor));
            verify(mockIgnoreManager).addIgnoredEntry(eq(mockIssue), eq("issue-789"));
        }
    }

    @Test
    void handleLink_WithIgnoreAllOfThisType_DelegatesToIgnoreManagerAndReturnsTrue() {
        ScanIssue mockIssue = buildMatchingIssue("issue-999", ScanEngine.OSS);
        setupEditorWithIssue(mockIssue, "/project/package.json");
        IgnoreManager mockIgnoreManager = mock(IgnoreManager.class);

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class);
             MockedStatic<TelemetryService> telemetryMock = mockStatic(TelemetryService.class);
             MockedStatic<IgnoreManager> ignoreMock = mockStatic(IgnoreManager.class)) {
            stubFdm(fdmMock, "/project/package.json");
            ignoreMock.when(() -> IgnoreManager.getInstance(any())).thenReturn(mockIgnoreManager);

            assertTrue(handler.handleLink("ignoreallofthis:issue-999:OSS", mockEditor));
            verify(mockIgnoreManager).addAllIgnoredEntry(eq(mockIssue), eq("issue-999"));
        }
    }

    @Test
    void handleLink_WithUnknownAction_ReturnsFalse() {
        ScanIssue mockIssue = buildMatchingIssue("issue-000", ScanEngine.OSS);
        setupEditorWithIssue(mockIssue, "/project/build.gradle");

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class)) {
            stubFdm(fdmMock, "/project/build.gradle");

            assertFalse(handler.handleLink("unknownaction:issue-000:OSS", mockEditor));
        }
    }

    // ===== getScanIssueUsingVulnerabilityId paths =====

    @Test
    void handleLink_WithVulnerabilityIdMatch_FindsIssueViaVulnerabilityAndReturnsTrue() {
        // scanIssueId is different from the link issueId → first lookup fails, second finds by vulnerability ID
        String vulnerabilityId = "vuln-id-123";
        ScanIssue issue = buildIssueWithVulnerability("different-scan-id", ScanEngine.OSS, vulnerabilityId);
        setupEditorWithIssue(issue, "/project/pom.xml");

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class);
             MockedStatic<TelemetryService> telemetryMock = mockStatic(TelemetryService.class)) {
            stubFdm(fdmMock, "/project/pom.xml");

            assertTrue(handler.handleLink("copyfixprompt:" + vulnerabilityId + ":OSS", mockEditor));
            verify(mockRemediationManager).fixWithCxOneAssist(eq(mockProject), eq(issue), eq(vulnerabilityId));
        }
    }

    @Test
    void handleLink_WithVulnerabilityId_NoMatchingVulnerability_ReturnsFalse() {
        // Both lookups fail: scanIssueId doesn't match, vulnerability ID doesn't match
        ScanIssue issue = buildIssueWithVulnerability("scan-id-abc", ScanEngine.OSS, "other-vuln-id");
        setupEditorWithIssue(issue, "/project/pom.xml");

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class)) {
            stubFdm(fdmMock, "/project/pom.xml");

            assertFalse(handler.handleLink("copyfixprompt:no-match:OSS", mockEditor));
        }
    }

    @Test
    void handleLink_WithVulnerabilityId_EngineNameMismatch_ReturnsFalse() {
        // Vulnerability ID matches but engine name doesn't
        String vulnerabilityId = "vuln-id-456";
        ScanIssue issue = buildIssueWithVulnerability("scan-id-xyz", ScanEngine.OSS, vulnerabilityId);
        setupEditorWithIssue(issue, "/project/pom.xml");

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class)) {
            stubFdm(fdmMock, "/project/pom.xml");

            assertFalse(handler.handleLink("copyfixprompt:" + vulnerabilityId + ":ASCA", mockEditor));
        }
    }

    @Test
    void handleLink_IssueWithEmptyVulnerabilityList_FallsBackToNullAndReturnsFalse() {
        // scanIssueId doesn't match, vulnerability list is empty → both lookups return null
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanIssueId()).thenReturn("some-other-id");
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        when(issue.getVulnerabilities()).thenReturn(Collections.emptyList());
        setupEditorWithIssue(issue, "/project/pom.xml");

        try (MockedStatic<FileDocumentManager> fdmMock = mockStatic(FileDocumentManager.class)) {
            stubFdm(fdmMock, "/project/pom.xml");

            assertFalse(handler.handleLink("copyfixprompt:vuln-999:OSS", mockEditor));
        }
    }

    // ---- Helpers ----

    private ScanIssue buildMatchingIssue(String issueId, ScanEngine engine) {
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanIssueId()).thenReturn(issueId);
        when(issue.getScanEngine()).thenReturn(engine);
        when(issue.getVulnerabilities()).thenReturn(Collections.emptyList());
        return issue;
    }

    private ScanIssue buildIssueWithVulnerability(String scanIssueId, ScanEngine engine, String vulnerabilityId) {
        Vulnerability vuln = new Vulnerability();
        vuln.setVulnerabilityId(vulnerabilityId);
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanIssueId()).thenReturn(scanIssueId);
        when(issue.getScanEngine()).thenReturn(engine);
        when(issue.getVulnerabilities()).thenReturn(List.of(vuln));
        return issue;
    }

    private void setupEditorWithIssue(ScanIssue issue, String filePath) {
        Document mockDoc = mock(Document.class);
        when(mockEditor.getProject()).thenReturn(mockProject);
        when(mockEditor.getDocument()).thenReturn(mockDoc);

        VirtualFile mockVFile = mock(VirtualFile.class);
        when(mockVFile.getPath()).thenReturn(filePath);

        ProblemHolderService mockPhs = mock(ProblemHolderService.class);
        when(mockProject.getService(ProblemHolderService.class)).thenReturn(mockPhs);
        when(mockPhs.getScanIssueByFile(filePath)).thenReturn(List.of(issue));
    }

    private void stubFdm(MockedStatic<FileDocumentManager> fdmMock, String filePath) {
        FileDocumentManager mockFdm = mock(FileDocumentManager.class);
        fdmMock.when(FileDocumentManager::getInstance).thenReturn(mockFdm);
        VirtualFile mockVFile = mock(VirtualFile.class);
        when(mockVFile.getPath()).thenReturn(filePath);
        when(mockFdm.getFile(any(Document.class))).thenReturn(mockVFile);
    }
}
