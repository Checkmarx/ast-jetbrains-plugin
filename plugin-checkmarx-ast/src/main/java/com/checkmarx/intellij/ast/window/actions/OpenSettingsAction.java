package com.checkmarx.intellij.ast.window.actions;

import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.settings.global.GlobalSettingsConfigurable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Action to open Checkmarx settings from the tool window.
 */
@SuppressWarnings("ComponentNotRegistered")
public class OpenSettingsAction extends AnAction implements CxToolWindowAction {

    public OpenSettingsAction() {
        super(Bundle.messagePointer(Resource.SETTINGS_ACTION));
    }

    /**
     * {@inheritDoc}
     * Open Checkmarx settings.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance()
                        .showSettingsDialog(e.getProject(), GlobalSettingsConfigurable.class);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
