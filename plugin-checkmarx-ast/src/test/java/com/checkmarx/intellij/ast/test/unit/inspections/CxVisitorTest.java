package com.checkmarx.intellij.ast.test.unit.inspections;

import com.checkmarx.ast.results.result.Node;
import com.checkmarx.ast.results.result.Result;
import com.checkmarx.intellij.inspections.CxVisitor;
import com.checkmarx.intellij.project.ProjectResultsService;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CxVisitorTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private PsiElement mockElement;

    @Mock
    private PsiFile mockFile;

    @Mock
    private Project mockProject;

    @Mock
    private Document mockDocument;

    @Mock
    private PsiDocumentManager mockPsiDocumentManager;

    @Mock
    private VirtualFile mockVirtualFile;

    @Mock
    private ProjectResultsService mockResultsService;

    @Mock
    private Node mockNode;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Result mockResult;

    private CxVisitor cxVisitor;

    @BeforeEach
    void setUp() {
        cxVisitor = new CxVisitor(mockHolder);
    }

    @Test
    void visitElement_WithEmptyTextRange_DoesNotRegisterProblem() {
        // Arrange
        when(mockElement.getTextRange()).thenReturn(new TextRange(10, 10));

        // Act
        cxVisitor.visitElement(mockElement);

        // Assert
        verify(mockHolder, never()).registerProblem(
            any(PsiElement.class),
            anyString(),
            eq(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        );
    }

    @Test
    void visitElement_WithNoDocument_DoesNotRegisterProblem() {
        // Arrange
        when(mockElement.getTextRange()).thenReturn(new TextRange(10, 15));
        when(mockElement.getContainingFile()).thenReturn(mockFile);
        when(mockFile.getProject()).thenReturn(mockProject);

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class)) {
            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);
            when(mockPsiDocumentManager.getDocument(mockFile)).thenReturn(null);

            // Act
            cxVisitor.visitElement(mockElement);

            // Assert
            verify(mockHolder, never()).registerProblem(
                any(PsiElement.class),
                anyString(),
                eq(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            );
        }
    }

    @Test
    void visitElement_WithMatchingNode_RegistersProblem() {
        // Arrange
        when(mockElement.getTextRange()).thenReturn(new TextRange(10, 15));
        when(mockElement.getContainingFile()).thenReturn(mockFile);
        when(mockElement.getProject()).thenReturn(mockProject);
        when(mockFile.getProject()).thenReturn(mockProject);
        when(mockFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.getName()).thenReturn("/test/path");
        when(mockElement.getTextOffset()).thenReturn(10);

        when(mockNode.getColumn()).thenReturn(1);
        when(mockNode.getLength()).thenReturn(5);
        when(mockNode.getDefinitions()).thenReturn("1");
        when(mockNode.getNodeId()).thenReturn(1);
        when(mockNode.getName()).thenReturn("test node");

        // Mock the method chain
        when(mockResult.getData().getGroup()).thenReturn("test group");
        when(mockResult.getData().getQueryName()).thenReturn("test query");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class)) {
            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);
            when(mockPsiDocumentManager.getDocument(mockFile)).thenReturn(mockDocument);
            when(mockDocument.getLineNumber(10)).thenReturn(0);
            when(mockDocument.getLineStartOffset(0)).thenReturn(10);

            when(mockProject.getService(ProjectResultsService.class)).thenReturn(mockResultsService);
            when(mockResultsService.getResultsForFileAndLine(mockProject, "/test/path", 1))
                    .thenReturn(Collections.singletonList(mockNode));
            when(mockResultsService.getResultForNode(mockNode)).thenReturn(mockResult);

            // Act
            cxVisitor.visitElement(mockElement);

            // Assert
            verify(mockHolder).registerProblem(
                eq(mockElement),
                contains("test group"),
                eq(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            );
        }
    }

    @Test
    void visitElement_WithAlreadyRegisteredNode_DoesNotRegisterProblemAgain() {
        // Arrange
        when(mockElement.getTextRange()).thenReturn(new TextRange(10, 15));
        when(mockElement.getContainingFile()).thenReturn(mockFile);
        when(mockElement.getProject()).thenReturn(mockProject);
        when(mockFile.getProject()).thenReturn(mockProject);
        when(mockFile.getVirtualFile()).thenReturn(mockVirtualFile);
        when(mockVirtualFile.getName()).thenReturn("/test/path");
        when(mockElement.getTextOffset()).thenReturn(10);

        when(mockNode.getColumn()).thenReturn(1);
        when(mockNode.getLength()).thenReturn(5);
        when(mockNode.getDefinitions()).thenReturn("1");
        when(mockNode.getNodeId()).thenReturn(1);
        when(mockNode.getName()).thenReturn("test node");

        // Mock the method chain
        when(mockResult.getData().getGroup()).thenReturn("test group");
        when(mockResult.getData().getQueryName()).thenReturn("test query");

        try (MockedStatic<PsiDocumentManager> docManagerMock = mockStatic(PsiDocumentManager.class)) {
            docManagerMock.when(() -> PsiDocumentManager.getInstance(mockProject))
                    .thenReturn(mockPsiDocumentManager);
            when(mockPsiDocumentManager.getDocument(mockFile)).thenReturn(mockDocument);
            when(mockDocument.getLineNumber(10)).thenReturn(0);
            when(mockDocument.getLineStartOffset(0)).thenReturn(10);

            when(mockProject.getService(ProjectResultsService.class)).thenReturn(mockResultsService);
            when(mockResultsService.getResultsForFileAndLine(mockProject, "/test/path", 1))
                    .thenReturn(Collections.singletonList(mockNode));
            when(mockResultsService.getResultForNode(mockNode)).thenReturn(mockResult);

            // First visit to register the node
            cxVisitor.visitElement(mockElement);
            // Second visit should not register again
            cxVisitor.visitElement(mockElement);

            // Assert
            verify(mockHolder, times(1)).registerProblem(
                any(PsiElement.class),
                anyString(),
                eq(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            );
        }
    }
} 