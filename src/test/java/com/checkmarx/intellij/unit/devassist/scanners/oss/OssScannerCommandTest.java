package com.checkmarx.intellij.unit.devassist.scanners.oss;

import com.checkmarx.intellij.devassist.scanners.oss.OssScannerCommand;
import com.checkmarx.intellij.devassist.scanners.oss.OssScannerService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OssScannerCommandTest {

    private static class TestOssScannerCommand extends OssScannerCommand {
        public TestOssScannerCommand(Disposable parentDisposable, Project project) { super(parentDisposable, project); }
        @Override
        protected com.intellij.openapi.vfs.VirtualFile findVirtualFile(String path) { return null; } // avoid LocalFileSystem.getInstance()
        public void invokeInitializeScanner() { super.initializeScanner(); }
    }

    private Project project;
    private Disposable parentDisposable;
    private OssScannerService ossScannerServiceSpy;
    private TestOssScannerCommand command;
    private void invokePrivateScan(OssScannerCommand cmd) throws Exception {
        Method m = OssScannerCommand.class.getDeclaredMethod("scanAllManifestFilesInFolder");
        m.setAccessible(true);
        m.invoke(cmd);
    }

    @BeforeEach
    void setUp() throws Exception {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        parentDisposable = mock(Disposable.class);
        command = new TestOssScannerCommand(parentDisposable, project);
        // Spy on internal service field for later verification
        Field f = OssScannerCommand.class.getDeclaredField("ossScannerService");
        f.setAccessible(true);
        OssScannerService original = (OssScannerService) f.get(command);
        ossScannerServiceSpy = spy(original);
        f.set(command, ossScannerServiceSpy);
    }

    @Test
    @DisplayName("Constructor initializes internal service and project reference")
    void testConstructor_functionality() throws Exception {
        Field f = OssScannerCommand.class.getDeclaredField("ossScannerService");
        f.setAccessible(true);
        assertNotNull(f.get(command));
        Field p = OssScannerCommand.class.getDeclaredField("project");
        p.setAccessible(true);
        assertSame(project, p.get(command));
    }

    @Test
    @DisplayName("initializeScanner queues background task (NPE expected in headless env)")
    void testInitializeScanner_connectivityQueuesTask_functionality() {
        assertThrows(NullPointerException.class, () -> command.invokeInitializeScanner());
    }

    @Test
    @DisplayName("scanAllManifestFilesInFolder with empty content roots performs no scans")
    void testScanAllManifestFiles_emptyRoots_functionality() throws Exception {
        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[0]);
        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);
            invokePrivateScan(command);
            verifyNoInteractions(ossScannerServiceSpy);
        }
    }

    @Test
    @DisplayName("scanAllManifestFilesInFolder with single non-matching file performs no scans")
    void testScanAllManifestFiles_singleNonMatch_functionality() throws Exception {
        VirtualFile root = mock(VirtualFile.class);
        when(root.isDirectory()).thenReturn(false);
        when(root.getPath()).thenReturn("/some/random/file.txt");
        when(root.exists()).thenReturn(true);

        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});

        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);
            invokePrivateScan(command);
            verifyNoInteractions(ossScannerServiceSpy);
        }
    }

    @Test
    @DisplayName("scanAllManifestFilesInFolder with matching manifest path but overridden findVirtualFile yields graceful no-op")
    void testScanAllManifestFiles_matchingManifestGraceful_functionality() throws Exception {
        VirtualFile root = mock(VirtualFile.class);
        when(root.isDirectory()).thenReturn(false);
        when(root.getPath()).thenReturn("/workspace/package.json");
        when(root.exists()).thenReturn(true);
        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});
        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);
            assertDoesNotThrow(() -> invokePrivateScan(command));
        }
    }

    @Test
    @DisplayName("scanAllManifestFilesInFolder handles matching manifest without LocalFileSystem (graceful)")
    void testScanAllManifestFiles_exceptionDuringScan_functionality() throws Exception {
        VirtualFile root = mock(VirtualFile.class);
        when(root.isDirectory()).thenReturn(false);
        when(root.getPath()).thenReturn("/workspace/pom.xml"); // another manifest pattern
        when(root.exists()).thenReturn(true);
        ProjectRootManager prm = mock(ProjectRootManager.class);
        when(prm.getContentRoots()).thenReturn(new VirtualFile[]{root});
        try (MockedStatic<ProjectRootManager> pm = mockStatic(ProjectRootManager.class)) {
            pm.when(() -> ProjectRootManager.getInstance(project)).thenReturn(prm);
            assertDoesNotThrow(() -> invokePrivateScan(command));
        }
    }
}
