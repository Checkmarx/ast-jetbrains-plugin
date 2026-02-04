package com.checkmarx.intellij.unit.devassist.inspection;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.basescanner.ScannerService;
import com.checkmarx.intellij.devassist.inspection.CxOneAssistInspection;
import com.checkmarx.intellij.devassist.inspection.CxOneAssistInspectionMgr;
import com.checkmarx.intellij.devassist.inspection.CxOneAssistScanScheduler;
import com.checkmarx.intellij.devassist.inspection.CxOneAssistScanStateHolder;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CxOneAssistInspectionTest {

    private CxOneAssistInspection inspection;
    private InspectionManager inspectionManager;
    private Project project;
    private PsiFile psiFile;
    private VirtualFile virtualFile;
    private Document document;
    private ProblemHolderService holderService;
    private CxOneAssistInspectionMgr inspectionMgr;
    private CxOneAssistScanStateHolder stateHolder;
    private PsiDocumentManager psiDocumentManager;
    private ScannerService<?> scannerService;
    private ProblemHolderService problemHolderService;

    @BeforeEach
    void setUp() throws Exception {
        inspection = new CxOneAssistInspection();
        inspectionManager = mock(InspectionManager.class);
        project = mock(Project.class);
        psiFile = mock(PsiFile.class);
        virtualFile = mock(VirtualFile.class);
        document = mock(Document.class);
        holderService = mock(ProblemHolderService.class);
        inspectionMgr = mock(CxOneAssistInspectionMgr.class);
        stateHolder = mock(CxOneAssistScanStateHolder.class);
        psiDocumentManager = mock(PsiDocumentManager.class);
        scannerService = mock(ScannerService.class);
        problemHolderService = mock(ProblemHolderService.class);

        injectField(inspection, "cxOneAssistInspectionMgr", inspectionMgr);

        when(inspectionManager.getProject()).thenReturn(project);
        when(psiFile.getProject()).thenReturn(project);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        when(virtualFile.getPath()).thenReturn("/repo/file.tf");
    }

    @Test
    @DisplayName("Returns empty array when virtual file is missing and resets editor state")
    void checkFileReturnsEmptyWhenVirtualFileMissing() throws Exception {
        when(psiFile.getVirtualFile()).thenReturn(null);
        try (MockedStatic<ProblemHolderService> holder = mockStatic(ProblemHolderService.class);
             MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class);
             MockedStatic<Utils> auth = mockStatic(Utils.class)) {
            ProblemDescriptor[] descriptors = inspection.checkFile(psiFile, inspectionManager, true);
            assertEquals(0, descriptors.length);
            verify(inspectionMgr).resetEditorAndResults(project, null);
        }
    }

    @Test
    @DisplayName("Returns cached descriptors when timestamps match")
    void checkFileReturnsExistingDescriptorsWhenCached() throws Exception {
        when(psiFile.getName()).thenReturn("file.tf");
        when(virtualFile.getTimeStamp()).thenReturn(5L);
        when(psiFile.getModificationStamp()).thenReturn(10L);
        when(document.getModificationStamp()).thenReturn(7L);
        ProblemDescriptor existing = mock(ProblemDescriptor.class);

        when(inspectionMgr.getSupportedScanner(anyString(), eq(psiFile))).thenReturn(singletonList(scannerService));
        when(inspectionMgr.getExistingProblems(eq(holderService), anyString(), eq(document), eq(psiFile),
                eq(singletonList(scannerService)), eq(inspectionManager)))
                .thenReturn(new ProblemDescriptor[]{existing});

        try (MockedStatic<Utils> auth = mockStatic(Utils.class);
             MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class);
             MockedStatic<PsiDocumentManager> psiMgr = mockStatic(PsiDocumentManager.class);
             MockedStatic<ProblemHolderService> holder = mockStatic(ProblemHolderService.class);
             MockedStatic<CxOneAssistScanStateHolder> state = mockStatic(CxOneAssistScanStateHolder.class)) {
            auth.when(Utils::isUserAuthenticated).thenReturn(true);
            devUtils.when(DevAssistUtils::isAnyScannerEnabled).thenReturn(true);
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(false);
            psiMgr.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocumentManager);
            when(psiDocumentManager.getDocument(psiFile)).thenReturn(document);
            holder.when(() -> ProblemHolderService.getInstance(project)).thenReturn(holderService);
            state.when(() -> CxOneAssistScanStateHolder.getInstance(project)).thenReturn(stateHolder);

            long composite = 5L ^ 10L ^ 7L;
            when(stateHolder.getTimeStamp("/repo/file.tf")).thenReturn(composite);

            ProblemDescriptor[] descriptors = inspection.checkFile(psiFile, inspectionManager, true);
            assertArrayEquals(new ProblemDescriptor[]{existing}, descriptors);
        }
    }

    @Test
    @DisplayName("Schedules scan and returns cached descriptors when scheduler accepts")
    void checkFileSchedulesScan() throws Exception {
        when(psiFile.getName()).thenReturn("file.tf");
        when(virtualFile.getTimeStamp()).thenReturn(5L);
        when(psiFile.getModificationStamp()).thenReturn(10L);
        when(document.getModificationStamp()).thenReturn(7L);
        when(inspectionMgr.getSupportedScanner(anyString(), eq(psiFile))).thenReturn(List.of(scannerService));

        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setScanEngine(ScanEngine.IAC);

        try (MockedStatic<Utils> auth = mockStatic(Utils.class);
             MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class);
             MockedStatic<PsiDocumentManager> psiMgr = mockStatic(PsiDocumentManager.class);
             MockedStatic<ProblemHolderService> holder = mockStatic(ProblemHolderService.class);
             MockedStatic<CxOneAssistScanStateHolder> state = mockStatic(CxOneAssistScanStateHolder.class)) {

            auth.when(Utils::isUserAuthenticated).thenReturn(true);
            devUtils.when(DevAssistUtils::isAnyScannerEnabled).thenReturn(true);
            devUtils.when(DevAssistUtils::isDarkTheme).thenReturn(false);
            psiMgr.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocumentManager);
            when(psiDocumentManager.getDocument(psiFile)).thenReturn(document);
            holder.when(() -> ProblemHolderService.getInstance(project)).thenReturn(holderService);
            when(holderService.getScanIssueByFile("/repo/file.tf")).thenReturn(List.of(scanIssue));
        ProblemDescriptor descriptor = mock(ProblemDescriptor.class);
            when(holderService.getProblemDescriptors("/repo/file.tf")).thenReturn(List.of(descriptor));

            state.when(() -> CxOneAssistScanStateHolder.getInstance(project)).thenReturn(stateHolder);
            when(stateHolder.getTimeStamp("/repo/file.tf")).thenReturn(null);

            setupSchedulerMock(true);
            ProblemDescriptor[] existing = new ProblemDescriptor[]{descriptor};
            when(holderService.getProblemDescriptors("/repo/file.tf")).thenReturn(List.of(descriptor));
            when(problemHolderService.getProblemDescriptors("/repo/file.tf")).thenReturn(List.of(descriptor));

            // Mock decorateUI to avoid exceptions
            doNothing().when(inspectionMgr).decorateUI(eq(document), eq(psiFile), anyList());

            ProblemDescriptor[] descriptors = inspection.checkFile(psiFile, inspectionManager, true);
            assertEquals(1, descriptors.length);
        }
    }

    @Test
    @DisplayName("Falls back to synchronous scan when scheduler declines")
    void checkFileFallsBackWhenSchedulerDeclines() throws Exception {
        when(psiFile.getName()).thenReturn("file.tf");
        when(virtualFile.getTimeStamp()).thenReturn(5L);
        when(psiFile.getModificationStamp()).thenReturn(10L);
        when(document.getModificationStamp()).thenReturn(7L);
        when(inspectionMgr.getSupportedScanner(anyString(), eq(psiFile))).thenReturn(List.of(scannerService));
        ProblemDescriptor fallback = mock(ProblemDescriptor.class);
        when(inspectionMgr.startScanAndCreateProblemDescriptors(any())).thenReturn(new ProblemDescriptor[]{fallback});

        try (MockedStatic<Utils> auth = mockStatic(Utils.class);
             MockedStatic<DevAssistUtils> devUtils = mockStatic(DevAssistUtils.class);
             MockedStatic<PsiDocumentManager> psiMgr = mockStatic(PsiDocumentManager.class);
             MockedStatic<ProblemHolderService> holder = mockStatic(ProblemHolderService.class);
             MockedStatic<CxOneAssistScanStateHolder> state = mockStatic(CxOneAssistScanStateHolder.class)) {

            auth.when(Utils::isUserAuthenticated).thenReturn(true);
            devUtils.when(DevAssistUtils::isAnyScannerEnabled).thenReturn(true);
            psiMgr.when(() -> PsiDocumentManager.getInstance(project)).thenReturn(psiDocumentManager);
            when(psiDocumentManager.getDocument(psiFile)).thenReturn(document);
            holder.when(() -> ProblemHolderService.getInstance(project)).thenReturn(holderService);
            state.when(() -> CxOneAssistScanStateHolder.getInstance(project)).thenReturn(stateHolder);
            when(stateHolder.getTimeStamp("/repo/file.tf")).thenReturn(null);

            setupSchedulerMock(false);

            ProblemDescriptor[] descriptors = inspection.checkFile(psiFile, inspectionManager, true);
            assertArrayEquals(new ProblemDescriptor[]{fallback}, descriptors);
            verify(inspectionMgr).startScanAndCreateProblemDescriptors(any());
        }
    }

    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
    }

    private void setupSchedulerMock(boolean scheduleResult) {
        CxOneAssistScanScheduler scheduler = mock(CxOneAssistScanScheduler.class, invocation -> {
            if ("scheduleScan".equals(invocation.getMethod().getName())) {
                return scheduleResult;
            }
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (returnType.equals(boolean.class)) return false;
            if (returnType.equals(int.class)) return 0;
            if (returnType.equals(long.class)) return 0L;
            return null;
        });
        when(project.getUserData(DevAssistConstants.Keys.SCHEDULER_INSTANCE_KEY)).thenReturn(scheduler);
    }
}
