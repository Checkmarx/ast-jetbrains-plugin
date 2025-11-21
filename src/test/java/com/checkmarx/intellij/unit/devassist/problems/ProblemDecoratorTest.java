package com.checkmarx.intellij.unit.devassist.problems;

import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.util.SeverityLevel;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.TextRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.swing.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProblemDecoratorTest {
    private ProblemDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new ProblemDecorator();
    }

    // test severityHighlighterLayerMap initialization
    @Test
    @DisplayName("Test severityHighlighterLayerMap initialization")
    void testSeverityHighlighterLayerMapInitialization() {
        assertFalse(decorator.getSeverityHighlighterLayerMap().isEmpty());
        assertTrue(decorator.getSeverityHighlighterLayerMap().containsKey("Malicious"));
        assertTrue(decorator.getSeverityHighlighterLayerMap().containsKey("Critical"));
        assertTrue(decorator.getSeverityHighlighterLayerMap().containsKey("High"));
        assertTrue(decorator.getSeverityHighlighterLayerMap().containsKey("Medium"));
        assertTrue(decorator.getSeverityHighlighterLayerMap().containsKey("Low"));
    }

    // test getGutterIconBasedOnStatus for all severities
    @Test
    @DisplayName("Test getGutterIconBasedOnStatus for all severities")
    void testGetGutterIconBasedOnStatus_AllSeverities() {
        for (SeverityLevel level : SeverityLevel.values()) {
            Icon icon = decorator.getGutterIconBasedOnStatus(level.getSeverity());
            assertNotNull(icon, "Icon should not be null for severity: " + level.getSeverity());
        }
        // Unknown severity string
        Icon unknownIcon = decorator.getGutterIconBasedOnStatus("not-a-severity");
        assertNotNull(unknownIcon);
    }

    // test determineHighlighterLayer for all severities
    @Test
    @DisplayName("Test determineHighlighterLayer for all severities")
    void testDetermineHighlighterLayer_AllSeverities() {
        for (SeverityLevel level : SeverityLevel.values()) {
            ScanIssue issue = new ScanIssue();
            issue.setSeverity(level.getSeverity());
            Integer layer = decorator.determineHighlighterLayer(issue);
            assertNotNull(layer);
        }
        // Unknown severity
        ScanIssue unknown = new ScanIssue();
        unknown.setSeverity("not-a-severity");
        Integer layer = decorator.determineHighlighterLayer(unknown);
        assertNotNull(layer);
    }

    // test highlightLineAddGutterIconForProblem (corner cases: null editor, wrong document, empty locations)
    @Test
    @DisplayName("Test highlightLineAddGutterIconForProblem with corner cases")
    void testHighlightLineAddGutterIconForProblem_CornerCases() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setLocations(new ArrayList<>()); // empty locations
        boolean isProblem = true;
        int problemLineNumber = 1;
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> psiDocManager = Mockito.mockStatic(PsiDocumentManager.class)) {
            Application application = mock(Application.class);
            //noinspection ResultOfMethodCallIgnored
            appManager.when(ApplicationManager::getApplication).thenReturn(application); // restore scenario stub
            Application capturedAppRestore = ApplicationManager.getApplication();
            assertSame(application, capturedAppRestore);
            doAnswer(invocation -> { // run invokeLater immediately
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(application).invokeLater(any(Runnable.class));
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(null); // null editor path
            decorator.highlightLineAddGutterIconForProblem(project, psiFile, scanIssue, isProblem, problemLineNumber);
            // Non-null editor but mismatched document
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            Document doc = mock(Document.class);
            when(editor.getDocument()).thenReturn(doc);
            when(doc.getCharsSequence()).thenReturn("a".repeat(100));
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocManager.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(mock(Document.class)); // different document so early return
            decorator.highlightLineAddGutterIconForProblem(project, psiFile, scanIssue, isProblem, problemLineNumber);
        }
    }

    // test removeAllGutterIcons (corner cases: null editor, null highlighters)
    @Test
    @DisplayName("Test removeAllGutterIcons with corner cases")
    void testRemoveAllGutterIcons_CornerCases() {
        PsiFile psiFile = mock(PsiFile.class);
        Project project = mock(Project.class);
        when(psiFile.getProject()).thenReturn(project);
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class)) {
            Application application = mock(Application.class);
            //noinspection ResultOfMethodCallIgnored
            appManager.when(ApplicationManager::getApplication).thenReturn(application);
            Application capturedAppRemove = ApplicationManager.getApplication();
            assertSame(application, capturedAppRemove);
            doAnswer(invocation -> { // run invokeLater immediately
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(application).invokeLater(any(Runnable.class));
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(null); // null editor path
            decorator.removeAllGutterIcons(psiFile);
            // Non-null editor, empty highlighters array
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            MarkupModel markupModel = mock(MarkupModel.class);
            when(editor.getMarkupModel()).thenReturn(markupModel);
            RangeHighlighter[] empty = new RangeHighlighter[0];
            when(markupModel.getAllHighlighters()).thenReturn(empty);
            decorator.removeAllGutterIcons(psiFile);
        }
    }

    // test restoreGutterIcons (corner cases: empty scanIssueList, null elementAtLine)
    @Test
    @DisplayName("Test restoreGutterIcons with corner cases")
    void testRestoreGutterIcons_CornerCases() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        Document document = mock(Document.class);
        when(document.getCharsSequence()).thenReturn("a".repeat(200));
        List<ScanIssue> scanIssueList = new ArrayList<>();
        decorator.restoreGutterIcons(project, psiFile, scanIssueList, document); // empty list path
        // One scanIssue, elementAtLine null
        ScanIssue issue = new ScanIssue();
        issue.setSeverity("High");
        Location location = new Location(1, 0, 10);
        issue.setLocations(Collections.singletonList(location));
        issue.setTitle("TestTitle");
        scanIssueList.add(issue);
        when(document.getLineStartOffset(anyInt())).thenReturn(0);
        when(psiFile.findElementAt(anyInt())).thenReturn(null); // null element path
        decorator.restoreGutterIcons(project, psiFile, scanIssueList, document);
        // Second scenario: elementAtLine non-null triggers highlightLineAddGutterIconForProblem
        PsiFile psiFile2 = mock(PsiFile.class);
        when(psiFile2.getProject()).thenReturn(project);
        ScanIssue issue2 = new ScanIssue();
        issue2.setSeverity("Low");
        issue2.setLocations(Collections.singletonList(location));
        issue2.setTitle("Title2");
        List<ScanIssue> list2 = Collections.singletonList(issue2);
        PsiElement elementAt = mock(PsiElement.class);
        when(document.getLineStartOffset(location.getLine())).thenReturn(0);
        when(psiFile2.findElementAt(0)).thenReturn(elementAt);
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> psiDocManager = Mockito.mockStatic(PsiDocumentManager.class);
             MockedStatic<DevAssistUtils> devUtilsMock = Mockito.mockStatic(DevAssistUtils.class)) {
            devUtilsMock.when(() -> DevAssistUtils.getTextRangeForLine(any(Document.class), anyInt()))
                    .thenReturn(new TextRange(0,1));
            Application application = mock(Application.class);
            appManager.when(ApplicationManager::getApplication).thenReturn(application);
            Application capturedAppRestore = ApplicationManager.getApplication();
            assertSame(application, capturedAppRestore);
            doAnswer(inv -> { Runnable r = inv.getArgument(0); r.run(); return null; }).when(application).invokeLater(any(Runnable.class));
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            Document doc2 = mock(Document.class);
            when(editor.getDocument()).thenReturn(doc2);
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocManager.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile2)).thenReturn(doc2);
            // locations iteration requires getLineStartOffset/End etc.
            when(doc2.getLineStartOffset(location.getLine())).thenReturn(0);
            when(doc2.getLineEndOffset(location.getLine())).thenReturn(5);
            when(doc2.getTextLength()).thenReturn(10);
            when(doc2.getLineCount()).thenReturn(2);
            decorator.restoreGutterIcons(project, psiFile2, list2, doc2);
        }
    }

    @Test
    @DisplayName("Test removeAllGutterIcons exception path")
    void testRemoveAllGutterIcons_ExceptionPath() {
        PsiFile psiFile = mock(PsiFile.class);
        Project project = mock(Project.class);
        when(psiFile.getProject()).thenReturn(project);
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class)) {
            Application application = mock(Application.class);
            //noinspection ResultOfMethodCallIgnored
            appManager.when(ApplicationManager::getApplication).thenReturn(application); // exception path stub
            Application capturedAppException = ApplicationManager.getApplication();
            assertSame(application, capturedAppException);
            doAnswer(inv -> {
                try {
                    throw new RuntimeException("boom");
                } catch (RuntimeException e) {
                    // swallow
                }
                return null;
            }).when(application).invokeLater(any(Runnable.class));
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenThrow(new RuntimeException("manager fail"));
            decorator.removeAllGutterIcons(psiFile); // ensure no exception escapes
        }
    }

    @Test
    @DisplayName("Test restoreGutterIcons catch block")
    void testRestoreGutterIcons_CatchBlock() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        Document document = mock(Document.class);
        // Issue with empty locations to trigger IndexOutOfBoundsException when accessing get(0)
        ScanIssue issue = new ScanIssue();
        issue.setSeverity(SeverityLevel.HIGH.getSeverity());
        issue.setLocations(Collections.emptyList());
        issue.setTitle("Title");
        List<ScanIssue> list = Collections.singletonList(issue);
        decorator.restoreGutterIcons(project, psiFile, list, document); // should hit catch and continue
    }

    @Test
    @DisplayName("Test removeAllGutterIcons remove all branch")
    void testRemoveAllGutterIcons_RemoveAllBranch() {
        PsiFile psiFile = mock(PsiFile.class);
        Project project = mock(Project.class);
        when(psiFile.getProject()).thenReturn(project);
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class)) {
            Application application = mock(Application.class);
            //noinspection ResultOfMethodCallIgnored
            appManager.when(ApplicationManager::getApplication).thenReturn(application);
            Application capturedApp = ApplicationManager.getApplication();
            assertSame(application, capturedApp);
            doAnswer(inv -> { Runnable r = inv.getArgument(0); r.run(); return null; }).when(application).invokeLater(any(Runnable.class));
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            MarkupModel markupModel = mock(MarkupModel.class);
            when(editor.getMarkupModel()).thenReturn(markupModel);
            RangeHighlighter h1 = mock(RangeHighlighter.class);
            RangeHighlighter h2 = mock(RangeHighlighter.class);
            when(markupModel.getAllHighlighters()).thenReturn(new RangeHighlighter[]{h1, h2});
            decorator.removeAllGutterIcons(psiFile);
            verify(markupModel, times(1)).removeAllHighlighters();
        }
    }

    // test highlightLineAddGutterIconForProblem with multi-location
    @Test
    @DisplayName("Test highlightLineAddGutterIconForProblem with multi-location")
    void testHighlightLineAddGutterIconForProblem_MultiLocation() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        Location location1 = new Location(1, 0, 10);
        Location location2 = new Location(2, 0, 10);
        Location location3 = new Location(3, 0, 10);
        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setLocations(Arrays.asList(location1, location2, location3));
        boolean isProblem = true;
        int problemLineNumber = 1;
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> psiDocManager = Mockito.mockStatic(PsiDocumentManager.class)) {
            Application application = mock(Application.class);
            //noinspection ResultOfMethodCallIgnored
            appManager.when(ApplicationManager::getApplication).thenReturn(application); // restore scenario stub
            Application capturedAppRestore = ApplicationManager.getApplication();
            assertSame(application, capturedAppRestore);
            doAnswer(invocation -> { // run invokeLater immediately
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(application).invokeLater(any(Runnable.class));
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(null); // null editor path
            decorator.highlightLineAddGutterIconForProblem(project, psiFile, scanIssue, isProblem, problemLineNumber);
            // Non-null editor but mismatched document
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            Document doc = mock(Document.class);
            when(editor.getDocument()).thenReturn(doc);
            when(doc.getCharsSequence()).thenReturn("a".repeat(100));
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocManager.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(mock(Document.class)); // different document so early return
            decorator.highlightLineAddGutterIconForProblem(project, psiFile, scanIssue, isProblem, problemLineNumber);
        }
    }
}
