package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.settings.global.GlobalSettingsConfigurable;
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
}
