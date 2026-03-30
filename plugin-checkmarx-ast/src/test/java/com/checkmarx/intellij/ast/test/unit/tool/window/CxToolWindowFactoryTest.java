package com.checkmarx.intellij.ast.test.unit.tool.window;

import com.checkmarx.intellij.ast.window.CxToolWindowFactory;
import com.checkmarx.intellij.ast.window.CxToolWindowPanel;
import com.checkmarx.intellij.common.context.PluginContext;
import com.checkmarx.intellij.devassist.ui.findings.window.DevAssistFindingsWindow;
import com.checkmarx.intellij.devassist.ui.findings.window.DevAssistIgnoredFindings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CxToolWindowFactory.
 * Uses Unsafe to bypass constructor and static mocks / mockConstruction for IntelliJ platform dependencies.
 */
@ExtendWith(MockitoExtension.class)
class CxToolWindowFactoryTest {

    @Mock
    private Project mockProject;

    @Mock
    private ToolWindow mockToolWindow;

    @Mock
    private ContentManager mockContentManager;

    @Mock
    private ContentFactory mockContentFactory;

    @Mock
    private Content mockContent;

    @Mock
    private PluginContext mockPluginContext;

    private CxToolWindowFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        factory = (CxToolWindowFactory) unsafe.allocateInstance(CxToolWindowFactory.class);
    }

    @Test
    void createToolWindowContent_RegistersPluginContextOnFirstCall() {
        try (MockedStatic<PluginContext> mockedPluginContext = mockStatic(PluginContext.class);
             MockedStatic<Disposer> mockedDisposer = mockStatic(Disposer.class);
             MockedConstruction<CxToolWindowPanel> mockedPanel = mockConstruction(CxToolWindowPanel.class);
             MockedConstruction<DevAssistFindingsWindow> mockedFindings = mockConstruction(DevAssistFindingsWindow.class);
             MockedConstruction<DevAssistIgnoredFindings> mockedIgnored = mockConstruction(DevAssistIgnoredFindings.class)) {

            when(mockPluginContext.isPlugin(PluginContext.PLUGIN_CHECKMARX_AST)).thenReturn(true);
            mockedPluginContext.when(PluginContext::getInstance).thenReturn(mockPluginContext);

            when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);
            when(mockContentManager.getFactory()).thenReturn(mockContentFactory);
            when(mockContentFactory.createContent(any(), anyString(), anyBoolean())).thenReturn(mockContent);

            factory.createToolWindowContent(mockProject, mockToolWindow);

            verify(mockPluginContext).setPluginName(PluginContext.PLUGIN_CHECKMARX_AST);
        }
    }

    @Test
    void createToolWindowContent_AddsThreeContentTabs() {
        try (MockedStatic<PluginContext> mockedPluginContext = mockStatic(PluginContext.class);
             MockedStatic<Disposer> mockedDisposer = mockStatic(Disposer.class);
             MockedConstruction<CxToolWindowPanel> mockedPanel = mockConstruction(CxToolWindowPanel.class);
             MockedConstruction<DevAssistFindingsWindow> mockedFindings = mockConstruction(DevAssistFindingsWindow.class);
             MockedConstruction<DevAssistIgnoredFindings> mockedIgnored = mockConstruction(DevAssistIgnoredFindings.class)) {

            when(mockPluginContext.isPlugin(anyString())).thenReturn(false);
            mockedPluginContext.when(PluginContext::getInstance).thenReturn(mockPluginContext);

            when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);
            when(mockContentManager.getFactory()).thenReturn(mockContentFactory);
            when(mockContentFactory.createContent(any(), anyString(), anyBoolean())).thenReturn(mockContent);

            factory.createToolWindowContent(mockProject, mockToolWindow);

            // Scan Results + DevAssist Findings + Ignored Findings
            verify(mockContentManager, times(3)).addContent(any(Content.class));
        }
    }

    @Test
    void createToolWindowContent_WithPluginContextAlreadySet_SkipsRegistration() {
        try (MockedStatic<PluginContext> mockedPluginContext = mockStatic(PluginContext.class);
             MockedStatic<Disposer> mockedDisposer = mockStatic(Disposer.class);
             MockedConstruction<CxToolWindowPanel> mockedPanel = mockConstruction(CxToolWindowPanel.class);
             MockedConstruction<DevAssistFindingsWindow> mockedFindings = mockConstruction(DevAssistFindingsWindow.class);
             MockedConstruction<DevAssistIgnoredFindings> mockedIgnored = mockConstruction(DevAssistIgnoredFindings.class)) {

            // isPlugin returns false → plugin name is already set, skip setPluginName
            when(mockPluginContext.isPlugin(anyString())).thenReturn(false);
            mockedPluginContext.when(PluginContext::getInstance).thenReturn(mockPluginContext);

            when(mockToolWindow.getContentManager()).thenReturn(mockContentManager);
            when(mockContentManager.getFactory()).thenReturn(mockContentFactory);
            when(mockContentFactory.createContent(any(), anyString(), anyBoolean())).thenReturn(mockContent);

            factory.createToolWindowContent(mockProject, mockToolWindow);

            // setPluginName should NOT be called when isPlugin returns false
            verify(mockPluginContext, never()).setPluginName(anyString());
        }
    }
}

