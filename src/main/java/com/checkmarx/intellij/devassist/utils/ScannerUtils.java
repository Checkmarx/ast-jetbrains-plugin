package com.checkmarx.intellij.devassist.utils;

import com.checkmarx.intellij.devassist.common.ScannerType;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.openapi.application.ApplicationManager;

public class ScannerUtils {

    private static GlobalScannerController global() {
        return ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }

    public static boolean isScannerActive(String engineName) {
        if (engineName == null) return false;
        try {
            if(GlobalSettingsState.getInstance().isAuthenticated()){
                ScannerType kind = ScannerType.valueOf(engineName.toUpperCase());
                return global().isScannerGloballyEnabled(kind);
            }
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return false;
    }
}
