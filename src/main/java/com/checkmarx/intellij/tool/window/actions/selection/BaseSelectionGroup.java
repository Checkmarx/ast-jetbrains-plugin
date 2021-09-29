package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseSelectionGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    protected final Project project;

    public BaseSelectionGroup(Project project) {
        super();
        this.project = project;
        setPopup(true);
        getTemplatePresentation().setText(() -> getPrefix() + "none");
    }

    protected abstract String getPrefix();

    @Override
    public boolean displayTextInToolbar() {
        return true;
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
        return false;
    }
}
