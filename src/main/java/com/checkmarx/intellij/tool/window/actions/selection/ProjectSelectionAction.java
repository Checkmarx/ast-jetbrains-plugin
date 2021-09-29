package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class ProjectSelectionAction extends AnAction implements CxToolWindowAction {

    private final Consumer<String> select;

    public ProjectSelectionAction(String name, Consumer<String> select) {
        super(name);
        this.select = select;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        select.accept(getTemplatePresentation().getText());
    }
}
