package com.checkmarx.intellij.devassist.test.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.remediation.DevAssistFix;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemHolderServiceTest {

    private ProblemHolderService service;
    private Project mockProject;
    private MessageBus messageBus;

    @BeforeEach
    void setUp() {
        mockProject = mock(Project.class);
        messageBus = mock(MessageBus.class);

        // Return the mocked message bus and a mocked IssueListener publisher
        doReturn(messageBus).when(mockProject).getMessageBus();
        ProblemHolderService.IssueListener publisher = mock(ProblemHolderService.IssueListener.class);
        when(messageBus.syncPublisher(ProblemHolderService.ISSUE_TOPIC)).thenReturn(publisher);

        service = new ProblemHolderService(mockProject);

        // Ensure project.getService(...) returns this service instance
        when(mockProject.getService(ProblemHolderService.class)).thenReturn(service);
    }

    @Test
    void testAddScanIssues_ValidInput() {
        String filePath = "testFile.java";
        List<ScanIssue> issues = Collections.singletonList(new ScanIssue());

        service.addScanIssues(filePath, issues);

        Map<String, List<ScanIssue>> allIssues = service.getAllIssues();
        assertTrue(allIssues.containsKey(filePath));
        assertEquals(1, allIssues.get(filePath).size());
    }

    @Test
    void testGetAllIssues_Empty() {
        Map<String, List<ScanIssue>> allIssues = service.getAllIssues();
        assertTrue(allIssues.isEmpty());
    }

    @Test
    void testRemoveAllScanIssuesOfType_ValidType() {
        String filePath = "testFile.java";
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.OSS);
        service.addScanIssues(filePath, Collections.singletonList(issue));

        service.removeAllScanIssuesOfType("OSS");
        assertTrue(service.getAllIssues().get(filePath).isEmpty());
    }

    @Test
    void testGetProblemDescriptors_NoDescriptors() {
        List<ProblemDescriptor> descriptors = service.getProblemDescriptors("nonExistentFile.java");
        assertTrue(descriptors.isEmpty());
    }

    @Test
    void testAddProblemDescriptors_ValidInput() {
        String filePath = "testFile.java";
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        service.addProblemDescriptors(filePath, Collections.singletonList(descriptor));

        List<ProblemDescriptor> descriptors = service.getProblemDescriptors(filePath);
        assertEquals(1, descriptors.size());
    }

    @Test
    void testRemoveProblemDescriptorsForFile_ValidFile() {
        String filePath = "testFile.java";
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        service.addProblemDescriptors(filePath, Collections.singletonList(descriptor));

        service.removeProblemDescriptorsForFile(filePath);
        assertTrue(service.getProblemDescriptors(filePath).isEmpty());
    }

    @Test
    void testAddToCxOneFindings_ValidInput() {
        PsiFile mockFile = mock(PsiFile.class);
        VirtualFile vf = mock(VirtualFile.class);

        when(mockFile.getProject()).thenReturn(mockProject);
        when(mockFile.getVirtualFile()).thenReturn(vf);
        when(vf.getPath()).thenReturn("testFile.java");

        List<ScanIssue> issues = Collections.singletonList(new ScanIssue());

        // Call the static helper which uses project.getService(...) internally (we stubbed it in setUp)
        ProblemHolderService.addToCxOneFindings(mockFile, issues);

        Map<String, List<ScanIssue>> allIssues = service.getAllIssues();
        assertTrue(allIssues.containsKey("testFile.java"));
        assertEquals(1, allIssues.get("testFile.java").size());
    }

    // ===== getScanIssueByFile =====

    @Test
    void getScanIssueByFile_returnsIssuesForSpecificFile() {
        String file1 = "file1.java";
        String file2 = "file2.java";
        ScanIssue issue1 = new ScanIssue();
        ScanIssue issue2 = new ScanIssue();
        service.addScanIssues(file1, List.of(issue1));
        service.addScanIssues(file2, List.of(issue2));

        List<ScanIssue> result = service.getScanIssueByFile(file1);

        assertEquals(1, result.size());
        assertEquals(issue1, result.get(0));
    }

    @Test
    void getScanIssueByFile_unknownPath_returnsEmptyList() {
        List<ScanIssue> result = service.getScanIssueByFile("nonexistent.java");
        assertTrue(result.isEmpty());
    }

    // ===== removeScanIssues =====

    @Test
    void removeScanIssues_removesAllIssuesForFile() {
        String filePath = "remove.java";
        service.addScanIssues(filePath, List.of(new ScanIssue()));

        service.removeScanIssues(filePath);

        assertTrue(service.getScanIssueByFile(filePath).isEmpty());
    }

    @Test
    void removeScanIssues_nonExistentPath_isNoOp() {
        // Should not throw
        service.removeScanIssues("nonexistent.java");
        assertTrue(service.getAllIssues().isEmpty());
    }

    @Test
    void removeScanIssues_nullPath_isNoOp() {
        service.removeScanIssues(null);
        assertTrue(service.getAllIssues().isEmpty());
    }

    // ===== removeScanIssuesByFileAndScanner =====

    @Test
    void removeScanIssuesByFileAndScanner_removesOnlyMatchingScanner() {
        String filePath = "multi.java";
        ScanIssue ascaIssue = mock(ScanIssue.class);
        when(ascaIssue.getScanEngine()).thenReturn(ScanEngine.ASCA);
        ScanIssue ossIssue = mock(ScanIssue.class);
        when(ossIssue.getScanEngine()).thenReturn(ScanEngine.OSS);

        service.addScanIssues(filePath, List.of(ascaIssue, ossIssue));
        service.removeScanIssuesByFileAndScanner("ASCA", filePath);

        List<ScanIssue> remaining = service.getScanIssueByFile(filePath);
        assertEquals(1, remaining.size());
        assertEquals(ScanEngine.OSS, remaining.get(0).getScanEngine());
    }

    @Test
    void removeScanIssuesByFileAndScanner_emptyScannerType_isNoOp() {
        String filePath = "file.java";
        ScanIssue issue = mock(ScanIssue.class);
        when(issue.getScanEngine()).thenReturn(ScanEngine.ASCA);
        service.addScanIssues(filePath, List.of(issue));

        service.removeScanIssuesByFileAndScanner("", filePath);

        assertEquals(1, service.getScanIssueByFile(filePath).size());
    }

    // ===== mergeScanIssues =====

    @Test
    void mergeScanIssues_noExistingIssues_addsAll() {
        String filePath = "merge.java";
        ScanIssue issue = new ScanIssue();

        service.mergeScanIssues(filePath, List.of(issue));

        assertEquals(1, service.getScanIssueByFile(filePath).size());
    }

    @Test
    void mergeScanIssues_existingIssues_appendsNew() {
        String filePath = "merge2.java";
        ScanIssue existing = new ScanIssue();
        ScanIssue newIssue = new ScanIssue();
        service.addScanIssues(filePath, List.of(existing));

        service.mergeScanIssues(filePath, List.of(newIssue));

        assertEquals(2, service.getScanIssueByFile(filePath).size());
    }

    // ===== mergeProblemDescriptors =====

    @Test
    void mergeProblemDescriptors_noExistingDescriptors_addsAll() {
        String filePath = "descr.java";
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);

        service.mergeProblemDescriptors(filePath, List.of(descriptor));

        assertEquals(1, service.getProblemDescriptors(filePath).size());
    }

    @Test
    void mergeProblemDescriptors_existingDescriptors_appendsNew() {
        String filePath = "descr2.java";
        ProblemDescriptor existing = mock(ProblemDescriptor.class);
        ProblemDescriptor newDescriptor = mock(ProblemDescriptor.class);
        service.addProblemDescriptors(filePath, List.of(existing));

        service.mergeProblemDescriptors(filePath, List.of(newDescriptor));

        assertEquals(2, service.getProblemDescriptors(filePath).size());
    }

    // ===== removeProblemDescriptorsForFileByScanner =====

    @Test
    void removeProblemDescriptorsForFileByScanner_nullScanEngine_isNoOp() {
        String filePath = "file.java";
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        service.addProblemDescriptors(filePath, List.of(descriptor));

        service.removeProblemDescriptorsForFileByScanner(filePath, null);

        assertEquals(1, service.getProblemDescriptors(filePath).size());
    }

    @Test
    void removeProblemDescriptorsForFileByScanner_fileNotInMap_isNoOp() {
        service.removeProblemDescriptorsForFileByScanner("/not/in/map.java", ScanEngine.ASCA);
        assertTrue(service.getProblemDescriptors("/not/in/map.java").isEmpty());
    }

    @Test
    void removeProblemDescriptorsForFileByScanner_withMatchingDescriptor_removesIt() {
        String filePath = "match.java";
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.ASCA);

        DevAssistFix fix = mock(DevAssistFix.class);
        when(fix.getScanIssue()).thenReturn(issue);
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        when(descriptor.getFixes()).thenReturn(new LocalQuickFix[]{fix});

        service.addProblemDescriptors(filePath, List.of(descriptor));
        service.removeProblemDescriptorsForFileByScanner(filePath, ScanEngine.ASCA);

        assertTrue(service.getProblemDescriptors(filePath).isEmpty());
    }

    @Test
    void removeProblemDescriptorsForFileByScanner_withNonMatchingDescriptor_keepsIt() {
        String filePath = "nomatch.java";
        ScanIssue issue = new ScanIssue();
        issue.setScanEngine(ScanEngine.OSS);

        DevAssistFix fix = mock(DevAssistFix.class);
        when(fix.getScanIssue()).thenReturn(issue);
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
        when(descriptor.getFixes()).thenReturn(new LocalQuickFix[]{fix});

        service.addProblemDescriptors(filePath, List.of(descriptor));
        service.removeProblemDescriptorsForFileByScanner(filePath, ScanEngine.ASCA);

        assertEquals(1, service.getProblemDescriptors(filePath).size());
    }

    // ===== getInstance =====

    @Test
    void getInstance_returnsServiceFromProject() {
        ProblemHolderService result = ProblemHolderService.getInstance(mockProject);
        assertSame(service, result);
    }
}
