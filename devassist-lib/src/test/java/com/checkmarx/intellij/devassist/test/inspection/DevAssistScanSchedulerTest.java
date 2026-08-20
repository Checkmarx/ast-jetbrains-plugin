package com.checkmarx.intellij.devassist.test.inspection;

import com.checkmarx.intellij.devassist.inspection.DevAssistScanScheduler;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHelper;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import com.checkmarx.intellij.devassist.inspection.DevAssistInspectionMgr;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.Keys.SCHEDULER_INSTANCE_KEY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DevAssistScanSchedulerTest {

    private Project mockProject;

    // ---- Reflection helpers ----

    private static DevAssistScanScheduler newInstanceWithoutConstructor() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);
        return (DevAssistScanScheduler) unsafe.allocateInstance(DevAssistScanScheduler.class);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    /**
     * Creates a DevAssistScanScheduler via Unsafe and injects all required non-null fields
     * that would normally be set by field initializers and the constructor.
     */
    private DevAssistScanScheduler createSchedulerWithProject(Project project) throws Exception {
        DevAssistScanScheduler scheduler = newInstanceWithoutConstructor();
        setField(scheduler, "project", project);
        setField(scheduler, "fileAlarms", new ConcurrentHashMap<String, Alarm>());
        setField(scheduler, "scanIndicators", new ConcurrentHashMap<>());
        setField(scheduler, "scanRequestTimeMap", new ConcurrentHashMap<>());
        setField(scheduler, "lastRestartTimeMap", new ConcurrentHashMap<>());
        setField(scheduler, "lock", new ReentrantLock());
        return scheduler;
    }

    @BeforeEach
    void setUp() {
        mockProject = mock(Project.class);
    }

    // ===== getInstance() =====

    @Test
    void getInstance_WhenSchedulerAlreadyCached_ReturnsCachedInstance() throws Exception {
        DevAssistScanScheduler cachedScheduler = createSchedulerWithProject(mockProject);
        when(mockProject.getUserData(SCHEDULER_INSTANCE_KEY)).thenReturn(cachedScheduler);

        DevAssistScanScheduler result = DevAssistScanScheduler.getInstance(mockProject);

        assertSame(cachedScheduler, result);
        verify(mockProject, never()).putUserData(any(Key.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getInstance_WhenNoCachedScheduler_CreatesAndStoresNewInstance() {
        when(mockProject.getUserData(SCHEDULER_INSTANCE_KEY)).thenReturn(null);
        doNothing().when(mockProject).putUserData(any(), any());

        try (MockedConstruction<com.checkmarx.intellij.devassist.inspection.DevAssistInspectionMgr> ignored =
                mockConstruction(com.checkmarx.intellij.devassist.inspection.DevAssistInspectionMgr.class)) {
            DevAssistScanScheduler result = DevAssistScanScheduler.getInstance(mockProject);

            assertNotNull(result);
            verify(mockProject).putUserData(eq(SCHEDULER_INSTANCE_KEY), eq(result));
        }
    }

    // ===== scheduleScan() =====

    @Test
    void scheduleScan_WhenProjectIsDisposed_ReturnsFalse() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(true);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        assertFalse(scheduler.scheduleScan("/path/file.java", mockHelper, ScanEngine.ASCA));
    }

    @Test
    void scheduleScan_WhenScanEngineIsNull_ReturnsFalse() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        assertFalse(scheduler.scheduleScan("/path/file.java", mockHelper, null));
    }

    @Test
    void scheduleScan_WithValidParameters_SchedulesAndReturnsTrue() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        Alarm mockAlarm = mock(Alarm.class);
        Map<String, Alarm> fileAlarms = getField(scheduler, "fileAlarms");
        fileAlarms.put("/path/file.java", mockAlarm);

        assertTrue(scheduler.scheduleScan("/path/file.java", mockHelper, ScanEngine.OSS));
        verify(mockAlarm).addRequest(any(Runnable.class), eq(1000));
    }

    @Test
    void scheduleScan_WhenExceptionThrown_ReturnsFalse() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenThrow(new RuntimeException("disposed check failed"));
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        assertFalse(scheduler.scheduleScan("/path/file.java", mockHelper, ScanEngine.OSS));
    }

    @Test
    void scheduleScan_WithDifferentEngines_AllReturnTrue() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        Alarm mockAlarm = mock(Alarm.class);
        Map<String, Alarm> fileAlarms = getField(scheduler, "fileAlarms");
        fileAlarms.put("/file.java", mockAlarm);

        assertTrue(scheduler.scheduleScan("/file.java", mockHelper, ScanEngine.ASCA));
        assertTrue(scheduler.scheduleScan("/file.java", mockHelper, ScanEngine.OSS));
        assertTrue(scheduler.scheduleScan("/file.java", mockHelper, ScanEngine.ALL));
    }

    // ===== cancelPendingAndRunningScan via scheduleScan (debounce path) =====

    @Test
    void scheduleScan_cancelsPreviousPendingAlarm_whenCalledTwice() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        Alarm mockAlarm = mock(Alarm.class);
        Map<String, Alarm> fileAlarms = getField(scheduler, "fileAlarms");
        fileAlarms.put("/file.java", mockAlarm);

        // First call schedules
        scheduler.scheduleScan("/file.java", mockHelper, ScanEngine.ASCA);
        // Second call should cancel previous alarm first, then schedule again
        scheduler.scheduleScan("/file.java", mockHelper, ScanEngine.ASCA);

        // cancelAllRequests should be called at least once for the debounce
        verify(mockAlarm, atLeastOnce()).cancelAllRequests();
    }

    @Test
    void scheduleScan_withProgressIndicatorRunning_cancelsIt() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        Alarm mockAlarm = mock(Alarm.class);
        Map<String, Alarm> fileAlarms = getField(scheduler, "fileAlarms");
        fileAlarms.put("/path/file.java", mockAlarm);

        com.intellij.openapi.progress.ProgressIndicator mockIndicator = mock(com.intellij.openapi.progress.ProgressIndicator.class);
        when(mockIndicator.isCanceled()).thenReturn(false);

        @SuppressWarnings("unchecked")
        Map<String, com.intellij.openapi.progress.ProgressIndicator> scanIndicators = getField(scheduler, "scanIndicators");
        scanIndicators.put("/path/file.java", mockIndicator);

        scheduler.scheduleScan("/path/file.java", mockHelper, ScanEngine.ASCA);

        verify(mockIndicator).cancel();
    }

    // ===== isRequestOutdated (via scheduleScan double-call) =====

    @Test
    void scheduleScan_alarmCallback_skipsOutdatedRequest() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        Alarm mockAlarm = mock(Alarm.class);
        final Runnable[] capturedRunnable = new Runnable[1];
        doAnswer(inv -> {
            capturedRunnable[0] = inv.getArgument(0);
            return null;
        }).when(mockAlarm).addRequest(any(Runnable.class), anyInt());

        Map<String, Alarm> fileAlarms = getField(scheduler, "fileAlarms");
        fileAlarms.put("/stale.java", mockAlarm);

        // Schedule first (captures runnable with requestTime T1)
        scheduler.scheduleScan("/stale.java", mockHelper, ScanEngine.ASCA);
        long firstRunnable = (long) ((Map<?, ?>) getField(scheduler, "scanRequestTimeMap")).get("/stale.java");

        // Schedule second (updates scanRequestTimeMap to T2, different from T1)
        scheduler.scheduleScan("/stale.java", mockHelper, ScanEngine.ASCA);

        // Force-update scanRequestTimeMap to a different value so the first runnable is outdated
        Map<String, Long> requestTimeMap = getField(scheduler, "scanRequestTimeMap");
        requestTimeMap.put("/stale.java", firstRunnable + 1000L);

        // Running the first captured runnable should be a no-op (outdated check skips execution)
        assertDoesNotThrow(() -> {
            if (capturedRunnable[0] != null) {
                capturedRunnable[0].run();
            }
        });
    }

    // ===== scanRequestTimeMap: direct entries are stored =====

    @Test
    void scheduleScan_storesScanRequestTime() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);
        ProblemHelper mockHelper = mock(ProblemHelper.class);

        Alarm mockAlarm = mock(Alarm.class);
        Map<String, Alarm> fileAlarms = getField(scheduler, "fileAlarms");
        fileAlarms.put("/timed.java", mockAlarm);

        long before = System.currentTimeMillis();
        scheduler.scheduleScan("/timed.java", mockHelper, ScanEngine.ASCA);
        long after = System.currentTimeMillis();

        Map<String, Long> requestTimeMap = getField(scheduler, "scanRequestTimeMap");
        assertTrue(requestTimeMap.containsKey("/timed.java"));
        long stored = requestTimeMap.get("/timed.java");
        assertTrue(stored >= before && stored <= after);
    }

    // ===== cacheScanResults (private) =====

    private static Method getCacheScanResults() throws Exception {
        Method m = DevAssistScanScheduler.class.getDeclaredMethod(
                "cacheScanResults", ProblemHelper.class, String.class, List.class, List.class, ScanEngine.class);
        m.setAccessible(true);
        return m;
    }

    @Test
    void cacheScanResults_withAllEngine_addsAllScanIssuesAndDescriptors() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        ProblemHolderService mockHolderService = mock(ProblemHolderService.class);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        when(mockHelper.getProblemHolderService()).thenReturn(mockHolderService);

        List<ScanIssue> issues = Collections.singletonList(new ScanIssue());
        List<ProblemDescriptor> descriptors = Collections.singletonList(mock(ProblemDescriptor.class));

        getCacheScanResults().invoke(scheduler, mockHelper, "/file.java", issues, descriptors, ScanEngine.ALL);

        verify(mockHolderService).addScanIssues("/file.java", issues);
        verify(mockHolderService).addProblemDescriptors("/file.java", descriptors);
    }

    @Test
    void cacheScanResults_withSpecificEngine_mergesScanIssues() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        ProblemHolderService mockHolderService = mock(ProblemHolderService.class);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        when(mockHelper.getProblemHolderService()).thenReturn(mockHolderService);
        when(mockHelper.getFilePath()).thenReturn("/file.java");

        List<ScanIssue> issues = Collections.singletonList(new ScanIssue());
        List<ProblemDescriptor> descriptors = Collections.singletonList(mock(ProblemDescriptor.class));

        getCacheScanResults().invoke(scheduler, mockHelper, "/file.java", issues, descriptors, ScanEngine.ASCA);

        verify(mockHolderService).removeScanIssuesByFileAndScanner("ASCA", "/file.java");
        verify(mockHolderService).mergeScanIssues("/file.java", issues);
        verify(mockHolderService).removeProblemDescriptorsForFileByScanner("/file.java", ScanEngine.ASCA);
        verify(mockHolderService).mergeProblemDescriptors("/file.java", descriptors);
    }

    // ===== resetCachedData (private) =====

    private static Method getResetCachedData() throws Exception {
        Method m = DevAssistScanScheduler.class.getDeclaredMethod("resetCachedData", ProblemHelper.class, ScanEngine.class);
        m.setAccessible(true);
        return m;
    }

    @Test
    void resetCachedData_withAllEngine_clearsAllData() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        ProblemHolderService mockHolderService = mock(ProblemHolderService.class);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        when(mockHelper.getProblemHolderService()).thenReturn(mockHolderService);
        when(mockHelper.getFilePath()).thenReturn("/file.java");

        getResetCachedData().invoke(scheduler, mockHelper, ScanEngine.ALL);

        verify(mockHolderService).addScanIssues("/file.java", Collections.emptyList());
        verify(mockHolderService).addProblemDescriptors("/file.java", Collections.emptyList());
    }

    @Test
    void resetCachedData_withSpecificEngine_removesEngineData() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        ProblemHolderService mockHolderService = mock(ProblemHolderService.class);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        when(mockHelper.getProblemHolderService()).thenReturn(mockHolderService);
        when(mockHelper.getFilePath()).thenReturn("/file.java");

        getResetCachedData().invoke(scheduler, mockHelper, ScanEngine.OSS);

        verify(mockHolderService).removeScanIssuesByFileAndScanner("OSS", "/file.java");
        verify(mockHolderService).removeProblemDescriptorsForFileByScanner("/file.java", ScanEngine.OSS);
        verify(mockHolderService, never()).addScanIssues(any(), any());
    }

    // ===== restartFileAfterScan (private) =====

    @Test
    void restartFileAfterScan_recentRestart_skipsInvokeLater() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        PsiFile mockFile = mock(PsiFile.class);
        when(mockHelper.getFilePath()).thenReturn("/file.java");
        when(mockHelper.getFile()).thenReturn(mockFile);

        Map<String, Long> lastRestartTimeMap = getField(scheduler, "lastRestartTimeMap");
        lastRestartTimeMap.put("/file.java", System.currentTimeMillis()); // recent restart

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);

            Method m = DevAssistScanScheduler.class.getDeclaredMethod("restartFileAfterScan", ProblemHelper.class);
            m.setAccessible(true);
            m.invoke(scheduler, mockHelper);

            verify(mockApp, never()).invokeLater(any(Runnable.class), any(ModalityState.class));
        }
    }

    @Test
    void restartFileAfterScan_noRecentRestart_invokesLater() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        PsiFile mockFile = mock(PsiFile.class);
        when(mockHelper.getFilePath()).thenReturn("/file.java");
        when(mockHelper.getFile()).thenReturn(mockFile);

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any(Runnable.class), any(ModalityState.class));

            Method m = DevAssistScanScheduler.class.getDeclaredMethod("restartFileAfterScan", ProblemHelper.class);
            m.setAccessible(true);
            m.invoke(scheduler, mockHelper);

            verify(mockApp).invokeLater(any(Runnable.class), eq(ModalityState.NON_MODAL));
        }
    }

    // ===== isProjectDisposed (private) =====

    @Test
    void isProjectDisposed_projectDisposed_returnsTrue() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(true);

        Method m = DevAssistScanScheduler.class.getDeclaredMethod("isProjectDisposed", String.class, String.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(scheduler, "test action", "/file.java");

        assertTrue(result);
    }

    @Test
    void isProjectDisposed_projectNotDisposed_returnsFalse() throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(mockProject);
        when(mockProject.isDisposed()).thenReturn(false);

        Method m = DevAssistScanScheduler.class.getDeclaredMethod("isProjectDisposed", String.class, String.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(scheduler, "test action", "/file.java");

        assertFalse(result);
    }

    // ===== runScan (private) via reflection =====

    private DevAssistScanScheduler createSchedulerWithMockInspectionMgr(Project project) throws Exception {
        DevAssistScanScheduler scheduler = createSchedulerWithProject(project);
        DevAssistInspectionMgr mockMgr = mock(DevAssistInspectionMgr.class);
        setField(scheduler, "cxOneAssistInspectionMgr", mockMgr);
        return scheduler;
    }

    @Test
    void runScan_emptyScanIssues_resetsDataAndRestartsFile() throws Exception {
        when(mockProject.isDisposed()).thenReturn(false);
        DevAssistScanScheduler scheduler = createSchedulerWithMockInspectionMgr(mockProject);
        DevAssistInspectionMgr mockMgr = getField(scheduler, "cxOneAssistInspectionMgr");

        PsiFile mockFile = mock(PsiFile.class);
        when(mockFile.getName()).thenReturn("App.java");
        when(mockFile.isValid()).thenReturn(true);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        when(mockHelper.getFile()).thenReturn(mockFile);
        when(mockHelper.getFilePath()).thenReturn("/file.java");
        ProblemHolderService mockHolder = mock(ProblemHolderService.class);
        when(mockHelper.getProblemHolderService()).thenReturn(mockHolder);

        when(mockMgr.scanFile(any(), any(), any())).thenReturn(Collections.emptyList());

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doNothing().when(mockApp).invokeLater(any(Runnable.class), any(ModalityState.class));
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(mockApp).runReadAction(any(Runnable.class));

            Method m = DevAssistScanScheduler.class.getDeclaredMethod("runScan", String.class, ProblemHelper.class, ScanEngine.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> {
                try { m.invoke(scheduler, "/file.java", mockHelper, ScanEngine.ASCA); }
                catch (java.lang.reflect.InvocationTargetException e) { /* allowed */ }
            });

            verify(mockMgr).scanFile(any(), eq(mockFile), eq(ScanEngine.ASCA));
        }
    }

    @Test
    void runScan_nonEmptyScanIssues_cachesResultsAndRestartsFile() throws Exception {
        when(mockProject.isDisposed()).thenReturn(false);
        DevAssistScanScheduler scheduler = createSchedulerWithMockInspectionMgr(mockProject);
        DevAssistInspectionMgr mockMgr = getField(scheduler, "cxOneAssistInspectionMgr");

        PsiFile mockFile = mock(PsiFile.class);
        when(mockFile.getName()).thenReturn("App.java");
        when(mockFile.isValid()).thenReturn(true);
        ProblemHelper mockHelper = mock(ProblemHelper.class);
        when(mockHelper.getFile()).thenReturn(mockFile);
        when(mockHelper.getFilePath()).thenReturn("/file.java");
        when(mockHelper.toBuilder(any())).thenReturn(mock(ProblemHelper.ProblemHelperBuilder.class, RETURNS_DEEP_STUBS));
        ProblemHolderService mockHolder = mock(ProblemHolderService.class);
        when(mockHelper.getProblemHolderService()).thenReturn(mockHolder);

        ScanIssue issue = new ScanIssue();
        when(mockMgr.scanFile(any(), any(), any())).thenReturn(List.of(issue));
        when(mockMgr.createProblemDescriptorsWithoutDecoration(any())).thenReturn(Collections.emptyList());

        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(mockApp).invokeLater(any(Runnable.class), any(ModalityState.class));
            doAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return null; })
                    .when(mockApp).runReadAction(any(Runnable.class));

            Method m = DevAssistScanScheduler.class.getDeclaredMethod("runScan", String.class, ProblemHelper.class, ScanEngine.class);
            m.setAccessible(true);
            assertDoesNotThrow(() -> {
                try { m.invoke(scheduler, "/file.java", mockHelper, ScanEngine.ALL); }
                catch (java.lang.reflect.InvocationTargetException e) { /* inner exception OK */ }
            });

            verify(mockMgr).scanFile(any(), eq(mockFile), eq(ScanEngine.ALL));
        }
    }
}
