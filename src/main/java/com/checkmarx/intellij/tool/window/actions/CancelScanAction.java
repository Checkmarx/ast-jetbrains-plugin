package com.checkmarx.intellij.tool.window.actions;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.commands.Scan;
import com.intellij.ide.ActivityTracker;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class CancelScanAction extends AnAction implements CxToolWindowAction {

    private static final Logger LOGGER = Utils.getLogger(CancelScanAction.class);

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
        ProgressManager.getInstance().run(new Task.Backgroundable(e.getProject(), Bundle.message(Resource.SCAN_CANCELING_TITLE)){
            @SneakyThrows
            public void run(@NotNull ProgressIndicator progressIndicator) {
                StartScanAction.cancelRunningScan();
                String scanId = propertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY);
                LOGGER.info(Bundle.message(Resource.SCAN_CANCELING_INFO, scanId));
                Scan.scanCancel(scanId);
                LOGGER.info(Bundle.message(Resource.SCAN_CANCELED, scanId));
                propertiesComponent.setValue(Constants.RUNNING_SCAN_ID_PROPERTY, StringUtils.EMPTY);
                ActivityTracker.getInstance().inc();
                Utils.notifyScan(null, Bundle.message(Resource.SCAN_CANCELED_SUCCESSFULLY), e.getProject(), null, NotificationType.INFORMATION, null);
            }
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        try {
            super.update(e);

            e.getPresentation().setVisible(StartScanAction.getUserHasPermissionsToScan());

            PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(Objects.requireNonNull(e.getProject()));
            boolean isScanRunning = StringUtils.isNotBlank(propertiesComponent.getValue(Constants.RUNNING_SCAN_ID_PROPERTY));
            e.getPresentation().setEnabled(isScanRunning);
        }
        catch (Exception ex) {
            ex.printStackTrace();
            e.getPresentation().setEnabled(false);
        }
    }
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

}
