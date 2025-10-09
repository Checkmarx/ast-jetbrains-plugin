package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.realtimeScanners.customProblemWindow.VulnerabilityToolWindow;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;


import javax.swing.*;

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
        // First tab
        contentManager.addContent(
                contentManager.getFactory().createContent(cxToolWindowPanel, "CxOne Result", false)
        );
        // Second tab
        Content customProblemContent = contentManager.getFactory().createContent(null, "CxOne Problems", false);
        final VulnerabilityToolWindow vulnerabilityToolWindow = new VulnerabilityToolWindow(project, customProblemContent);
        customProblemContent.setComponent(vulnerabilityToolWindow);
        contentManager.addContent(customProblemContent);

        Disposer.register(project, cxToolWindowPanel);
        Disposer.register(project, vulnerabilityToolWindow);
    }
}
