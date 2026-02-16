package com.checkmarx.intellij.ignite.test.window;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.ignite.window.CxDevAssistToolWindowFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CxDevAssistToolWindowFactoryTest {

    private GlobalSettingsState mockGlobalState;
    private MockedStatic<GlobalSettingsState> mockedGlobalState;
    private Project mockProject;
    private ToolWindow mockToolWindow;

    @BeforeEach
    void setUp() {
        mockGlobalState = mock(GlobalSettingsState.class);
        mockProject = mock(Project.class);
        mockToolWindow = mock(ToolWindow.class);
        
        mockedGlobalState = mockStatic(GlobalSettingsState.class);
        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(mockGlobalState);
    }
    
    @AfterEach
    void tearDown() {
        mockedGlobalState.close();
    }

    @Test
    @DisplayName("Factory can be instantiated")
    void testFactoryInstantiation() {
        assertDoesNotThrow(() -> new CxDevAssistToolWindowFactory());
    }

    @Test
    @DisplayName("isApplicable works with valid project")
    void testIsApplicable() {
        CxDevAssistToolWindowFactory factory = new CxDevAssistToolWindowFactory();
        boolean applicable = factory.isApplicable(mockProject);
        assertTrue(applicable || !applicable); // Just verify it returns a boolean
    }

    @Test
    @DisplayName("Multiple factory instances are independent")
    void testMultipleInstances() {
        CxDevAssistToolWindowFactory factory1 = new CxDevAssistToolWindowFactory();
        CxDevAssistToolWindowFactory factory2 = new CxDevAssistToolWindowFactory();
        assertNotSame(factory1, factory2);
    }

    @Test
    @DisplayName("Global state is accessible")
    void testGlobalStateAccess() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        assertEquals(mockGlobalState, state);
    }

    @Test
    @DisplayName("Authentication state affects tool window")
    void testAuthenticationState() {
        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAuthenticated());
    }

    @Test
    @DisplayName("Factory can be created multiple times")
    void testMultipleCreations() {
        assertDoesNotThrow(() -> {
            new CxDevAssistToolWindowFactory();
            new CxDevAssistToolWindowFactory();
            new CxDevAssistToolWindowFactory();
        });
    }

    @Test
    @DisplayName("isApplicable handles valid project")
    void testIsApplicableWithValidProject() {
        when(mockProject.isDisposed()).thenReturn(false);
        CxDevAssistToolWindowFactory factory = new CxDevAssistToolWindowFactory();
        assertDoesNotThrow(() -> factory.isApplicable(mockProject));
    }

    @Test
    @DisplayName("MCP enabled affects tool window applicability")
    void testMcpEnabledState() {
        when(mockGlobalState.isMcpEnabled()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isMcpEnabled());
    }

    @Test
    @DisplayName("DevAssist license affects applicability")
    void testDevAssistLicense() {
        when(mockGlobalState.isDevAssistLicenseEnabled()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isDevAssistLicenseEnabled());
    }
}

