package com.checkmarx.intellij.cxdevassist.window.actions;

import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.cxdevassist.settings.CxDevAssistSettingsConfigurable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import org.jetbrains.annotations.NotNull;

/**
 * OpenCxDevAssistSettingsAction class to open Checkmarx Developer Assist settings from the tool window.
 */
@SuppressWarnings("ComponentNotRegistered")
public class OpenCxDevAssistSettingsAction extends AnAction {

    public OpenCxDevAssistSettingsAction() {
        super(Bundle.messagePointer(Resource.SETTINGS_ACTION));
    }

    /**
     * {@inheritDoc}
     * Open Checkmarx settings.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        ShowSettingsUtil.getInstance()
                        .showSettingsDialog(e.getProject(), CxDevAssistSettingsConfigurable.class);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
