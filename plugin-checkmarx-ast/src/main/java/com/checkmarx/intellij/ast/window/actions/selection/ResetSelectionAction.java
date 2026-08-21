package com.checkmarx.intellij.ast.window.actions.selection;

import com.checkmarx.intellij.ast.window.actions.CxToolWindowAction;
import com.checkmarx.intellij.common.resources.Bundle;
import com.checkmarx.intellij.common.resources.Resource;
import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionManager;
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
        // Use the registered action presentation as the single source of truth for toolbar enablement
        AnAction registered = Utils.getResetSelectionAction();
        boolean enabledState = registered != null ? registered.getTemplatePresentation().isEnabled() : isEnabled();
        e.getPresentation().setEnabled(enabledState);
    }

    /**
     * {@inheritDoc}
     * Open Checkmarx settings.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Optional.ofNullable(getCxToolWindowPanel(e)).ifPresent(cxToolWindowPanel -> {
            // Disable the canonical (registered) action presentation so toolbar visuals update regardless of instance identity
            AnAction registered = Utils.getResetSelectionAction();
            if (registered != null) {
                registered.getTemplatePresentation().setEnabled(false);
            } else {
                setEnabled(false);
            }
            cxToolWindowPanel.refreshPanel();
            cxToolWindowPanel.resetPanel();
        });
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
