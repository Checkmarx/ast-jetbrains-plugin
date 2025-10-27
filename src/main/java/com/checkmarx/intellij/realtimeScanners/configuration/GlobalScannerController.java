package com.checkmarx.intellij.realtimeScanners.configuration;

import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;


import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.APP)
public final class GlobalScannerController implements Disposable, SettingsListener {


    private final Map<RealtimeScannerManager.ScannerKind, Boolean> activeMap =
            new EnumMap<>(RealtimeScannerManager.ScannerKind.class);

    private final Set<String> registeredProjects = ConcurrentHashMap.newKeySet();


    public GlobalScannerController() {

        GlobalSettingsState state = GlobalSettingsState.getInstance();

        activeMap.put(RealtimeScannerManager.ScannerKind.OSS, state.isOssRealtime());
        activeMap.put(RealtimeScannerManager.ScannerKind.SECRETS, state.isSecretDetectionRealtime());
        activeMap.put(RealtimeScannerManager.ScannerKind.CONTAINERS, state.isContainersRealtime());
        activeMap.put(RealtimeScannerManager.ScannerKind.IAC, state.isIacRealtime());

        ApplicationManager.getApplication()
                .getMessageBus()
                .connect(this)
                .subscribe(SettingsListener.SETTINGS_APPLIED, this);
    }

    @Override
    public void settingsApplied() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();

        synchronized (this) {
            activeMap.put(RealtimeScannerManager.ScannerKind.OSS, state.isOssRealtime());
            activeMap.put(RealtimeScannerManager.ScannerKind.SECRETS, state.isSecretDetectionRealtime());
            activeMap.put(RealtimeScannerManager.ScannerKind.CONTAINERS, state.isContainersRealtime());
            activeMap.put(RealtimeScannerManager.ScannerKind.IAC, state.isIacRealtime());
        }

        syncAll();
    }

    public synchronized boolean isScannerGloballyEnabled(RealtimeScannerManager.ScannerKind kind) {
        return activeMap.getOrDefault(kind, false);
    }

    public boolean isRegistered(Project project, RealtimeScannerManager.ScannerKind kind) {
        return registeredProjects.contains(key(project, kind));
    }

    private static String key(Project project, RealtimeScannerManager.ScannerKind kind) {
        return project.getName() + ":" + kind.name();
    }

    public void markRegistered(Project project, RealtimeScannerManager.ScannerKind kind) {
        registeredProjects.add(key(project, kind));
    }

    public void markUnregistered(Project project, RealtimeScannerManager.ScannerKind kind) {
        registeredProjects.remove(key(project, kind));
    }

    public synchronized void syncAll() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();

        if (!state.isAuthenticated()) {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                RealtimeScannerManager mgr = project.getService(RealtimeScannerManager.class);
                if (mgr != null) mgr.stopAll();
            }
            return;
        }

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            RealtimeScannerManager mgr = project.getService(RealtimeScannerManager.class);
            if (mgr != null) {
                mgr.updateFromGlobal(this);
            }
        }
    }

    @Override
    public void dispose() {
        activeMap.clear();
    }
}
