package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.commands.Scan;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class CancelScanAction extends AnAction implements CxToolWindowAction {
    @Getter
    @Setter
    private static boolean enabled = false;

    public CancelScanAction() {
        super(Bundle.messagePointer(Resource.CANCEL_SCAN_ACTION));
    }

    /**
     * {@inheritDoc}
     * Cancel current checkmarx scan.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(Objects.requireNonNull(e.getProject()));
        ProgressManager.getInstance().run(new Task.Backgroundable(e.getProject(), "Cancelling scan..."){
            @SneakyThrows
            public void run(@NotNull ProgressIndicator progressIndicator) {
                StartScanAction.cancelStartScanAction();
                StartScanAction.setEnabled(false);
                setEnabled(false);
                String scanId = propertiesComponent.getValue("RunningScanId");
                Thread.sleep(20000);
                Scan.scanCancel(scanId);
                System.out.println("Scan with id: " + scanId + " cancelled.");
                StartScanAction.setEnabled(true);
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setEnabled(isEnabled());
    }
}
