package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class BaseSelectionGroup extends DefaultActionGroup implements DumbAware, CxToolWindowAction {

    private String selected = "none";
    protected final Consumer<String> select = (str) -> selected = str;

    public BaseSelectionGroup() {
        super();
        setPopup(true);
        getTemplatePresentation().setText(() -> getPrefix() + selected);
    }

    protected abstract String getPrefix();

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        return new AnAction[]{};
    }

    @Override
    public boolean displayTextInToolbar() {
        return true;
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
        return false;
    }
}
