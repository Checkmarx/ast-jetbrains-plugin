package com.checkmarx.intellij.tool.window.actions.selection;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.tool.window.actions.CxToolWindowAction;
import com.intellij.icons.AllIcons;
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
        getTemplatePresentation().setIcon(AllIcons.Debugger.Db_invalid_breakpoint);
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
}
