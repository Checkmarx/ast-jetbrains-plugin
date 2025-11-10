package com.checkmarx.intellij.realtimeScanners.configuration;

import com.checkmarx.intellij.realtimeScanners.common.ScannerType;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Service(Service.Level.APP)
public final class GlobalScannerController implements  SettingsListener {
    private final Map<ScannerType, Boolean> scannerStateMap =
            new EnumMap<>(ScannerType.class);
    private final Set<String> activeScannerProjectSet = ConcurrentHashMap.newKeySet();

    public GlobalScannerController() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect()
                .subscribe(SettingsListener.SETTINGS_APPLIED, this);
        this.updateScannerState(state);
    }

    private void updateScannerState(GlobalSettingsState state){
        scannerStateMap.put(ScannerType.OSS, state.isOssRealtime());
        scannerStateMap.put(ScannerType.SECRETS, state.isSecretDetectionRealtime());
        scannerStateMap.put(ScannerType.CONTAINERS, state.isContainersRealtime());
        scannerStateMap.put(ScannerType.IAC, state.isIacRealtime());
    }

    @Override
    public void settingsApplied() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        synchronized (this) {
            updateScannerState(state);
        }
        this.syncAll(state);
    }

    public synchronized boolean isScannerGloballyEnabled(ScannerType type) {
        return scannerStateMap.getOrDefault(type, false);
    }

    public boolean isRegistered(Project project, ScannerType type) {
        return activeScannerProjectSet.contains(key(project, type));
    }

    private static String key(Project project, ScannerType type) {
        return project.getLocationHash() + "-" + type.name();
    }

    public void markRegistered(Project project, ScannerType type) {
        activeScannerProjectSet.add(key(project, type));
    }

    public void markUnregistered(Project project, ScannerType type) {
        activeScannerProjectSet.remove(key(project,type));
    }

    public synchronized void syncAll(GlobalSettingsState state) {
        if (!state.isAuthenticated()) {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                ScannerLifeCycleManager mgr = project.getService(ScannerLifeCycleManager.class);
                if (mgr != null) mgr.stopAll();
            }
            return;
        }
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            ScannerLifeCycleManager mgr = project.getService(ScannerLifeCycleManager.class);
            if (mgr != null) {
                mgr.updateFromGlobal(this);
            }
        }
    }

}
