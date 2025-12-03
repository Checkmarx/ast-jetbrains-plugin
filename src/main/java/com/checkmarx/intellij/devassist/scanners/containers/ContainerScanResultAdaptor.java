package com.checkmarx.intellij.devassist.scanners.containers;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeImage;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeResults;
import com.checkmarx.ast.containersrealtime.ContainersRealtimeVulnerability;
import com.checkmarx.ast.ossrealtime.OssRealtimeScanPackage;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.inspection.RealtimeInspection;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ContainerScanResultAdaptor implements ScanResult<ContainersRealtimeResults> {

    private final ContainersRealtimeResults containersRealtimeResults;
    private static final Logger LOGGER = Utils.getLogger(ContainerScanResultAdaptor.class);

    public ContainerScanResultAdaptor(ContainersRealtimeResults containersRealtimeResults) {
        this.containersRealtimeResults = containersRealtimeResults;
    }

    @Override
    public ContainersRealtimeResults getResults() {
        return containersRealtimeResults;
    }

    @Override
    public List<ScanIssue> getIssues() {
        List<ContainersRealtimeImage> images = Objects.nonNull(getResults()) ? getResults().getImages() : null;
        if (Objects.isNull(images) || images.isEmpty()) {
            return Collections.emptyList();
        }
        return images.stream()
                .map(this::createScanIssue)
                .collect(Collectors.toList());

    }

    private ScanIssue createScanIssue(ContainersRealtimeImage containersImageObj) {
        ScanIssue scanIssue = new ScanIssue();
        scanIssue.setScanEngine(ScanEngine.CONTAINERS);
        scanIssue.setTitle(containersImageObj.getImageName());
        scanIssue.setImageTag(containersImageObj.getImageTag());
        scanIssue.setSeverity(containersImageObj.getStatus());

        if (!Objects.isNull(containersImageObj.getLocations()) && !containersImageObj.getLocations().isEmpty()) {
            containersImageObj.getLocations().forEach(location -> scanIssue.getLocations().add(createLocation(location)));
        }

        if (!Objects.isNull(containersImageObj.getVulnerabilities()) && !containersImageObj.getVulnerabilities().isEmpty()) {
            containersImageObj.getVulnerabilities().forEach(vulnerability -> scanIssue.getVulnerabilities().add(createVulnerability(vulnerability)));
        }
        return scanIssue;
    }

    private Vulnerability createVulnerability(ContainersRealtimeVulnerability vulnerabilityObj) {
        return new Vulnerability(vulnerabilityObj.getCve(), this.getDescription(vulnerabilityObj.getSeverity()), vulnerabilityObj.getSeverity(), "", "");
    }

    private String getDescription(String severity){
        switch (severity){
            case (Constants.MALICIOUS_SEVERITY):
                return "Malicious-risk container image";

            case (Constants.CRITICAL_SEVERITY):
                return "Critical-risk container image";

            case (Constants.HIGH_SEVERITY):
                return "High-risk container image";

            case (Constants.MEDIUM_SEVERITY):
                return "Medium-risk container image";

            case (Constants.LOW_SEVERITY):
                return "Low-risk container image";

            default:
                return severity;
        }


    }

    private Location createLocation(RealtimeLocation location) {
        return new Location(getLine(location), location.getStartIndex(), location.getEndIndex());
    }

    private int getLine(RealtimeLocation location) {
        return location.getLine() + 1;
    }


}
