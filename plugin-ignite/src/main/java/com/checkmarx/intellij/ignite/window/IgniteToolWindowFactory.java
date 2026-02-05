package com.checkmarx.intellij.ignite.window;

import com.checkmarx.intellij.devassist.ui.findings.window.CxFindingsWindow;
import com.checkmarx.intellij.devassist.ui.findings.window.CxIgnoredFindings;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating the Checkmarx Ignite tool window with DevAssist features only.
 */
public class IgniteToolWindowFactory implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        // DevAssist Findings Tab
        Content findingsContent = contentFactory.createContent(null, DevAssistConstants.DEVASSIST_TAB, false);
        CxFindingsWindow findingsWindow = new CxFindingsWindow(project, findingsContent);
        findingsContent.setComponent(findingsWindow.getContent());
        toolWindow.getContentManager().addContent(findingsContent);
        Disposer.register(project, findingsWindow);

        // DevAssist Ignored Findings Tab
        Content ignoredContent = contentFactory.createContent(null, DevAssistConstants.IGNORED_FINDINGS_TAB, false);
        CxIgnoredFindings ignoredFindings = new CxIgnoredFindings(project, ignoredContent);
        ignoredContent.setComponent(ignoredFindings.getContent());
        toolWindow.getContentManager().addContent(ignoredContent);
        Disposer.register(project, ignoredFindings);
    }
}

