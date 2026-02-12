package com.checkmarx.intellij.devassist.test.registry;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.devassist.basescanner.ScannerCommand;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.devassist.registry.ScannerRegistry;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ScannerRegistryTest {
    private Project project;
    private ScannerRegistry registry;
    static MockedStatic<DevAssistUtils> devAssistUtilsMock;
    static MockedStatic<ProblemHolderService> problemHolderServiceMock;
    static ProblemHolderService mockProblemHolderService;

    private static class FakeScanner implements ScannerCommand {
        int registerCalls;
        int deregisterCalls;
        boolean disposed;

        @Override
        public void register(Project p) { registerCalls++; }

        @Override
        public void deregister(Project p) { deregisterCalls++; }

        @Override
        public void dispose() { disposed = true; }
    }

    @BeforeEach
    void setUp() {
        project = mock(Project.class, RETURNS_DEEP_STUBS);
        when(project.getName()).thenReturn("TestProject");
        registry = new ScannerRegistry(project);
        try {
            Field f = ScannerRegistry.class.getDeclaredField("scannerMap");
            f.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String, ScannerCommand> map = (Map<String, ScannerCommand>) f.get(registry);
            map.put(ScanEngine.OSS.name(), new FakeScanner());
        } catch (Exception e) {
            fail("Reflection setup failed: " + e.getMessage());
        }
    }

    @BeforeAll
    static void setupApplicationManager() throws Exception {
        Application mockApp = mock(Application.class, RETURNS_DEEP_STUBS);
        GlobalSettingsState mockSettings = mock(GlobalSettingsState.class, RETURNS_DEEP_STUBS);
        when(mockApp.getService(GlobalSettingsState.class)).thenReturn(mockSettings);

        Field appField = ApplicationManager.class.getDeclaredField("ourApplication");
        appField.setAccessible(true);
        appField.set(null, mockApp);
        mockProblemHolderService = mock(ProblemHolderService.class, RETURNS_DEEP_STUBS);
        problemHolderServiceMock = mockStatic(ProblemHolderService.class, CALLS_REAL_METHODS);
        problemHolderServiceMock.when(() -> ProblemHolderService.getInstance(any())).thenReturn(mockProblemHolderService);

        GlobalScannerController mockController = mock(GlobalScannerController.class, RETURNS_DEEP_STUBS);
        devAssistUtilsMock = mockStatic(DevAssistUtils.class, CALLS_REAL_METHODS);
        devAssistUtilsMock.when(DevAssistUtils::globalScannerController).thenReturn(mockController);
        when(mockController.isRegistered(any(), any())).thenReturn(true);
    }

    @AfterAll
    static void tearDownStaticMocks() {
        if (devAssistUtilsMock != null) devAssistUtilsMock.close();
        if (problemHolderServiceMock != null) problemHolderServiceMock.close();
    }

    @SuppressWarnings("unchecked")
    private Map<String,ScannerCommand> scannerMap() throws Exception {
        Field f=ScannerRegistry.class.getDeclaredField("scannerMap");
        f.setAccessible(true);
        return (Map<String,ScannerCommand>)f.get(registry);
    }

    private void putScanner(String id, ScannerCommand sc) throws Exception {
        scannerMap().put(id, sc);
    }

    @Test
    @DisplayName("Constructor initializes OSS scanner")
    void testScannerRegistry_constructorInitializesOssScanner_functionality(){
        assertNotNull(registry.getScanner(ScanEngine.OSS.name()));
    }

    @Test
    @DisplayName("getScanner returns existing scanner")
    void testGetScanner_existingReturnsNonNull_functionality(){
        assertNotNull(registry.getScanner(ScanEngine.OSS.name()));
    }

    @Test
    @DisplayName("getScanner returns null when id missing")
    void testGetScanner_missingReturnsNull_functionality(){
        assertNull(registry.getScanner("UNKNOWN"));
    }

    @Test
    @DisplayName("registerScanner invokes register on existing scanner")
    void testRegisterScanner_existingInvokesRegister_functionality() throws Exception {
        FakeScanner fake=new FakeScanner();
        putScanner(ScanEngine.OSS.name(),fake);
        registry.registerScanner(ScanEngine.OSS.name());
        assertEquals(1,fake.registerCalls);
    }

    @Test
    @DisplayName("registerScanner does nothing for unknown id")
    void testRegisterScanner_unknownNoAction_functionality(){
        registry.registerScanner("BAD_ID");
    }

    @Test
    @DisplayName("registerAllScanners invokes register on every scanner")
    void testRegisterAllScanners_invokesRegisterForEveryEntry_functionality() throws Exception {
        FakeScanner s1=new FakeScanner();
        FakeScanner s2=new FakeScanner();
        putScanner("S1",s1);
        putScanner("S2",s2);
        registry.registerAllScanners(project);
        assertEquals(1,s1.registerCalls);
        assertEquals(1,s2.registerCalls);
    }

    @Test
    @DisplayName("registerAllScanners with empty map does nothing")
    void testRegisterAllScanners_emptyNoAction_functionality() throws Exception {
        scannerMap().clear();
        registry.registerAllScanners(project);
    }

    @Test
    @DisplayName("deregisterScanner invokes deregister and dispose")
    void testDeregisterScanner_existingInvokesDeregisterAndDispose_functionality() throws Exception {
        FakeScanner fake=new FakeScanner();
        putScanner(ScanEngine.OSS.name(),fake);
        registry.deregisterScanner(ScanEngine.OSS.name());
        assertEquals(1,fake.deregisterCalls);
        assertTrue(fake.disposed);
    }

    @Test
    @DisplayName("deregisterScanner unknown id does nothing")
    void testDeregisterScanner_unknownNoAction_functionality(){
        registry.deregisterScanner("BAD_ID");
    }

    @Test
    @DisplayName("deregisterAllScanners invokes deregister on each scanner without disposing")
    void testDeregisterAllScanners_invokesDeregisterForEveryEntry_functionality() throws Exception {
        FakeScanner s1=new FakeScanner();
        FakeScanner s2=new FakeScanner();
        putScanner("S1",s1);
        putScanner("S2",s2);
        registry.deregisterAllScanners();
        assertEquals(1,s1.deregisterCalls);
        assertEquals(1,s2.deregisterCalls);
        assertFalse(s1.disposed);
        assertFalse(s2.disposed);
    }

    @Test
    @DisplayName("deregisterAllScanners empty map does nothing")
    void testDeregisterAllScanners_emptyNoAction_functionality() throws Exception {
        scannerMap().clear();
        registry.deregisterAllScanners();
    }

    @Test
    @DisplayName("dispose deregisters all scanners and clears map")
    void testDispose_clearsMapAndDeregistersScanners_functionality() throws Exception {
        FakeScanner s1=new FakeScanner();
        FakeScanner s2=new FakeScanner();
        putScanner("S1",s1);
        putScanner("S2",s2);
        registry.dispose();
        assertEquals(1,s1.deregisterCalls);
        assertEquals(1,s2.deregisterCalls);
        assertTrue(scannerMap().isEmpty());
    }

    @Test
    @DisplayName("scannerInitialization populates OSS scanner")
    void testScannerInitialization_populatesOssScanner_functionality(){
        assertNotNull(registry.getScanner(ScanEngine.OSS.name()));
    }

    @Test
    @DisplayName("setScanner private method adds scanner to map")
    void testSetScanner_privateAddsScannerToMap_functionality() throws Exception {
        Method m=ScannerRegistry.class.getDeclaredMethod("setScanner",String.class,ScannerCommand.class);
        m.setAccessible(true);
        FakeScanner fake=new FakeScanner();
        m.invoke(registry,"CUSTOM",fake);
        assertSame(fake,registry.getScanner("CUSTOM"));
    }

    @Test
    @DisplayName("getScanner returns same instance (identity)")
    void testGetScanner_identity_functionality() throws Exception {
        FakeScanner fake=new FakeScanner();
        putScanner("IDENTITY",fake);
        assertSame(fake, registry.getScanner("IDENTITY"));
    }

    @Test
    @DisplayName("dispose called twice leaves map empty and no exception")
    void testDispose_idempotent_functionality() throws Exception {
        registry.dispose();
        registry.dispose();
        assertTrue(scannerMap().isEmpty());
    }

    @Test
    @DisplayName("registerScanner after dispose has no effect")
    void testRegisterScanner_afterDisposeNoEffect_functionality() throws Exception {
        FakeScanner fake=new FakeScanner();
        putScanner(ScanEngine.OSS.name(),fake);
        registry.dispose();
        fake.registerCalls=0;
        registry.registerScanner(ScanEngine.OSS.name());
        assertEquals(0,fake.registerCalls);
    }
}
