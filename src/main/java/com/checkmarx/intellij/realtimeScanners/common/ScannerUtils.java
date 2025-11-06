package com.checkmarx.intellij.realtimeScanners.common;

import com.checkmarx.intellij.realtimeScanners.configuration.GlobalScannerController;
import com.intellij.openapi.application.ApplicationManager;

public class ScannerUtils {

    private static GlobalScannerController global() {
        return ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }

    public static boolean isScannerActive(String engineName) {
        if (engineName == null) return false;
        try {
            ScannerKind kind = ScannerKind.valueOf(engineName.toUpperCase());
            return global().isScannerGloballyEnabled(kind);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
