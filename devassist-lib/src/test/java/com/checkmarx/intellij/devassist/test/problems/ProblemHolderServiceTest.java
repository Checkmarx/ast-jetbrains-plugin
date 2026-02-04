package com.checkmarx.intellij.devassist.test.problems;

import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
