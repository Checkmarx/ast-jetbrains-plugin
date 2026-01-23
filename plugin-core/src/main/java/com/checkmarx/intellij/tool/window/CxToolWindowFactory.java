package com.checkmarx.intellij.tool.window;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * Factory class to create the Checkmarx Scan Results tool window.
 * Creates a single tab showing scan results from Checkmarx One.
 * Authentication and license checking is delegated to CxToolWindowPanel.
 */
public class CxToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final String SCAN_RESULTS_TAB = "Scan Results";

    /**
     * {@inheritDoc}
     * Create the Scan Results tab.
     */
    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {
        ContentManager contentManager = toolWindow.getContentManager();

        // Create and add Scan Results tab
        final CxToolWindowPanel cxToolWindowPanel = new CxToolWindowPanel(project);
        contentManager.addContent(
                contentManager.getFactory().createContent(cxToolWindowPanel, SCAN_RESULTS_TAB, false)
        );
        Disposer.register(project, cxToolWindowPanel);
    }
}
