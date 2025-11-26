package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Action to collapse the results tree.
 */
@SuppressWarnings("ComponentNotRegistered")
public class CollapseAllAction extends AnAction implements CxToolWindowAction {


    public CollapseAllAction() {
        super(Bundle.messagePointer(Resource.COLLAPSE_ALL_ACTION));
    }

    /**
     * {@inheritDoc}
     * Trigger a collapse all in the tree for the current project, if it exists.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Optional.ofNullable(getCxToolWindowPanel(e)).ifPresent(CxToolWindowPanel::collapseAll);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
