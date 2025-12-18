package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    public  IacScanResultAdaptor(IacRealtimeResults iacRealtimeResults) {
        this.iacRealtimeResults = iacRealtimeResults;
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
                }));

      return groupedIssues.values().stream().map(this::createScanIssue).collect(Collectors.toList());
      // return iacIssuesList.stream().map(this::createScanIssue).collect(Collectors.toList());
    }

    private ScanIssue createScanIssue(List<IssueLocationEntry> iacScanIssue) {
        ScanIssue scanIssue = getScanIssue(iacScanIssue);
        for (IssueLocationEntry entry : iacScanIssue) {
            scanIssue.getVulnerabilities().add(createVulnerability(entry.issue));
        }
        scanIssue.getLocations().add(createLocation(iacScanIssue.get(0).location));
        return scanIssue;
    }

    private static @NotNull ScanIssue getScanIssue(List<IssueLocationEntry> iacScanIssue) {
        ScanIssue scanIssue = new ScanIssue();
        if(iacScanIssue.size()>1){
            scanIssue.setTitle(iacScanIssue.size() +" IAC issues detected on this line");
        }
        else{
            scanIssue.setTitle(iacScanIssue.get(0).issue.getTitle());
        }
        scanIssue.setDescription(iacScanIssue.get(0).issue.getDescription());
        scanIssue.setSeverity(iacScanIssue.get(0).issue.getSeverity());
        scanIssue.setFilePath(iacScanIssue.get(0).issue.getFilePath());
        scanIssue.setScanEngine(ScanEngine.IAC);
        return scanIssue;
    }

    private Vulnerability createVulnerability(IacRealtimeResults.Issue vulnerabilityObj) {
        return new Vulnerability("",vulnerabilityObj.getDescription(),vulnerabilityObj.getSeverity(),"","",vulnerabilityObj.getActualValue(),vulnerabilityObj.getExpectedValue(),vulnerabilityObj.getTitle());
    }


    private Location createLocation(RealtimeLocation location) {
        return new Location(getLine(location), location.getStartIndex(), location.getEndIndex());
    }
    private int getLine(RealtimeLocation location) {
        return location.getLine() + 1;
    }

}
