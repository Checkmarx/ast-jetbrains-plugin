package com.checkmarx.intellij.realtimeScanners.common;

import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;

import java.util.Collections;
import java.util.List;

public interface ScanResult<T> {
    T getResults();
    default List<OssRealtimeScanPackage> getPackages() {
        return Collections.emptyList();
    }
}
