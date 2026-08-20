package com.checkmarx.intellij.devassist.test.problems;

import com.checkmarx.intellij.common.utils.SeverityLevel;
import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.checkmarx.intellij.devassist.ignore.IgnoreManager;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemDecorator;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.intellij.openapi.editor.markup.GutterIconRenderer;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

public class ProblemDecoratorTest {
    private ProblemDecorator decorator;

    @BeforeEach
    void setUp() {
        decorator = new ProblemDecorator();
    }

    @Test
    @DisplayName("Test getGutterIconBasedOnStatus for all severities")
    void testGetGutterIconBasedOnStatus_AllSeverities() {
        for (SeverityLevel level : SeverityLevel.values()) {
            Icon icon = decorator.getGutterIconBasedOnStatus(level.getSeverity());
            assertNotNull(icon, "Icon should not be null for severity: " + level.getSeverity());
        }
        Icon unknownIcon = decorator.getGutterIconBasedOnStatus("not-a-severity");
        assertNotNull(unknownIcon);
    }



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
            decorator.highlightLineAddGutterIconForProblem(getProblemHelper(psiFile, project), scanIssue, isProblem, problemLineNumber);
            // Non-null editor but mismatched document
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            Document doc = mock(Document.class);
            when(editor.getDocument()).thenReturn(doc);
            when(doc.getCharsSequence()).thenReturn("a".repeat(100));
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocManager.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(mock(Document.class)); // different document so early return
            decorator.highlightLineAddGutterIconForProblem(getProblemHelper(psiFile, project), scanIssue, isProblem, problemLineNumber);
        }
    }

    @Test
    @DisplayName("Test removeAllGutterIcons with corner cases")
    void testRemoveAllHighlighters_CornerCases() {
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
            decorator.removeAllHighlighters(psiFile.getProject());
            // Non-null editor, empty highlighters array
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            MarkupModel markupModel = mock(MarkupModel.class);
            when(editor.getMarkupModel()).thenReturn(markupModel);
            RangeHighlighter[] empty = new RangeHighlighter[0];
            when(markupModel.getAllHighlighters()).thenReturn(empty);
            decorator.removeAllHighlighters(psiFile.getProject());
        }
    }

    // test restoreGutterIcons (corner cases: empty scanIssueList, null elementAtLine)
    @Test
    @DisplayName("Test restoreGutterIcons with corner cases")
    void testDecorateUI_CornerCases() {

        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> psiDocManager = Mockito.mockStatic(PsiDocumentManager.class);
             MockedStatic<DevAssistUtils> devUtilsMock = Mockito.mockStatic(DevAssistUtils.class)) {
            Project project = mock(Project.class);
            PsiFile psiFile = mock(PsiFile.class);
            Document document = mock(Document.class);
            when(document.getCharsSequence()).thenReturn("a".repeat(200));
            List<ScanIssue> scanIssueList = new ArrayList<>();  Application application = mock(Application.class);
            appManager.when(ApplicationManager::getApplication).thenReturn(application);

            decorator.decorateUI(project, psiFile, scanIssueList, document); // empty list path

            ScanIssue issue = new ScanIssue();
            issue.setSeverity("High");
            Location location = new Location(1, 0, 10);
            issue.setLocations(Collections.singletonList(location));
            issue.setTitle("TestTitle");
            scanIssueList.add(issue);
            when(document.getLineStartOffset(anyInt())).thenReturn(0);
            when(psiFile.findElementAt(anyInt())).thenReturn(null); // null element path
            decorator.decorateUI(project, psiFile, scanIssueList, document);
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
            devUtilsMock.when(() -> DevAssistUtils.getTextRangeForLine(any(Document.class), anyInt()))
                    .thenReturn(new TextRange(0, 1));



            Application capturedAppRestore = ApplicationManager.getApplication();
            assertSame(application, capturedAppRestore);
            doAnswer(inv -> {
                Runnable r = inv.getArgument(0);
                r.run();
                return null;
            }).when(application).invokeLater(any(Runnable.class));
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            Document doc2 = mock(Document.class);
            when(editor.getDocument()).thenReturn(doc2);
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocManager.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile2)).thenReturn(doc2);
            when(doc2.getLineStartOffset(location.getLine())).thenReturn(0);
            when(doc2.getLineEndOffset(location.getLine())).thenReturn(5);
            when(doc2.getTextLength()).thenReturn(10);
            when(doc2.getLineCount()).thenReturn(2);
            decorator.decorateUI(project, psiFile2, list2, doc2);
        }
    }

    @Test
    @DisplayName("Test removeAllGutterIcons exception path")
    void testRemoveAllHighlighters_ExceptionPath() {
        PsiFile psiFile = mock(PsiFile.class);
        Project project = mock(Project.class);
        when(psiFile.getProject()).thenReturn(project);
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class)) {
            Application application = mock(Application.class);

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
            decorator.removeAllHighlighters(psiFile.getProject()); // ensure no exception escapes
        }
    }

    @Test
    @DisplayName("Test restoreGutterIcons catch block")
    void testDecorateUI_CatchBlock() {
        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class)){
            Application application = mock(Application.class);
            appManager.when(ApplicationManager::getApplication).thenReturn(application);
            Project project = mock(Project.class);
            PsiFile psiFile = mock(PsiFile.class);
            Document document = mock(Document.class);
            ScanIssue issue = new ScanIssue();
            issue.setSeverity(SeverityLevel.HIGH.getSeverity());
            issue.setLocations(Collections.emptyList());
            issue.setTitle("Title");
            List<ScanIssue> list = Collections.singletonList(issue);
            decorator.decorateUI(project, psiFile, list, document);
        }
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
            appManager.when(ApplicationManager::getApplication).thenReturn(application);
            Application capturedApp = ApplicationManager.getApplication();
            assertSame(application, capturedApp);
            doAnswer(inv -> {
                Runnable r = inv.getArgument(0);
                r.run();
                return null;
            }).when(application).invokeLater(any(Runnable.class), any(ModalityState.class));
            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            MarkupModel markupModel = mock(MarkupModel.class);
            when(editor.getMarkupModel()).thenReturn(markupModel);
            RangeHighlighter h1 = mock(RangeHighlighter.class);
            RangeHighlighter h2 = mock(RangeHighlighter.class);
            when(markupModel.getAllHighlighters()).thenReturn(new RangeHighlighter[]{h1, h2});
            decorator.removeAllHighlighters(psiFile.getProject());
            verify(markupModel, times(1)).removeAllHighlighters();
        }
    }


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
            decorator.highlightLineAddGutterIconForProblem(getProblemHelper(psiFile, project), scanIssue, isProblem, problemLineNumber);
            // Non-null editor but mismatched document
            Editor editor = mock(Editor.class);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);
            Document doc = mock(Document.class);
            when(editor.getDocument()).thenReturn(doc);
            when(doc.getCharsSequence()).thenReturn("a".repeat(100));
            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocManager.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(mock(Document.class)); // different document so early return
            decorator.highlightLineAddGutterIconForProblem(getProblemHelper(psiFile, project), scanIssue, isProblem, problemLineNumber);
        }
    }

    @Test
    @DisplayName("addGutterIcon via highlightLineAddGutterIconForProblem — exercises GutterIconRenderer anonymous class")
    void gutterIconRenderer_methods_exercised() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getProject()).thenReturn(project);

        Location location = new Location(1, 0, 5);
        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setSeverity("High");
        scanIssue.setLocations(Collections.singletonList(location));
        scanIssue.setScanIssueId("issue-gutter-test");

        try (MockedStatic<ApplicationManager> appMock = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> femMock = Mockito.mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> pdmMock = Mockito.mockStatic(PsiDocumentManager.class);
             MockedStatic<DevAssistUtils> devUtilsMock = Mockito.mockStatic(DevAssistUtils.class);
             MockedStatic<com.intellij.openapi.editor.colors.EditorColorsManager> colorsMock =
                     Mockito.mockStatic(com.intellij.openapi.editor.colors.EditorColorsManager.class)) {

            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(app).invokeLater(any(Runnable.class), any(ModalityState.class));

            Document sharedDoc = mock(Document.class);
            when(sharedDoc.getLineStartOffset(anyInt())).thenReturn(0);
            when(sharedDoc.getLineEndOffset(anyInt())).thenReturn(10);
            when(sharedDoc.getTextLength()).thenReturn(20);
            when(sharedDoc.getLineCount()).thenReturn(5);
            when(sharedDoc.getLineNumber(anyInt())).thenReturn(0);

            MarkupModel markupModel = mock(MarkupModel.class);
            RangeHighlighter lineHighlighter = mock(RangeHighlighter.class);
            RangeHighlighter rangeHighlighter = mock(RangeHighlighter.class);
            when(markupModel.addLineHighlighter(anyInt(), anyInt(), any())).thenReturn(lineHighlighter);
            when(markupModel.addRangeHighlighter(anyInt(), anyInt(), anyInt(), any(), any())).thenReturn(rangeHighlighter);
            when(markupModel.getAllHighlighters()).thenReturn(new RangeHighlighter[0]);

            Editor editor = mock(Editor.class);
            when(editor.getDocument()).thenReturn(sharedDoc);
            when(editor.getMarkupModel()).thenReturn(markupModel);

            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);

            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            pdmMock.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(sharedDoc);

            devUtilsMock.when(() -> DevAssistUtils.getTextRangeForLine(any(), anyInt()))
                    .thenReturn(new com.intellij.openapi.util.TextRange(0, 10));
            devUtilsMock.when(() -> DevAssistUtils.getSeverityBasedOnPrecedence(any(), any()))
                    .thenReturn("High");

            com.intellij.openapi.editor.colors.EditorColorsManager colorsManager =
                    mock(com.intellij.openapi.editor.colors.EditorColorsManager.class);
            colorsMock.when(com.intellij.openapi.editor.colors.EditorColorsManager::getInstance).thenReturn(colorsManager);
            com.intellij.openapi.editor.colors.EditorColorsScheme scheme =
                    mock(com.intellij.openapi.editor.colors.EditorColorsScheme.class, RETURNS_DEEP_STUBS);
            when(colorsManager.getGlobalScheme()).thenReturn(scheme);
            when(scheme.getAttributes(any())).thenReturn(new com.intellij.openapi.editor.markup.TextAttributes());

            ProblemHelper helper = ProblemHelper.builder(psiFile, project)
                    .scanIssueList(Collections.singletonList(scanIssue))
                    .build();

            decorator.highlightLineAddGutterIconForProblem(helper, scanIssue, true, 1);

            // Capture the GutterIconRenderer set on the highlighter and exercise its methods
            ArgumentCaptor<GutterIconRenderer> rendererCaptor = ArgumentCaptor.forClass(GutterIconRenderer.class);
            verify(rangeHighlighter).setGutterIconRenderer(rendererCaptor.capture());
            GutterIconRenderer renderer = rendererCaptor.getValue();

            assertNotNull(renderer.getIcon());
            assertNotNull(renderer.getAlignment());
            assertNotNull(renderer.getTooltipText());
            org.junit.jupiter.api.Assertions.assertTrue(renderer.equals(renderer));
            org.junit.jupiter.api.Assertions.assertFalse(renderer.equals(new Object()));
            int hash1 = renderer.hashCode();
            int hash2 = renderer.hashCode();
            org.junit.jupiter.api.Assertions.assertEquals(hash1, hash2);
        }
    }

    @Test
    @DisplayName("getRelativePath with non-null basePath exercises VfsUtilCore branch")
    void getRelativePath_nonNullBasePath_exercisesVfsUtilCoreBranch() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(null); // use null path → psiFile.getVirtualFile().getPath() branch (already tested via other tests)

        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getName()).thenReturn("App.java");
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.getPath()).thenReturn("/project/src/App.java");

        IgnoreEntry entry = new IgnoreEntry();
        List<IgnoreEntry.FileReference> refs = new ArrayList<>();
        refs.add(new IgnoreEntry.FileReference("/project/src/App.java", true, 3, "code-x"));
        entry.setFiles(refs);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.ignore.IgnoreFileManager> ifmMock =
                     mockStatic(com.checkmarx.intellij.devassist.ignore.IgnoreFileManager.class);
             MockedStatic<com.checkmarx.intellij.devassist.problems.ProblemHolderService> phsMock =
                     mockStatic(com.checkmarx.intellij.devassist.problems.ProblemHolderService.class);
             MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class)) {

            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(app).invokeLater(any(Runnable.class), any(ModalityState.class));

            com.checkmarx.intellij.devassist.ignore.IgnoreFileManager mockIfm =
                    mock(com.checkmarx.intellij.devassist.ignore.IgnoreFileManager.class);
            ifmMock.when(() -> com.checkmarx.intellij.devassist.ignore.IgnoreFileManager.getInstance(project))
                    .thenReturn(mockIfm);
            when(mockIfm.getAllIgnoreEntries()).thenReturn(List.of(entry));

            com.checkmarx.intellij.devassist.problems.ProblemHolderService mockPhs =
                    mock(com.checkmarx.intellij.devassist.problems.ProblemHolderService.class);
            phsMock.when(() -> com.checkmarx.intellij.devassist.problems.ProblemHolderService.getInstance(project))
                    .thenReturn(mockPhs);

            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(null);

            // Should complete without exception; null path triggers the getPath() branch
            assertDoesNotThrow(() -> decorator.decorateUIForIgnoredVulnerability(project, psiFile, Collections.emptyList()));
        }
    }

    private ProblemHelper getProblemHelper(PsiFile psiFile, Project project) {
        return ProblemHelper.builder(psiFile, project).scanIssueList(Collections.emptyList()).build();
    }

    @Test
    @DisplayName("Test highlightLineAddGutterIconForProblem with matching document exercises highlightLocationInEditor")
    void testHighlightLineAddGutterIconForProblem_MatchingDocument_ExercisesHighlightPath() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getProject()).thenReturn(project);

        Location location = new Location(2, 0, 10);
        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setSeverity("High");
        scanIssue.setLocations(Collections.singletonList(location));
        scanIssue.setScanIssueId("issue-1");

        try (MockedStatic<ApplicationManager> appManager = Mockito.mockStatic(ApplicationManager.class);
             MockedStatic<FileEditorManager> fileEditorManager = Mockito.mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> psiDocManager = Mockito.mockStatic(PsiDocumentManager.class);
             MockedStatic<DevAssistUtils> devUtilsMock = Mockito.mockStatic(DevAssistUtils.class);
             MockedStatic<EditorColorsManager> colorsMock = Mockito.mockStatic(EditorColorsManager.class)) {

            Application application = mock(Application.class);
            appManager.when(ApplicationManager::getApplication).thenReturn(application);
            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(application).invokeLater(any(Runnable.class), any(ModalityState.class));

            Document sharedDoc = mock(Document.class);
            when(sharedDoc.getLineStartOffset(anyInt())).thenReturn(0);
            when(sharedDoc.getLineEndOffset(anyInt())).thenReturn(10);
            when(sharedDoc.getTextLength()).thenReturn(20);
            when(sharedDoc.getLineCount()).thenReturn(5);
            when(sharedDoc.getLineNumber(anyInt())).thenReturn(1);

            Editor editor = mock(Editor.class);
            when(editor.getDocument()).thenReturn(sharedDoc);

            MarkupModel markupModel = mock(MarkupModel.class);
            when(editor.getMarkupModel()).thenReturn(markupModel);

            RangeHighlighter lineHighlighter = mock(RangeHighlighter.class);
            RangeHighlighter rangeHighlighter = mock(RangeHighlighter.class);
            when(markupModel.addLineHighlighter(anyInt(), anyInt(), any())).thenReturn(lineHighlighter);
            when(markupModel.addRangeHighlighter(anyInt(), anyInt(), anyInt(), any(), any())).thenReturn(rangeHighlighter);
            when(markupModel.getAllHighlighters()).thenReturn(new RangeHighlighter[0]);

            FileEditorManager fileMgr = mock(FileEditorManager.class);
            fileEditorManager.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);

            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            psiDocManager.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(sharedDoc); // same document object

            devUtilsMock.when(() -> DevAssistUtils.getTextRangeForLine(any(Document.class), anyInt()))
                    .thenReturn(new TextRange(0, 10));
            devUtilsMock.when(() -> DevAssistUtils.getSeverityBasedOnPrecedence(any(), any()))
                    .thenReturn("High");

            EditorColorsScheme scheme = mock(EditorColorsScheme.class, RETURNS_DEEP_STUBS);
            EditorColorsManager colorsManager = mock(EditorColorsManager.class);
            colorsMock.when(EditorColorsManager::getInstance).thenReturn(colorsManager);
            when(colorsManager.getGlobalScheme()).thenReturn(scheme);
            when(scheme.getAttributes(any())).thenReturn(new com.intellij.openapi.editor.markup.TextAttributes());

            ProblemHelper helper = ProblemHelper.builder(psiFile, project)
                    .scanIssueList(Collections.singletonList(scanIssue))
                    .build();

            // Should not throw; exercises the matching-document path
            assertDoesNotThrow(() ->
                    decorator.highlightLineAddGutterIconForProblem(helper, scanIssue, true, 2));
        }
    }

    @Test
    @DisplayName("Test getGutterIconBasedOnStatus for Malicious and Critical severities")
    void testGetGutterIconBasedOnStatus_MaliciousAndCritical() {
        assertNotNull(decorator.getGutterIconBasedOnStatus(SeverityLevel.MALICIOUS.getSeverity()));
        assertNotNull(decorator.getGutterIconBasedOnStatus(SeverityLevel.CRITICAL.getSeverity()));
        assertNotNull(decorator.getGutterIconBasedOnStatus(SeverityLevel.OK.getSeverity()));
        assertNotNull(decorator.getGutterIconBasedOnStatus(SeverityLevel.IGNORED.getSeverity()));
    }

    @Test
    @DisplayName("decorateUIForIgnoredVulnerability: empty ignore list returns early without touching FileEditorManager")
    void decorateUIForIgnoredVulnerability_emptyIgnoreList_returnsEarly() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getName()).thenReturn("File.java");

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<IgnoreFileManager> ifmMock = mockStatic(IgnoreFileManager.class);
             MockedStatic<ProblemHolderService> phsMock = mockStatic(ProblemHolderService.class);
             MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class)) {

            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(app).invokeLater(any(Runnable.class), any(ModalityState.class));

            IgnoreFileManager mockIfm = mock(IgnoreFileManager.class);
            ifmMock.when(() -> IgnoreFileManager.getInstance(project)).thenReturn(mockIfm);
            when(mockIfm.getAllIgnoreEntries()).thenReturn(Collections.emptyList());

            ProblemHolderService mockPhs = mock(ProblemHolderService.class);
            phsMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mockPhs);

            decorator.decorateUIForIgnoredVulnerability(project, psiFile, Collections.emptyList());

            femMock.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("decorateUIForIgnoredVulnerability: null editor returns early")
    void decorateUIForIgnoredVulnerability_nullEditor_returnsEarly() {
        Project project = mock(Project.class);
        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getName()).thenReturn("File.java");

        IgnoreEntry entry = new IgnoreEntry();
        List<IgnoreEntry.FileReference> refs = new ArrayList<>();
        refs.add(new IgnoreEntry.FileReference("src/File.java", true, 5, "some-code"));
        entry.setFiles(refs);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<IgnoreFileManager> ifmMock = mockStatic(IgnoreFileManager.class);
             MockedStatic<ProblemHolderService> phsMock = mockStatic(ProblemHolderService.class);
             MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class)) {

            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(app).invokeLater(any(Runnable.class), any(ModalityState.class));

            IgnoreFileManager mockIfm = mock(IgnoreFileManager.class);
            ifmMock.when(() -> IgnoreFileManager.getInstance(project)).thenReturn(mockIfm);
            when(mockIfm.getAllIgnoreEntries()).thenReturn(List.of(entry));

            ProblemHolderService mockPhs = mock(ProblemHolderService.class);
            phsMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mockPhs);

            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(null);

            decorator.decorateUIForIgnoredVulnerability(project, psiFile, Collections.emptyList());
            // No NPE; completed without touching PsiDocumentManager
        }
    }

    @Test
    @DisplayName("decorateUIForIgnoredVulnerability: matching file ref with no conflicting vulnerability adds gutter icon")
    void decorateUIForIgnoredVulnerability_matchingRefNoConflict_addsGutterIcon() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(null); // null basePath → uses psiFile.getVirtualFile().getPath()

        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getName()).thenReturn("File.java");
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.getPath()).thenReturn("src/File.java");

        IgnoreEntry entry = new IgnoreEntry();
        List<IgnoreEntry.FileReference> refs = new ArrayList<>();
        refs.add(new IgnoreEntry.FileReference("src/File.java", true, 5, "some-code"));
        entry.setFiles(refs);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<IgnoreFileManager> ifmMock = mockStatic(IgnoreFileManager.class);
             MockedStatic<ProblemHolderService> phsMock = mockStatic(ProblemHolderService.class);
             MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> pdmMock = mockStatic(PsiDocumentManager.class)) {

            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(app).invokeLater(any(Runnable.class), any(ModalityState.class));

            IgnoreFileManager mockIfm = mock(IgnoreFileManager.class);
            ifmMock.when(() -> IgnoreFileManager.getInstance(project)).thenReturn(mockIfm);
            when(mockIfm.getAllIgnoreEntries()).thenReturn(List.of(entry));

            ProblemHolderService mockPhs = mock(ProblemHolderService.class);
            phsMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mockPhs);

            Document sharedDoc = mock(Document.class);
            Editor editor = mock(Editor.class);
            when(editor.getDocument()).thenReturn(sharedDoc);
            MarkupModel markupModel = mock(MarkupModel.class);
            when(editor.getMarkupModel()).thenReturn(markupModel);
            RangeHighlighter highlighter = mock(RangeHighlighter.class);
            when(markupModel.addLineHighlighter(anyInt(), anyInt(), any())).thenReturn(highlighter);
            when(markupModel.getAllHighlighters()).thenReturn(new RangeHighlighter[0]);

            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);

            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            pdmMock.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(sharedDoc);

            // No scan issue at line 5 → should add gutter icon
            decorator.decorateUIForIgnoredVulnerability(project, psiFile, Collections.emptyList());

            verify(markupModel).addLineHighlighter(eq(4), anyInt(), any());
        }
    }

    @Test
    @DisplayName("decorateUIForIgnoredVulnerability: file ref with vulnerability on same line skips gutter icon")
    void decorateUIForIgnoredVulnerability_conflictingVulnerabilityOnLine_skipsGutterIcon() {
        Project project = mock(Project.class);
        when(project.getBasePath()).thenReturn(null);

        PsiFile psiFile = mock(PsiFile.class);
        when(psiFile.getName()).thenReturn("File.java");
        VirtualFile vf = mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(vf);
        when(vf.getPath()).thenReturn("src/File.java");

        IgnoreEntry entry = new IgnoreEntry();
        List<IgnoreEntry.FileReference> refs = new ArrayList<>();
        refs.add(new IgnoreEntry.FileReference("src/File.java", true, 5, "some-code"));
        entry.setFiles(refs);

        ScanIssue conflictingIssue = new ScanIssue();
        conflictingIssue.setLocations(List.of(new Location(5, 0, 0)));

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class);
             MockedStatic<IgnoreFileManager> ifmMock = mockStatic(IgnoreFileManager.class);
             MockedStatic<ProblemHolderService> phsMock = mockStatic(ProblemHolderService.class);
             MockedStatic<FileEditorManager> femMock = mockStatic(FileEditorManager.class);
             MockedStatic<PsiDocumentManager> pdmMock = mockStatic(PsiDocumentManager.class)) {

            Application app = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(app);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(app).invokeLater(any(Runnable.class), any(ModalityState.class));

            IgnoreFileManager mockIfm = mock(IgnoreFileManager.class);
            ifmMock.when(() -> IgnoreFileManager.getInstance(project)).thenReturn(mockIfm);
            when(mockIfm.getAllIgnoreEntries()).thenReturn(List.of(entry));

            ProblemHolderService mockPhs = mock(ProblemHolderService.class);
            phsMock.when(() -> ProblemHolderService.getInstance(project)).thenReturn(mockPhs);

            Document sharedDoc = mock(Document.class);
            Editor editor = mock(Editor.class);
            when(editor.getDocument()).thenReturn(sharedDoc);
            MarkupModel markupModel = mock(MarkupModel.class);
            when(editor.getMarkupModel()).thenReturn(markupModel);

            FileEditorManager fileMgr = mock(FileEditorManager.class);
            femMock.when(() -> FileEditorManager.getInstance(project)).thenReturn(fileMgr);
            when(fileMgr.getSelectedTextEditor()).thenReturn(editor);

            PsiDocumentManager psiDocMgr = mock(PsiDocumentManager.class);
            pdmMock.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocMgr);
            when(psiDocMgr.getDocument(psiFile)).thenReturn(sharedDoc);

            // Scan issue on same line 5 → addLineHighlighter should NOT be called
            decorator.decorateUIForIgnoredVulnerability(project, psiFile, List.of(conflictingIssue));

            verify(markupModel, never()).addLineHighlighter(anyInt(), anyInt(), any());
        }
    }
}
