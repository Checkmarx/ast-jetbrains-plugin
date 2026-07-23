package com.checkmarx.intellij.common.context;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PluginContextTest {

    private PluginContext context;

    @BeforeEach
    void setUp() {
        context = new PluginContext();
    }

    @Test
    @DisplayName("setPluginName devassist → isDevAssistPlugin returns true, isCheckmarxAstPlugin returns false")
    void setPluginName_devassist_isDevAssistPlugin_true() {
        context.setPluginName(PluginContext.PLUGIN_CHECKMARX_DEVASSIST);

        assertTrue(context.isDevAssistPlugin());
        assertFalse(context.isCheckmarxAstPlugin());
        assertEquals(PluginContext.PLUGIN_CHECKMARX_DEVASSIST, context.getPluginName());
    }

    @Test
    @DisplayName("setPluginName ast → isCheckmarxAstPlugin returns true, isDevAssistPlugin returns false")
    void setPluginName_ast_isCheckmarxAstPlugin_true() {
        context.setPluginName(PluginContext.PLUGIN_CHECKMARX_AST);

        assertTrue(context.isCheckmarxAstPlugin());
        assertFalse(context.isDevAssistPlugin());
    }

    @Test
    @DisplayName("getPluginDisplayName returns explicit displayName when set")
    void getPluginDisplayName_withDisplayName_returnsDisplayName() {
        context.setPluginName(PluginContext.PLUGIN_CHECKMARX_DEVASSIST);
        context.setPluginDisplayName("Checkmarx DevAssist");

        assertEquals("Checkmarx DevAssist", context.getPluginDisplayName());
    }

    @Test
    @DisplayName("getPluginDisplayName falls back to pluginName when displayName is null")
    void getPluginDisplayName_noDisplayName_returnsPluginName() {
        context.setPluginName(PluginContext.PLUGIN_CHECKMARX_DEVASSIST);

        assertEquals(PluginContext.PLUGIN_CHECKMARX_DEVASSIST, context.getPluginDisplayName());
    }

    @Test
    @DisplayName("isPlugin returns true for devassist plugin id")
    void isPlugin_devassistId_returnsTrue() {
        assertTrue(context.isPlugin(PluginContext.PLUGIN_CHECKMARX_DEVASSIST));
    }

    @Test
    @DisplayName("isPlugin returns true for ast plugin id")
    void isPlugin_astId_returnsTrue() {
        assertTrue(context.isPlugin(PluginContext.PLUGIN_CHECKMARX_AST));
    }

    @Test
    @DisplayName("isPlugin returns false for unknown id")
    void isPlugin_unknownId_returnsFalse() {
        assertFalse(context.isPlugin("unknown-plugin"));
    }

    @Test
    @DisplayName("isPlugin returns false for null id")
    void isPlugin_nullId_returnsFalse() {
        assertFalse(context.isPlugin(null));
    }

    @Test
    @DisplayName("getSettingsConfigurableClassName returns devassist class for devassist plugin")
    void getSettingsConfigurableClassName_devassist_returnsDevAssistClass() {
        context.setPluginName(PluginContext.PLUGIN_CHECKMARX_DEVASSIST);

        String className = context.getSettingsConfigurableClassName();

        assertNotNull(className);
        assertTrue(className.contains("DevAssist") || className.contains("devassist"));
    }

    @Test
    @DisplayName("getSettingsConfigurableClassName returns ast class for ast plugin")
    void getSettingsConfigurableClassName_ast_returnsAstClass() {
        context.setPluginName(PluginContext.PLUGIN_CHECKMARX_AST);

        String className = context.getSettingsConfigurableClassName();

        assertNotNull(className);
        assertTrue(className.contains("GlobalSettings") || className.contains("ast"));
    }

    @Test
    @DisplayName("getSettingsConfigurableClassName returns null when no plugin set")
    void getSettingsConfigurableClassName_noPlugin_returnsNull() {
        assertNull(context.getSettingsConfigurableClassName());
    }

    @Test
    @DisplayName("reset clears pluginName and displayName")
    void reset_clearsAllFields() {
        context.setPluginName(PluginContext.PLUGIN_CHECKMARX_DEVASSIST);
        context.setPluginDisplayName("display");

        context.reset();

        assertNull(context.getPluginName());
        assertNull(context.getPluginDisplayName());
        assertFalse(context.isDevAssistPlugin());
        assertFalse(context.isCheckmarxAstPlugin());
    }

    @Test
    @DisplayName("getInstance delegates to ApplicationManager.getApplication().getService()")
    void getInstance_delegatesToApplicationManager() {
        try (MockedStatic<ApplicationManager> appMock = mockStatic(ApplicationManager.class)) {
            Application mockApp = mock(Application.class);
            PluginContext expectedInstance = new PluginContext();
            appMock.when(ApplicationManager::getApplication).thenReturn(mockApp);
            when(mockApp.getService(PluginContext.class)).thenReturn(expectedInstance);

            PluginContext result = PluginContext.getInstance();

            assertSame(expectedInstance, result);
        }
    }
}
