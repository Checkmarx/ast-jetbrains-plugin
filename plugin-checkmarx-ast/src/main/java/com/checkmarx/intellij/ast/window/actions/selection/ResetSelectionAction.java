package com.checkmarx.intellij.ast.window.actions.selection;

import com.checkmarx.intellij.ast.window.actions.CxToolWindowAction;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Action to reset the project and scan selection and clear results.
 */
@SuppressWarnings("ComponentNotRegistered")
public class ResetSelectionAction extends AnAction implements CxToolWindowAction {

    @Getter
    @Setter
    private boolean enabled = true;

    public ResetSelectionAction() {
        super(Bundle.messagePointer(Resource.RESET_ACTION));
        getTemplatePresentation().setIcon(AllIcons.Actions.Refresh);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(isEnabled());
    }

    /**
     * {@inheritDoc}
     * Open Checkmarx settings.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Optional.ofNullable(getCxToolWindowPanel(e)).ifPresent(cxToolWindowPanel -> {
            setEnabled(false);
            cxToolWindowPanel.refreshPanel();
            cxToolWindowPanel.resetPanel();
        });
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
