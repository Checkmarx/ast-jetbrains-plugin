package com.checkmarx.intellij.tool.window;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * Factory class to build {@link CxToolWindowPanel} panels.
 */
public class CxToolWindowFactory implements ToolWindowFactory, DumbAware {

    /**
     * {@inheritDoc}
     * Create and register a {@link CxToolWindowPanel}.
     */
    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {
        final CxToolWindowPanel cxToolWindowPanel = new CxToolWindowPanel(project);
        ContentManager contentManager = toolWindow.getContentManager();
        // First tab (your real panel)
        contentManager.addContent(
                contentManager.getFactory().createContent(cxToolWindowPanel, "Main View", false)
        );

        final VulnerabilityToolWindow ascaVulnerabilityToolWindow = new VulnerabilityToolWindow(project);

        contentManager.addContent(
                contentManager.getFactory().createContent(ascaVulnerabilityToolWindow, "Scan Results", false)
        );

        // Dispose properly
        Disposer.register(project, cxToolWindowPanel);
    }
}
