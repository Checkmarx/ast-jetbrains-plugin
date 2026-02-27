package com.checkmarx.intellij.devassist.configuration;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsListener;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Application-level controller that tracks the global enablement state of each realtime scanner
 * and keeps all open projects synchronized with the latest toggle values from global settings.
 */
@Service(Service.Level.APP)
public final class GlobalScannerController implements SettingsListener {
    @Getter
    private final Map<ScanEngine, Boolean> scannerStateMap = new EnumMap<>(ScanEngine.class);
    private final Set<String> activeScannerProjectSet = ConcurrentHashMap.newKeySet();

    /**
     * Get the singleton instance of GlobalScannerController
     *
     * @return GlobalScannerController
     */
    public static GlobalScannerController getInstance() {
        return ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }

    /**
     * Wires the controller into the settings listener bus and seeds the in-memory scanner state
     * from the persisted {@link GlobalSettingsState}.
     */
    public GlobalScannerController() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        ApplicationManager.getApplication()
                .getMessageBus()
                .connect()
                .subscribe(SettingsListener.SETTINGS_APPLIED, this);
        this.updateScannerState(state);
    }

    /**
     * Updates the current realtime toggle values from the provided settings state into the
     * per-engine cache so callers can query enablement without re-reading settings.
     *
     * @param state current global settings snapshot
     */
    private void updateScannerState(GlobalSettingsState state) {
        scannerStateMap.put(ScanEngine.ASCA, state.isAscaRealtime());
        scannerStateMap.put(ScanEngine.OSS, state.isOssRealtime());
        scannerStateMap.put(ScanEngine.SECRETS, state.isSecretDetectionRealtime());
        scannerStateMap.put(ScanEngine.CONTAINERS, state.isContainersRealtime());
        scannerStateMap.put(ScanEngine.IAC, state.isIacRealtime());
    }

    /**
     * Reacts to global settings changes by refreshing the cached scanner state and pushing the
     * new configuration out to all open projects.
     */
    @Override
    public void settingsApplied() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        synchronized (this) {
            updateScannerState(state);
        }
        this.syncAll(state);
    }


    /**
     * Indicates whether the specified scanner type is globally enabled according to the most
     * recent settings snapshot. Also checks if MCP is enabled at tenant level.
     *
     * @param type scanner engine identifier
     * @return {@code true} if enabled globally and MCP is enabled; {@code false} otherwise
     */
    public synchronized boolean isScannerGloballyEnabled(ScanEngine type) {
        GlobalSettingsState state = GlobalSettingsState.getInstance();

        // If MCP is disabled at tenant level, scanners should be disabled
        if (!state.isMcpEnabled()) {
            return false;
        }

        // Return the scanner's individual state
        return scannerStateMap.getOrDefault(type, false);
    }


    /**
     * Checks whether the given project has already registered the specified scanner, using
     * a composite key derived from the project location hash.
     *
     * @param project IntelliJ project
     * @param type    scanner engine identifier
     * @return {@code true} if the project is currently registered; {@code false} otherwise
     */
    public boolean isRegistered(Project project, ScanEngine type) {
        return activeScannerProjectSet.contains(key(project, type));
    }


    /**
     * Builds a unique key per project/scanner pair based on the project's location hash
     * (stable even for same-named projects in different directories).
     *
     * @param project IntelliJ project
     * @param type    scanner engine identifier
     * @return unique string key for the pair
     */
    private static String key(Project project, ScanEngine type) {
        return project.getLocationHash() + "-" + type.name();
    }

    /**
     * Marks the specified project/scanner pair as registered so duplicate registrations
     * can be avoided.
     *
     * @param project IntelliJ project
     * @param type    scanner engine identifier
     */
    public void markRegistered(Project project, ScanEngine type) {
        activeScannerProjectSet.add(key(project, type));
    }


    /**
     * Removes the registration mark for the given project/scanner pair, typically after
     * de-registration or project disposal.
     *
     * @param project IntelliJ project
     * @param type    scanner engine identifier
     */
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

    /**
     * Checks if any scanner is enabled
     *
     * @return true if any scanner is enabled
     */
    public boolean checkAnyScannerEnabled() {
        return Arrays.stream(ScanEngine.values())
                .anyMatch(this::isScannerGloballyEnabled);
    }

    /**
     * Get the list of enabled scanners
     *
     * @return list of enabled scanners
     */
    public List<ScanEngine> getEnabledScanners() {
        return Arrays.stream(ScanEngine.values())
                .filter(this::isScannerGloballyEnabled)
                .collect(Collectors.toList());
    }
}
