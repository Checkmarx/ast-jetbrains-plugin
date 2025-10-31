package com.checkmarx.intellij.realtimeScanners.basescanner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;

public interface ScannerCommand extends Disposable {
    void register(Project project);
    void dispose();

}
