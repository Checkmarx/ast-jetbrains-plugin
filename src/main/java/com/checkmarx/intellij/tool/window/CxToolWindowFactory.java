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
        // First tab (your real panel)
        contentManager.addContent(
                contentManager.getFactory().createContent(cxToolWindowPanel, "Main View", false)
        );

        final VulnerabilityToolWindow vulnerabilityToolWindow = new VulnerabilityToolWindow(project);
        Content dummyContent = contentManager.getFactory().createContent(vulnerabilityToolWindow, "Dummy Tab", false);
        contentManager.addContent(dummyContent);

        // Example: periodically update tab title (better: update when your issues update)
        Timer timer = new Timer(1000, e -> {
            int problemCount = vulnerabilityToolWindow.getProblemCount();
            String title = "Dummy Tab";
            if (problemCount > 0) {
                title += " "+problemCount;
            }
            dummyContent.setDisplayName(title);
        });
        timer.start();
        // Dispose properly
        Disposer.register(project, () -> timer.stop());
        Disposer.register(project, cxToolWindowPanel);
        Disposer.register(project, vulnerabilityToolWindow);
    }
}
