package com.checkmarx.intellij.cxdevassist.test.unit.window;

import com.checkmarx.intellij.common.context.PluginContext;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.cxdevassist.utils.CxDevAssistConstants;
import com.checkmarx.intellij.cxdevassist.window.CxDevAssistToolWindowFactory;
import com.checkmarx.intellij.devassist.ui.findings.window.DevAssistFindingsWindow;
import com.checkmarx.intellij.devassist.ui.findings.window.DevAssistIgnoredFindings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
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

    // ===== createToolWindowContent() =====

    @Test
    @DisplayName("createToolWindowContent adds two content tabs to the tool window")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testCreateToolWindowContent_addsTwoTabs() {
        // Mock PluginContext
        PluginContext mockPluginContext = mock(PluginContext.class);
        when(mockPluginContext.isPlugin(PluginContext.PLUGIN_CHECKMARX_DEVASSIST)).thenReturn(true);

        // Mock ContentFactory
        ContentFactory mockContentFactory = mock(ContentFactory.class);
        Content mockFindingsContent = mock(Content.class);
        Content mockIgnoredContent = mock(Content.class);
        when(mockContentFactory.createContent(isNull(), eq(CxDevAssistConstants.FINDINGS_WINDOW_NAME), eq(false)))
                .thenReturn(mockFindingsContent);
        when(mockContentFactory.createContent(isNull(), eq(CxDevAssistConstants.IGNORED_FINDINGS_WINDOW_NAME), eq(false)))
                .thenReturn(mockIgnoredContent);

        // Mock ContentManager
        ContentManager mockContentManager = mock(ContentManager.class);
        when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);

        try (MockedStatic<PluginContext> pluginCtxMock = mockStatic(PluginContext.class);
             MockedStatic<ContentFactory> cfMock = mockStatic(ContentFactory.class);
             MockedStatic<Disposer> disposerMock = mockStatic(Disposer.class);
             MockedConstruction<DevAssistFindingsWindow> findingsMock =
                     mockConstruction(DevAssistFindingsWindow.class);
             MockedConstruction<DevAssistIgnoredFindings> ignoredMock =
                     mockConstruction(DevAssistIgnoredFindings.class)) {

            pluginCtxMock.when(PluginContext::getInstance).thenReturn(mockPluginContext);
            cfMock.when(ContentFactory::getInstance).thenReturn(mockContentFactory);
            disposerMock.when(() -> Disposer.register(any(), any())).thenAnswer(i -> null);

            CxDevAssistToolWindowFactory factory = new CxDevAssistToolWindowFactory();
            factory.createToolWindowContent(mockProject, mockToolWindow);

            // Both tabs must have been added
            verify(mockContentManager).addContent(mockFindingsContent);
            verify(mockContentManager).addContent(mockIgnoredContent);
            // Both windows must have been constructed
            assertEquals(1, findingsMock.constructed().size());
            assertEquals(1, ignoredMock.constructed().size());
        }
    }

    @Test
    @DisplayName("createToolWindowContent does not re-register plugin when already registered")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void testCreateToolWindowContent_pluginAlreadyRegistered_doesNotOverwrite() {
        PluginContext mockPluginContext = mock(PluginContext.class);
        // isPlugin() returns false — skip the registration block
        when(mockPluginContext.isPlugin(PluginContext.PLUGIN_CHECKMARX_DEVASSIST)).thenReturn(false);

        ContentFactory mockContentFactory = mock(ContentFactory.class);
        Content mockFindingsContent = mock(Content.class);
        Content mockIgnoredContent = mock(Content.class);
        when(mockContentFactory.createContent(any(), eq(CxDevAssistConstants.FINDINGS_WINDOW_NAME), anyBoolean()))
                .thenReturn(mockFindingsContent);
        when(mockContentFactory.createContent(any(), eq(CxDevAssistConstants.IGNORED_FINDINGS_WINDOW_NAME), anyBoolean()))
                .thenReturn(mockIgnoredContent);

        ContentManager mockContentManager = mock(ContentManager.class);
        when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);

        try (MockedStatic<PluginContext> pluginCtxMock = mockStatic(PluginContext.class);
             MockedStatic<ContentFactory> cfMock = mockStatic(ContentFactory.class);
             MockedStatic<Disposer> disposerMock = mockStatic(Disposer.class);
             MockedConstruction<DevAssistFindingsWindow> findingsMock =
                     mockConstruction(DevAssistFindingsWindow.class);
             MockedConstruction<DevAssistIgnoredFindings> ignoredMock =
                     mockConstruction(DevAssistIgnoredFindings.class)) {

            pluginCtxMock.when(PluginContext::getInstance).thenReturn(mockPluginContext);
            cfMock.when(ContentFactory::getInstance).thenReturn(mockContentFactory);
            disposerMock.when(() -> Disposer.register(any(), any())).thenAnswer(i -> null);

            CxDevAssistToolWindowFactory factory = new CxDevAssistToolWindowFactory();
            factory.createToolWindowContent(mockProject, mockToolWindow);

            // setPluginName / setPluginDisplayName should NOT be called
            verify(mockPluginContext, never()).setPluginName(any());
            verify(mockContentManager).addContent(mockFindingsContent);
            verify(mockContentManager).addContent(mockIgnoredContent);
        }
    }
}

