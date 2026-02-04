package com.checkmarx.intellij.ast.window;

import com.checkmarx.intellij.devassist.ui.findings.window.CxFindingsWindow;
import com.checkmarx.intellij.devassist.ui.findings.window.CxIgnoredFindings;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.intellij.openapi.diagnostic.Logger;
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
 * <p>
 * This factory simply creates and registers the tool window tabs.
 * Authentication and license checking is delegated to each panel's constructor/draw methods,
 * which decide what UI to display based on the current state.
 * <p>
 * Authentication and License Matrix (handled by each panel internally):
 * <p>
 * When NOT authenticated:
 * - Both tabs show their respective auth panels
 * <p>
 * When authenticated - Scan Results tab (CxToolWindowPanel):
 * - One Assist = TRUE: Show actual Scan Results content
 * - One Assist = FALSE, Dev Assist = TRUE: Show promotional upsell panel
 * - One Assist = FALSE, Dev Assist = FALSE: Show actual Scan Results content
 * <p>
 * When authenticated - Findings tab (CxFindingsWindow):
 * - One Assist = TRUE or Dev Assist = TRUE: Show split view (findings + promotional panel)
 * - One Assist = FALSE and Dev Assist = FALSE: Show full-screen promotional panel
 */
public class CxToolWindowFactory implements ToolWindowFactory, DumbAware {

    private static final Logger LOG = Logger.getInstance(CxToolWindowFactory.class);
    private static final String SCAN_RESULTS_TAB = "Scan Results";

    /**
     * {@inheritDoc}
     * Create and register tool window tabs.
     * Each panel handles its own authentication and license checking internally.
     */
    @Override
    public void createToolWindowContent(@NotNull Project project,
                                        @NotNull ToolWindow toolWindow) {

        ContentManager contentManager = toolWindow.getContentManager();

        // Add Scan Results tab - CxToolWindowPanel handles auth/license logic internally
        addScanResultsTab(project, contentManager);

        // Add Dev Assist Findings tab - CxFindingsWindow handles auth/license logic internally
        addDevAssistFindingsTab(project, contentManager);

        // Third tab
        Content ignoredVulnerabilities = contentManager.getFactory().createContent(null, DevAssistConstants.IGNORED_FINDINGS_TAB, false);
        final CxIgnoredFindings ignoredVulnerabilitiesWindow = new CxIgnoredFindings(project, ignoredVulnerabilities);
        ignoredVulnerabilities.setComponent(ignoredVulnerabilitiesWindow);
        contentManager.addContent(ignoredVulnerabilities);
        Disposer.register(project, ignoredVulnerabilitiesWindow);


    }

    /**
     * Add the Scan Results tab.
     * CxToolWindowPanel handles authentication and license checking internally in its draw methods.
     */
    private void addScanResultsTab(@NotNull Project project,
                                   @NotNull ContentManager contentManager) {
        final CxToolWindowPanel cxToolWindowPanel = new CxToolWindowPanel(project);
        contentManager.addContent(
                contentManager.getFactory().createContent(cxToolWindowPanel, SCAN_RESULTS_TAB, false)
        );
        Disposer.register(project, cxToolWindowPanel);
    }

    /**
     * Add the Dev Assist Findings tab.
     * CxFindingsWindow handles authentication and license checking internally in its draw methods.
     */
    private void addDevAssistFindingsTab(@NotNull Project project,
                                         @NotNull ContentManager contentManager) {
        try {
            Content customProblemContent = contentManager.getFactory().createContent(null, DevAssistConstants.DEVASSIST_TAB, false);
            final CxFindingsWindow vulnerabilityToolWindow = new CxFindingsWindow(project, customProblemContent);
            customProblemContent.setComponent(vulnerabilityToolWindow);
            contentManager.addContent(customProblemContent);
            Disposer.register(project, vulnerabilityToolWindow);
        } catch (Exception e) {
            LOG.error("Failed to create Dev Assist Findings tab", e);
        }
    }
}
