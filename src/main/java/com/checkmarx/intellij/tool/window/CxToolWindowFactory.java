package com.checkmarx.intellij.tool.window;

import com.checkmarx.intellij.devassist.ui.findings.window.CxFindingsWindow;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.ui.findings.window.CxIgnoredFindings;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
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
        // First tab
        contentManager.addContent(
                contentManager.getFactory().createContent(cxToolWindowPanel, Constants.RealTimeConstants.SCAN_RESULTS_TAB, false)
        );
        // Second tab
        Content customProblemContent = contentManager.getFactory().createContent(null, DevAssistConstants.DEVASSIST_TAB, false);
        final CxFindingsWindow vulnerabilityToolWindow = new CxFindingsWindow(project, customProblemContent);
        customProblemContent.setComponent(vulnerabilityToolWindow);
        contentManager.addContent(customProblemContent);
        // Third tab
        Content ignoredVulnerabilities = contentManager.getFactory().createContent(null, Constants.RealTimeConstants.IGNORED_FINDINGS_TAB, false);
        final CxIgnoredFindings ignoredVulnerabilitiesWindow = new CxIgnoredFindings(project, ignoredVulnerabilities);
        ignoredVulnerabilities.setComponent(ignoredVulnerabilitiesWindow);
        contentManager.addContent(ignoredVulnerabilities);
        // Register disposables
        Disposer.register(project, cxToolWindowPanel);
        Disposer.register(project, vulnerabilityToolWindow);
    }
}
