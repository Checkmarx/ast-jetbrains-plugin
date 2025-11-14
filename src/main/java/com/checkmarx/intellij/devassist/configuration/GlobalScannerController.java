package com.checkmarx.intellij.devassist.configuration;

import com.checkmarx.intellij.devassist.utils.ScanEngine;
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
public final class GlobalScannerController implements SettingsListener {
    private final Map<ScanEngine, Boolean> scannerStateMap =
            new EnumMap<>(ScanEngine.class);
    private final Set<String> activeScannerProjectSet = ConcurrentHashMap.newKeySet();

    public GlobalScannerController() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect()
                .subscribe(SettingsListener.SETTINGS_APPLIED, this);
        this.updateScannerState(state);
    }

    private void updateScannerState(GlobalSettingsState state) {
        scannerStateMap.put(ScanEngine.OSS, state.isOssRealtime());
        scannerStateMap.put(ScanEngine.SECRETS, state.isSecretDetectionRealtime());
        scannerStateMap.put(ScanEngine.CONTAINERS, state.isContainersRealtime());
        scannerStateMap.put(ScanEngine.IAC, state.isIacRealtime());
    }

    @Override
    public void settingsApplied() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        synchronized (this) {
            updateScannerState(state);
        }
        this.syncAll(state);
    }

    public synchronized boolean isScannerGloballyEnabled(ScanEngine type) {
        return scannerStateMap.getOrDefault(type, false);
    }

    public boolean isRegistered(Project project, ScanEngine type) {
        return activeScannerProjectSet.contains(key(project, type));
    }


    /**
     * Key Uses locationHash which is unique even for two project with same name but different LocationPath
     * LocationHash is used by intellij to uniquely identify the project
     */
    private static String key(Project project, ScanEngine type) {
        return project.getLocationHash() + "-" + type.name();
    }

    public void markRegistered(Project project, ScanEngine type) {
        activeScannerProjectSet.add(key(project, type));
    }

    public void markUnregistered(Project project, ScanEngine type) {
        activeScannerProjectSet.remove(key(project, type));
    }

    /**
     * Syncs  all the opened projects with latest changes in Scanner toggle settings
     * Calls @updateFromGlobal method for each project
     */
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
