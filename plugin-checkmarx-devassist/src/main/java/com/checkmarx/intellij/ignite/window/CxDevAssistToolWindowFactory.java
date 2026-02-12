package com.checkmarx.intellij.ignite.window;

import com.checkmarx.intellij.common.context.PluginContext;
import com.checkmarx.intellij.devassist.ui.findings.window.DevAssistFindingsWindow;
import com.checkmarx.intellij.devassist.ui.findings.window.DevAssistIgnoredFindings;
import com.checkmarx.intellij.ignite.utils.IgniteConstants;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;

/**
 * Factory for creating the Checkmarx Developer Assist tool window with realtime scanners features only.
 */
public class CxDevAssistToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final Logger LOGGER = Logger.getInstance(CxDevAssistToolWindowFactory.class);

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Register plugin context (only once)
        if (PluginContext.getInstance().isPlugin(PluginContext.PLUGIN_IGNITE)) {
            PluginContext.getInstance().setPluginName(PluginContext.PLUGIN_IGNITE);
            PluginContext.getInstance().setPluginDisplayName(IgniteConstants.PLUGIN_NAME);
            LOGGER.info(format("Registered plugin context: %s", PluginContext.PLUGIN_IGNITE));
        }
        ContentFactory contentFactory = ContentFactory.getInstance();

        // DevAssist Findings Tab
        Content findingsContent = contentFactory.createContent(null, IgniteConstants.FINDINGS_WINDOW_NAME, false);
        DevAssistFindingsWindow findingsWindow = new DevAssistFindingsWindow(project, findingsContent, IgniteConstants.PLUGIN_NAME);
        findingsContent.setComponent(findingsWindow);
        toolWindow.getContentManager().addContent(findingsContent);
        Disposer.register(project, findingsWindow);

        // DevAssist Ignored Findings Tab
        Content ignoredContent = contentFactory.createContent(null, IgniteConstants.IGNORED_FINDINGS_WINDOW_NAME, false);
        DevAssistIgnoredFindings ignoredFindings = new DevAssistIgnoredFindings(project, ignoredContent);
        ignoredContent.setComponent(ignoredFindings);
        toolWindow.getContentManager().addContent(ignoredContent);
        Disposer.register(project, ignoredFindings);
    }
}

