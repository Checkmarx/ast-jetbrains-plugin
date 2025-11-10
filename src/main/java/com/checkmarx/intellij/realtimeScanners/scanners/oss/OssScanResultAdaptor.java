package com.checkmarx.intellij.realtimeScanners.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.intellij.realtimeScanners.common.ScanResult;
import java.util.List;

public class OssScanResultAdaptor implements ScanResult<OssRealtimeResults> {
    private final OssRealtimeResults resultDelegate;

    public OssScanResultAdaptor(OssRealtimeResults delegate){
        this.resultDelegate=delegate;
    }

    @Override
    public OssRealtimeResults getResults() {
        return resultDelegate;
    }
    @Override
    public List<OssRealtimeScanPackage> getPackages() {
        return resultDelegate.getPackages();
    }
}
