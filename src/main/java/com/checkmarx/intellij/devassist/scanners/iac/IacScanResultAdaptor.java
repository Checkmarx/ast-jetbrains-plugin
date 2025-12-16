package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.ast.containersrealtime.ContainersRealtimeVulnerability;
import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.ScanEngine;

import java.security.Key;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class IacScanResultAdaptor implements ScanResult<IacRealtimeResults> {

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
      Map<String,List<IacRealtimeResults.Issue>> groupedIssues= iacIssuesList.stream().filter(Objects::nonNull)
               .filter(issue -> Objects.nonNull(issue.getLocations()) && !issue.getLocations().isEmpty())
               .collect(Collectors.groupingBy(issue ->{
                   RealtimeLocation location = issue.getLocations().get(0);
                   String filePath= issue.getFilePath();
                   return filePath+":"+location;
               }));


      return groupedIssues.values().stream().map(this::createScanIssue).collect(Collectors.toList());

      // return iacIssuesList.stream().map(this::createScanIssue).collect(Collectors.toList());

    }

    private ScanIssue createScanIssue(List<IacRealtimeResults.Issue> iacScanIssue) {
        ScanIssue scanIssue = new ScanIssue();
        if(iacScanIssue.size()>1){
            for (IacRealtimeResults.Issue issue : iacScanIssue) {
                scanIssue.getVulnerabilities().add(createVulnerability(issue));
            }
            scanIssue.setTitle(iacScanIssue.size() +" IAC issues detected on this line");
            scanIssue.setDescription(iacScanIssue.get(0).getDescription());
            scanIssue.setSeverity(iacScanIssue.get(0).getSeverity());
            scanIssue.setFilePath(iacScanIssue.get(0).getFilePath());
            scanIssue.setScanEngine(ScanEngine.IAC);
        }
        else{
            scanIssue.setTitle(iacScanIssue.get(0).getTitle());
            scanIssue.setDescription(iacScanIssue.get(0).getDescription());
            scanIssue.setSeverity(iacScanIssue.get(0).getSeverity());
            scanIssue.setFilePath(iacScanIssue.get(0).getFilePath());
            scanIssue.setScanEngine(ScanEngine.IAC);
        }

        if(Objects.nonNull(iacScanIssue.get(0).getLocations()) && !iacScanIssue.get(0).getLocations().isEmpty()){
            iacScanIssue.get(0).getLocations().forEach(location -> scanIssue.getLocations().add(createLocation(location)));
        }
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
