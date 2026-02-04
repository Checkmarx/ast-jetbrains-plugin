package com.checkmarx.intellij.ast.test.unit.devassist.scanners.iac;

import com.checkmarx.intellij.devassist.scanners.iac.IacScannerCommand;
import com.checkmarx.intellij.devassist.scanners.iac.IacScannerService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

public class IacScannerCommandTest {

  private static class TestIacScannerCommand extends IacScannerCommand {

      TestIacScannerCommand(Disposable disposable, Project project){
          super(disposable,project);
      }

      @Override
      public void initializeScanner() {
          super.initializeScanner();
      }
  }

    private Project project;
    private Disposable parentDisposable;
    private IacScannerService iacScannerServiceSpy;
    private TestIacScannerCommand command;

    @BeforeEach
    void setUp() throws Exception {
       project= mock(Project.class, RETURNS_DEEP_STUBS);
       parentDisposable= mock(Disposable.class);
       command= new TestIacScannerCommand(parentDisposable,project);

        Field scannerField= IacScannerCommand.class.getDeclaredField("iacScannerService");
        scannerField.setAccessible(true);
        IacScannerService originalService= (IacScannerService) scannerField.get(command);
        iacScannerServiceSpy= spy(originalService);
        scannerField.set(command, iacScannerServiceSpy);
    }

    @Test
    @DisplayName("Constructor initializes internal service and project reference\"")
    void testConstructor() throws Exception{

        Field seviceField= IacScannerCommand.class.getDeclaredField("iacScannerService");
        seviceField.setAccessible(true);
        assertNotNull(seviceField.get(command));

        Field projectField= IacScannerCommand.class.getDeclaredField("project");
        projectField.setAccessible(true);
        assertSame(project, projectField.get(command));
    }

    @Test
    @DisplayName("Initialize scanner completes successfully and logs initialization")
    void testInitializeScanner() {
        assertDoesNotThrow(() -> command.initializeScanner());
    }

    @Test
    @DisplayName("Service field is properly initialized and accessible")
    void testServiceFieldInitialization() throws Exception {
        Field serviceField = IacScannerCommand.class.getDeclaredField("iacScannerService");
        serviceField.setAccessible(true);
        IacScannerService service = (IacScannerService) serviceField.get(command);
        assertNotNull(service, "Service should be initialized");
        assertNotNull(service.getConfig(), "Service config should be available");
    }

    @Test
    @DisplayName("iacService should scan file")
    void testIacServiceShouldScanFile() throws Exception {

        Field IacServiceField = IacScannerCommand.class.getDeclaredField("iacScannerService");
        IacServiceField.setAccessible(true);
        IacScannerService iacScannerService= (IacScannerService) IacServiceField.get(command);
        PsiFile psiFile= mock(PsiFile.class);
        VirtualFile virtualFile= mock(VirtualFile.class);
        when(psiFile.getVirtualFile()).thenReturn(virtualFile);
        when(virtualFile.exists()).thenReturn(true);
        when(virtualFile.getExtension()).thenReturn("tf");
        assertTrue(() -> iacScannerService.shouldScanFile("src/test/resources/data/Dockerfile", psiFile));
        assertTrue(() -> iacScannerService.shouldScanFile("src/test/resources/data/main.tf", psiFile));
        assertTrue(() -> iacScannerService.shouldScanFile("src/test/resources/data/negative.yaml", psiFile));
    }


    @Test
    @DisplayName("IACScannerService should scan file")
    void testIacServiceShouldNotScanFile() throws Exception {
        Field IacServiceField = IacScannerCommand.class.getDeclaredField("iacScannerService");
        IacServiceField.setAccessible(true);
        IacScannerService iacScannerService= (IacScannerService) IacServiceField.get(command);
        PsiFile psiFile= mock(PsiFile.class);
        assertFalse(() -> iacScannerService.shouldScanFile("/node_modules/", psiFile));
    }

    @Test
    @DisplayName("IAC Command configuration should be properly set up")
    void testCommandConfiguration() throws Exception {
        Field serviceField = IacScannerCommand.class.getDeclaredField("iacScannerService");
        serviceField.setAccessible(true);
        IacScannerService service = (IacScannerService) serviceField.get(command);
        assertNotNull(service.getConfig(), "Configuration should be available");
        assertEquals("IAC", service.getConfig().getEngineName(), "Engine name should be IAC");
    }

    @Test
    @DisplayName("Multiple IAC command instances are independent")
    void testMultipleCommandInstances() throws Exception {
        IacScannerCommand command2 = new IacScannerCommand(parentDisposable, project);

        Field serviceField = IacScannerCommand.class.getDeclaredField("iacScannerService");
        serviceField.setAccessible(true);

        IacScannerService service1 = (IacScannerService) serviceField.get(command);
        IacScannerService service2 = (IacScannerService) serviceField.get(command2);

        assertNotNull(service1);
        assertNotNull(service2);
        assertNotSame(service1, service2, "Each command should have its own service instance");
    }

    @Test
    @DisplayName("Dispose method handles cleanup gracefully")
    void testDispose() {
        assertDoesNotThrow(() -> command.dispose());
    }


}
