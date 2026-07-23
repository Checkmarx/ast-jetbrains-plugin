package com.checkmarx.intellij.devassist.test.configuration;

import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.configuration.ScannerLifeCycleManager;
import com.checkmarx.intellij.devassist.inspection.DevAssistInspectionMgr;
import com.checkmarx.intellij.devassist.registry.ScannerRegistry;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScannerLifeCycleManagerTest {

    @Mock
    private Project mockProject;

    @Mock
    private ScannerRegistry mockRegistry;

    @Mock
    private GlobalScannerController mockController;

    private ScannerLifeCycleManager manager;

    @BeforeEach
    void setUp() {
        when(mockProject.getService(ScannerRegistry.class)).thenReturn(mockRegistry);
        manager = new ScannerLifeCycleManager(mockProject);
    }

    @Test
    void getProject_returnsInjectedProject() {
        assertSame(mockProject, manager.getProject());
    }

    @Test
    void start_registersScanner() {
        try (MockedConstruction<DevAssistInspectionMgr> ignored =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.start(ScanEngine.ASCA);
            verify(mockRegistry).registerScanner(ScanEngine.ASCA.name());
        }
    }

    @Test
    void stop_deregistersScanner() {
        try (MockedConstruction<DevAssistInspectionMgr> ignored =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.stop(ScanEngine.ASCA);
            verify(mockRegistry).deregisterScanner(ScanEngine.ASCA.name());
        }
    }

    @Test
    void stopAll_deregistersAllScanEngines() {
        try (MockedConstruction<DevAssistInspectionMgr> ignored =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.stopAll();
            for (ScanEngine engine : ScanEngine.values()) {
                verify(mockRegistry).deregisterScanner(engine.name());
            }
        }
    }

    @Test
    void stopAll_triggersInspection() {
        try (MockedConstruction<DevAssistInspectionMgr> constr =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.stopAll();
            assertFalse(constr.constructed().isEmpty());
            verify(constr.constructed().get(0)).triggerInspection(mockProject);
        }
    }

    @Test
    void dispose_deregistersAllScanEngines() {
        try (MockedConstruction<DevAssistInspectionMgr> ignored =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.dispose();
            for (ScanEngine engine : ScanEngine.values()) {
                verify(mockRegistry).deregisterScanner(engine.name());
            }
        }
    }

    @Test
    void updateFromGlobal_allEnabled_registersAll() {
        when(mockController.isScannerGloballyEnabled(any(ScanEngine.class))).thenReturn(true);
        try (MockedConstruction<DevAssistInspectionMgr> ignored =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.updateFromGlobal(mockController);
            for (ScanEngine engine : ScanEngine.values()) {
                verify(mockRegistry).registerScanner(engine.name());
            }
            verify(mockRegistry, never()).deregisterScanner(anyString());
        }
    }

    @Test
    void updateFromGlobal_allDisabled_deregistersAll() {
        when(mockController.isScannerGloballyEnabled(any(ScanEngine.class))).thenReturn(false);
        try (MockedConstruction<DevAssistInspectionMgr> ignored =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.updateFromGlobal(mockController);
            for (ScanEngine engine : ScanEngine.values()) {
                verify(mockRegistry).deregisterScanner(engine.name());
            }
            verify(mockRegistry, never()).registerScanner(anyString());
        }
    }

    @Test
    void updateFromGlobal_triggersInspection() {
        when(mockController.isScannerGloballyEnabled(any(ScanEngine.class))).thenReturn(false);
        try (MockedConstruction<DevAssistInspectionMgr> constr =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.updateFromGlobal(mockController);
            assertFalse(constr.constructed().isEmpty());
            verify(constr.constructed().get(0)).triggerInspection(mockProject);
        }
    }

    @Test
    void updateFromGlobal_someEnabled_registersEnabledDeregistersDisabled() {
        when(mockController.isScannerGloballyEnabled(ScanEngine.ASCA)).thenReturn(true);
        when(mockController.isScannerGloballyEnabled(ScanEngine.OSS)).thenReturn(false);
        when(mockController.isScannerGloballyEnabled(ScanEngine.SECRETS)).thenReturn(false);
        when(mockController.isScannerGloballyEnabled(ScanEngine.CONTAINERS)).thenReturn(false);
        when(mockController.isScannerGloballyEnabled(ScanEngine.IAC)).thenReturn(false);
        try (MockedConstruction<DevAssistInspectionMgr> ignored =
                     mockConstruction(DevAssistInspectionMgr.class)) {
            manager.updateFromGlobal(mockController);
            verify(mockRegistry).registerScanner(ScanEngine.ASCA.name());
            verify(mockRegistry).deregisterScanner(ScanEngine.OSS.name());
            verify(mockRegistry).deregisterScanner(ScanEngine.SECRETS.name());
            verify(mockRegistry).deregisterScanner(ScanEngine.CONTAINERS.name());
            verify(mockRegistry).deregisterScanner(ScanEngine.IAC.name());
        }
    }
}
