package com.checkmarx.intellij.devassist.scanners.oss;

import com.checkmarx.ast.ossrealtime.OssRealtimeResults;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class OssScanResultAdaptor implements ScanResult<OssRealtimeResults> {
    private final OssRealtimeResults ossRealtimeResults;

    public OssScanResultAdaptor(OssRealtimeResults ossRealtimeResults) {
        this.ossRealtimeResults = ossRealtimeResults;
    }

    @Override
    public OssRealtimeResults getResults() {
        return ossRealtimeResults;
    }

    @Override
    public List<OssRealtimeScanPackage> getPackages() {
        return ossRealtimeResults.getPackages();
    }
    @Override
    public List<ScanIssue> getIssues() {
        List<OssRealtimeScanPackage> packages = getResults().getPackages();
        if (Objects.isNull(packages) || packages.isEmpty()) {
            return Collections.emptyList();
        }
        return packages.stream()
                .map(this::createScanIssue)
                .collect(Collectors.toList());
    }

    private ScanIssue createScanIssue(OssRealtimeScanPackage packageObj) {
        ScanIssue problem = new ScanIssue();

        List<RealtimeLocation> locations = packageObj.getLocations();
        if (locations != null && !locations.isEmpty()) {
            locations.forEach(location -> problem.getLocations().add(createLocation(location)));
        }
        problem.setTitle(packageObj.getPackageName());
        problem.setPackageVersion(packageObj.getPackageVersion());
        problem.setScanEngine(Constants.RealTimeConstants.OSS_REALTIME_SCANNER_ENGINE_NAME);
        problem.setSeverity(packageObj.getStatus());

        return problem;
    }

    private Location createLocation(RealtimeLocation location){
        return new Location(location.getLine() + 1, location.getStartIndex(), location.getEndIndex());
    }
}
