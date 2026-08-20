package com.checkmarx.intellij.devassist.test.configuration;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GlobalScannerControllerTest {

    private MockedStatic<GlobalSettingsState> gsStaticMock;
    private MockedStatic<ApplicationManager> amStaticMock;
    private MockedStatic<ProjectManager> pmStaticMock;

    private GlobalSettingsState mockState;
    private ProjectManager mockProjectManager;
    private GlobalScannerController controller;

    @BeforeEach
    void setUp() {
        mockState = mock(GlobalSettingsState.class);
        Application mockApp = mock(Application.class, RETURNS_DEEP_STUBS);
        mockProjectManager = mock(ProjectManager.class);

        gsStaticMock = mockStatic(GlobalSettingsState.class);
        amStaticMock = mockStatic(ApplicationManager.class);
        pmStaticMock = mockStatic(ProjectManager.class);

        gsStaticMock.when(GlobalSettingsState::getInstance).thenReturn(mockState);
        amStaticMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
        pmStaticMock.when(ProjectManager::getInstance).thenReturn(mockProjectManager);
        when(mockProjectManager.getOpenProjects()).thenReturn(new Project[0]);

        lenient().when(mockState.isAscaRealtime()).thenReturn(false);
        lenient().when(mockState.isOssRealtime()).thenReturn(false);
        lenient().when(mockState.isSecretDetectionRealtime()).thenReturn(false);
        lenient().when(mockState.isContainersRealtime()).thenReturn(false);
        lenient().when(mockState.isIacRealtime()).thenReturn(false);
        lenient().when(mockState.isMcpEnabled()).thenReturn(true);
        lenient().when(mockState.isAuthenticated()).thenReturn(true);

        controller = new GlobalScannerController();
    }

    @AfterEach
    void tearDown() {
        gsStaticMock.close();
        amStaticMock.close();
        pmStaticMock.close();
    }

    @Test
    @DisplayName("isScannerGloballyEnabled returns false when MCP is disabled")
    void isScannerGloballyEnabled_whenMcpDisabled_returnsFalse() {
        when(mockState.isMcpEnabled()).thenReturn(false);
        assertFalse(controller.isScannerGloballyEnabled(ScanEngine.ASCA));
    }

    @Test
    @DisplayName("isScannerGloballyEnabled returns false when scanner key absent from map")
    void isScannerGloballyEnabled_whenKeyAbsentFromMap_returnsFalse() {
        when(mockState.isMcpEnabled()).thenReturn(true);
        assertFalse(controller.isScannerGloballyEnabled(ScanEngine.OSS));
    }

    @Test
    @DisplayName("isScannerGloballyEnabled returns true after settingsApplied enables scanner")
    void isScannerGloballyEnabled_afterSettingsApplied_returnsTrue() {
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);

        controller.settingsApplied();

        assertTrue(controller.isScannerGloballyEnabled(ScanEngine.ASCA));
    }

    @Test
    @DisplayName("settingsApplied updates scanner state map")
    void settingsApplied_updatesStateMap() {
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);

        controller.settingsApplied();

        assertTrue(controller.getScannerStateMap().get(ScanEngine.ASCA));
    }

    @Test
    @DisplayName("markRegistered and isRegistered track project/engine pair")
    void markRegistered_then_isRegistered_returnsTrue() {
        Project mockProject = mock(Project.class);
        when(mockProject.getLocationHash()).thenReturn("proj-hash-123");

        controller.markRegistered(mockProject, ScanEngine.ASCA);

        assertTrue(controller.isRegistered(mockProject, ScanEngine.ASCA));
    }

    @Test
    @DisplayName("isRegistered returns false before markRegistered is called")
    void isRegistered_beforeRegister_returnsFalse() {
        Project mockProject = mock(Project.class);
        when(mockProject.getLocationHash()).thenReturn("proj-hash-456");

        assertFalse(controller.isRegistered(mockProject, ScanEngine.ASCA));
    }

    @Test
    @DisplayName("markUnregistered removes the project/engine pair")
    void markUnregistered_removesRegistration() {
        Project mockProject = mock(Project.class);
        when(mockProject.getLocationHash()).thenReturn("proj-hash-789");

        controller.markRegistered(mockProject, ScanEngine.CONTAINERS);
        controller.markUnregistered(mockProject, ScanEngine.CONTAINERS);

        assertFalse(controller.isRegistered(mockProject, ScanEngine.CONTAINERS));
    }

    @Test
    @DisplayName("checkAnyScannerEnabled returns false when all scanners are disabled")
    void checkAnyScannerEnabled_whenAllDisabled_returnsFalse() {
        when(mockState.isMcpEnabled()).thenReturn(true);
        assertFalse(controller.checkAnyScannerEnabled());
    }

    @Test
    @DisplayName("checkAnyScannerEnabled returns true when at least one scanner is enabled")
    void checkAnyScannerEnabled_whenOneEnabled_returnsTrue() {
        when(mockState.isOssRealtime()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);

        controller.settingsApplied();

        assertTrue(controller.checkAnyScannerEnabled());
    }

    @Test
    @DisplayName("getEnabledScanners returns empty list when all disabled")
    void getEnabledScanners_whenAllDisabled_returnsEmpty() {
        when(mockState.isMcpEnabled()).thenReturn(true);
        assertTrue(controller.getEnabledScanners().isEmpty());
    }

    @Test
    @DisplayName("getEnabledScanners returns only enabled scanners")
    void getEnabledScanners_whenSomeEnabled_returnsEnabledOnly() {
        when(mockState.isAscaRealtime()).thenReturn(true);
        when(mockState.isContainersRealtime()).thenReturn(true);
        when(mockState.isMcpEnabled()).thenReturn(true);

        controller.settingsApplied();

        var enabled = controller.getEnabledScanners();
        assertEquals(2, enabled.size());
        assertTrue(enabled.contains(ScanEngine.ASCA));
        assertTrue(enabled.contains(ScanEngine.CONTAINERS));
    }

    @Test
    @DisplayName("getScannerStateMap contains all engine keys after construction")
    void getScannerStateMap_containsAllEngineKeys() {
        var map = controller.getScannerStateMap();
        assertNotNull(map);
        assertTrue(map.containsKey(ScanEngine.ASCA));
        assertTrue(map.containsKey(ScanEngine.OSS));
        assertTrue(map.containsKey(ScanEngine.SECRETS));
        assertTrue(map.containsKey(ScanEngine.CONTAINERS));
        assertTrue(map.containsKey(ScanEngine.IAC));
    }
}
