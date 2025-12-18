package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.util.SeverityLevel;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class IacScanResultAdaptor implements ScanResult<IacRealtimeResults> {


    private static final class IssueLocationEntry {
        final IacRealtimeResults.Issue issue;
        final RealtimeLocation location;

        IssueLocationEntry(IacRealtimeResults.Issue issue, RealtimeLocation location) {
            this.issue = issue;
            this.location = location;
        }
    }


    private final IacRealtimeResults iacRealtimeResults;
    private final String fileType;

    public  IacScanResultAdaptor(IacRealtimeResults iacRealtimeResults, String fileType) {
        this.iacRealtimeResults = iacRealtimeResults;
        this.fileType = fileType;
    }

    @Override
    public IacRealtimeResults getResults() {
        return this.iacRealtimeResults;
    }


    @Override
    public List<ScanIssue> getIssues() {
       List<IacRealtimeResults.Issue>iacIssuesList=  Objects.nonNull(getResults())? getResults().getResults():null;
       if(Objects.isNull(iacIssuesList) || iacIssuesList.isEmpty()){
           return Collections.emptyList();
       }
        Map<String, List<IssueLocationEntry>> groupedIssues = iacIssuesList.stream()
                .filter(Objects::nonNull)
                .flatMap(issue -> issue.getLocations().stream()
                        .filter(Objects::nonNull)
                        .map(location -> new IssueLocationEntry(issue, location)))
                .collect(Collectors.groupingBy(entry -> {
                    RealtimeLocation loc = entry.location;
                    return entry.issue.getFilePath() + ":" + loc.getLine() + ":" + loc.getStartIndex() + ":" + loc.getEndIndex();
                },Collectors.collectingAndThen(Collectors.toList(),entries->{
                    entries.sort(Comparator.comparingInt(e-> SeverityLevel.fromValue(e.issue.getSeverity()).getPrecedence()));
                    return entries;
                })));

      return groupedIssues.values().stream().map(this::createScanIssue).collect(Collectors.toList());

    }

    private ScanIssue createScanIssue(List<IssueLocationEntry> iacScanIssue) {
        ScanIssue scanIssue = getScanIssue(iacScanIssue);
        for (int i = 0; i < iacScanIssue.size(); i++) {
            IssueLocationEntry entry = iacScanIssue.get(i);
            String vulnerabilityId = (i == 0) ? scanIssue.getScanIssueId() : null;
            scanIssue.getVulnerabilities().add(createVulnerability(entry.issue, vulnerabilityId));
        }
        scanIssue.getLocations().add(createLocation(iacScanIssue.get(0).location));
        return scanIssue;
    }

    private @NotNull ScanIssue getScanIssue(List<IssueLocationEntry> iacScanIssue) {
        ScanIssue scanIssue = new ScanIssue();
        if(iacScanIssue.size()>1){
            scanIssue.setTitle(iacScanIssue.size() + Constants.RealTimeConstants.MULTIPLE_IAC_ISSUES);
        }
        else{
            scanIssue.setTitle(iacScanIssue.get(0).issue.getTitle());
        }
        scanIssue.setDescription(iacScanIssue.get(0).issue.getDescription());
        scanIssue.setSeverity(iacScanIssue.get(0).issue.getSeverity());
        scanIssue.setFilePath(iacScanIssue.get(0).issue.getFilePath());
        scanIssue.setScanEngine(ScanEngine.IAC);
        scanIssue.setFileType(this.fileType);
        return scanIssue;
    }

    private Vulnerability createVulnerability(IacRealtimeResults.Issue vulnerabilityObj, String overrideId) {
        Vulnerability vulnerability = new Vulnerability();
        if (overrideId != null && !overrideId.isBlank()) {
            vulnerability.setId(overrideId);
        }
        vulnerability.setDescription(vulnerabilityObj.getDescription());
        vulnerability.setSeverity(vulnerabilityObj.getSeverity());
        vulnerability.setActualValue(vulnerabilityObj.getActualValue());
        vulnerability.setExpectedValue(vulnerabilityObj.getExpectedValue());
        vulnerability.setTitle(vulnerabilityObj.getTitle());
        return vulnerability;
    }


    private Location createLocation(RealtimeLocation location) {
        return new Location(getLine(location), location.getStartIndex(), location.getEndIndex());
    }
    private int getLine(RealtimeLocation location) {
        return location.getLine() + 1;
    }

}
