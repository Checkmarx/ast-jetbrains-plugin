package com.checkmarx.intellij.ignite.window;

import com.checkmarx.intellij.devassist.ui.findings.window.CxFindingsWindow;
import com.checkmarx.intellij.devassist.ui.findings.window.CxIgnoredFindings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Factory for creating the Checkmarx Ignite tool window with DevAssist features only.
 */
public class IgniteToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.getInstance();

        // DevAssist Findings Tab
        Content findingsContent = contentFactory.createContent(null, "Findings", false);
        CxFindingsWindow findingsWindow = new CxFindingsWindow(project, findingsContent);
        findingsContent.setComponent(findingsWindow.getContent());
        toolWindow.getContentManager().addContent(findingsContent);

        // DevAssist Ignored Findings Tab
        Content ignoredContent = contentFactory.createContent(null, "Ignored", false);
        CxIgnoredFindings ignoredFindings = new CxIgnoredFindings(project, ignoredContent);
        ignoredContent.setComponent(ignoredFindings.getContent());
        toolWindow.getContentManager().addContent(ignoredContent);
    }
}

