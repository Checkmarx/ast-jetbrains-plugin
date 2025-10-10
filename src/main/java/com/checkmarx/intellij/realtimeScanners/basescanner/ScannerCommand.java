package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.intellij.openapi.Disposable;

public interface ScannerCommand extends Disposable {
    void register();
    void dispose();
}
