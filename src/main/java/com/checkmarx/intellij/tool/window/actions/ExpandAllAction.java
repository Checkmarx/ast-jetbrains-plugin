package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.CxToolWindowPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Action to expand the results tree.
 */
@SuppressWarnings("ComponentNotRegistered")
public class ExpandAllAction extends AnAction implements CxToolWindowAction {


    public ExpandAllAction() {
        super(Bundle.messagePointer(Resource.EXPAND_ALL_ACTION));
    }

    /**
     * {@inheritDoc}
     * Trigger an expand-all in the tree for the current project, if it exists.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Optional.ofNullable(getCxToolWindowPanel(e)).ifPresent(CxToolWindowPanel::expandAll);
    }
}
