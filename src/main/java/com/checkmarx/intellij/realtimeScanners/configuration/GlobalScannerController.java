package com.checkmarx.intellij.realtimeScanners.configuration;

import com.checkmarx.intellij.realtimeScanners.common.ScannerKind;
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

    private final Map<ScannerKind, Boolean> activeMap =
            new EnumMap<>(ScannerKind.class);

    private final Set<String> registeredProjects = ConcurrentHashMap.newKeySet();

    public GlobalScannerController() {

        GlobalSettingsState state = GlobalSettingsState.getInstance();
        this.updateScannerState(state);
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect(this)
                .subscribe(SettingsListener.SETTINGS_APPLIED, this);
    }

    private void updateScannerState(GlobalSettingsState state){
        activeMap.put(ScannerKind.OSS, state.isOssRealtime());
        activeMap.put(ScannerKind.SECRETS, state.isSecretDetectionRealtime());
        activeMap.put(ScannerKind.CONTAINERS, state.isContainersRealtime());
        activeMap.put(ScannerKind.IAC, state.isIacRealtime());
    }

    @Override
    public void settingsApplied() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        synchronized (this) {
            updateScannerState(state);
        }
        this.syncAll(state);
    }

    public synchronized boolean isScannerGloballyEnabled(ScannerKind kind) {
        return activeMap.getOrDefault(kind, false);
    }

    public boolean isRegistered(Project project,ScannerKind kind) {
        return registeredProjects.contains(key(project, kind));
    }

    private static String key(Project project, ScannerKind kind) {
        return project.getName() + "-" + kind.name();
    }

    public void markRegistered(Project project, ScannerKind kind) {
        registeredProjects.add(key(project, kind));
    }

    public void markUnregistered(Project project, ScannerKind kind) {
        registeredProjects.remove(key(project, kind));
    }

    public synchronized void syncAll(GlobalSettingsState state) {

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
