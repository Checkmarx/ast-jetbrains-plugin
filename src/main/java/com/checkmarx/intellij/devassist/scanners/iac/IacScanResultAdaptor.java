package com.checkmarx.intellij.devassist.scanners.iac;

import com.checkmarx.ast.iacrealtime.IacRealtimeResults;
import com.checkmarx.ast.realtime.RealtimeLocation;
import com.checkmarx.intellij.devassist.common.ScanResult;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.util.SeverityLevel;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter class for handling IAC scan results and converting them into a standardized format
 * using the {@link ScanResult} interface.
 * This class wraps an {@code IacRealtimeResults} instance and provides methods to process and extract
 * meaningful scan issues based on vulnerabilities detected in the images.
 */

public class IacScanResultAdaptor implements ScanResult<IacRealtimeResults> {

    private final IacRealtimeResults iacRealtimeResults;
    private final String fileType;
    private final String filePath;
    private final List<ScanIssue> scanIssues;

    public IacScanResultAdaptor(IacRealtimeResults iacRealtimeResults, String fileType, String filePath) {
        this.iacRealtimeResults = iacRealtimeResults;
        this.fileType = fileType;
        this.filePath = filePath;
        this.scanIssues = buildIssues();
    }


    /**
     * Represents an entry containing an issue and its associated real-time location
     * within an IaC (Infrastructure as Code) scan result.
     *
     * This class is used internally to encapsulate information about specific issues
     * discovered during the scanning process alongside their corresponding locations in the source.
     */
    private static final class IssueLocationEntry {
        final IacRealtimeResults.Issue issue;
        final RealtimeLocation location;

        IssueLocationEntry(IacRealtimeResults.Issue issue, RealtimeLocation location) {
            this.issue = issue;
            this.location = location;
        }
    }



    /**
     * Retrieves the real-time results of the Infrastructure as Code (IaC) scan.
     *
     * @return an {@code IacRealtimeResults} object representing the real-time scan results of the IaC analysis.
     */
    @Override
    public IacRealtimeResults getResults() {
        return this.iacRealtimeResults;
    }


    /**
     * Retrieves a list of scan issues discovered in the IaC real-time scan.
     *
     * @return a list of {@code ScanIssue} objects representing the vulnerabilities found during the scan,
     */
    @Override
    public List<ScanIssue> getIssues() {
        return scanIssues;
    }

    /**
     * Builds a list of {@code ScanIssue} objects by processing and grouping issues from the
     * Infrastructure as Code (IaC) real-time scan results.
     *
     * This method filters and organizes the issues based on their respective locations
     * and severity levels. It ensures that the issues are grouped by unique identifiers
     * generated from the file path, line number, and character index range. The highest
     * severity issue within each group is prioritized for inclusion in the result.
     *
     * @return a list of {@code ScanIssue} objects representing the identified vulnerabilities;
     * returns an empty list if no issues are present or valid in the scan results.
     */
    private List<ScanIssue> buildIssues() {
        List<IacRealtimeResults.Issue> iacIssuesList = Objects.nonNull(getResults()) ? getResults().getResults() : null;
        if (Objects.isNull(iacIssuesList) || iacIssuesList.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, List<IssueLocationEntry>> groupedIssues = iacIssuesList.stream()
                .filter(Objects::nonNull)
                .flatMap(issue -> issue.getLocations().stream()
                        .filter(Objects::nonNull)
                        .map(location -> new IssueLocationEntry(issue, location)))
                .collect(Collectors.groupingBy(this::getGroupingKey, Collectors.collectingAndThen(Collectors.toList(), this::sortEntriesBySeverity)));

        return groupedIssues.values().stream().map(this::createScanIssue).collect(Collectors.toList());
    }

    /**
     * Generates a unique grouping key for the given {@code IssueLocationEntry}.
     * The key is constructed using the file path of the issue, along with the line number,
     * start index, and end index of its associated location.
     *
     * @param entry the {@code IssueLocationEntry} object containing an issue and its associated
     *              real-time location. Must not be null.
     * @return a {@code String} representing the unique grouping key for the provided issue location.
     */
    private  String getGroupingKey(IssueLocationEntry entry) {
        RealtimeLocation loc = entry.location;
        return entry.issue.getFilePath() + ":" + loc.getLine() + ":" + loc.getStartIndex() + ":" + loc.getEndIndex();
    }

    /**
     * Sorts a list of {@code IssueLocationEntry} objects based on the severity of the issues they represent.
     * The severity is determined by mapping the issue's severity string value to a {@code SeverityLevel}
     * and using its precedence for ordering.
     *
     * @param entries the list of {@code IssueLocationEntry} objects to be sorted, where each entry
     *                contains an issue and its associated real-time location. Must not be null.
     * @return a sorted list of {@code IssueLocationEntry} objects, ordered by the severity of the issues
     *         in ascending order of precedence.
     */
    private List<IssueLocationEntry> sortEntriesBySeverity(List<IssueLocationEntry> entries) {
        entries.sort(Comparator.comparingInt(e -> SeverityLevel.fromValue(e.issue.getSeverity()).getPrecedence()));
        return entries;
    }

    /**
     * Creates a {@code ScanIssue} object representing a collection of vulnerabilities
     * and locations associated with the given list of issue location entries.
     *
     * This method processes the provided list of {@code IssueLocationEntry}, generates
     * corresponding vulnerabilities, assigns a unique vulnerability ID for the primary issue,
     * and creates a location entry for the first location in the list.
     *
     * @param iacScanIssue a list of {@code IssueLocationEntry} objects representing issues
     *                     with their associated locations in the IaC scan results.
     * @return a {@code ScanIssue} object containing aggregated information about vulnerabilities
     *         and their associated locations.
     */
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

    /**
     * Constructs a ScanIssue object based on a list of IssueLocationEntry objects obtained
     * from an Infrastructure as Code (IaC) scan result.
     *
     * This method processes the list of IssueLocationEntry, aggregates information from
     * the first entry, and assigns appropriate attributes to the ScanIssue object.
     * If the list contains more than one entry, the title of the ScanIssue indicates
     * multiple issues; otherwise, it uses the title of the first issue in the list.
     *
     * @param iacScanIssue a list of IssueLocationEntry objects representing issues
     *                     and their associated locations in the IaC scan results.
     *                     Must not be null or empty.
     *
     * @return a ScanIssue object containing aggregated details about the issues in
     *         the specified IssueLocationEntry list.
     */
    private @NotNull ScanIssue getScanIssue(List<IssueLocationEntry> iacScanIssue) {
        ScanIssue scanIssue = new ScanIssue();
        if (iacScanIssue.size() > 1) {
            scanIssue.setTitle(iacScanIssue.size() + DevAssistConstants.MULTIPLE_IAC_ISSUES);
        } else {
            scanIssue.setTitle(iacScanIssue.get(0).issue.getTitle());
        }
        scanIssue.setDescription(iacScanIssue.get(0).issue.getDescription());
        scanIssue.setSeverity(iacScanIssue.get(0).issue.getSeverity());
        scanIssue.setFilePath(this.filePath);
        scanIssue.setScanEngine(ScanEngine.IAC);
        scanIssue.setFileType(this.fileType);
        scanIssue.setSimilarityId(iacScanIssue.get(0).issue.getSimilarityId());
        scanIssue.setScanIssueId(getUniqueId(iacScanIssue.get(0).issue));
        return scanIssue;
    }

    /**
     * Creates a {@code Vulnerability} object based on the information provided in the given {@code IacRealtimeResults.Issue}.
     *
     * This method constructs a vulnerability by populating its attributes, such as ID, description, severity,
     * actual and expected values, and title, using the details of the specified issue.
     * If an override ID is provided and is not blank, it will replace the generated vulnerability ID.
     *
     * @param vulnerabilityObj the {@code IacRealtimeResults.Issue} object containing the details of the vulnerability.
     *                         Must not be null.
     * @param overrideId       an optional string to override the generated vulnerability ID. Can be null or blank.
     *
     * @return a {@code Vulnerability} object populated with the relevant details from the provided issue.
     */
    private Vulnerability createVulnerability(IacRealtimeResults.Issue vulnerabilityObj, String overrideId) {
        Vulnerability vulnerability = new Vulnerability();
        vulnerability.setVulnerabilityId(getUniqueId(vulnerabilityObj));
        if (overrideId != null && !overrideId.isBlank()) {
            vulnerability.setVulnerabilityId(overrideId);
        }
        vulnerability.setDescription(vulnerabilityObj.getDescription());
        vulnerability.setSeverity(vulnerabilityObj.getSeverity());
        vulnerability.setActualValue(vulnerabilityObj.getActualValue());
        vulnerability.setExpectedValue(vulnerabilityObj.getExpectedValue());
        vulnerability.setTitle(vulnerabilityObj.getTitle());
        vulnerability.setSimilarityId(vulnerabilityObj.getSimilarityId());
        return vulnerability;
    }


    private Location createLocation(RealtimeLocation location) {
        return new Location(getLine(location), location.getStartIndex(), location.getEndIndex());
    }

    private int getLine(RealtimeLocation location) {
        return location.getLine() + 1;
    }

    /**
     * Generates a unique ID for the given scan issue.
     */
    private String getUniqueId(IacRealtimeResults.Issue scanIssue) {
        int line = (Objects.nonNull(scanIssue.getLocations()) && !scanIssue.getLocations().isEmpty())
                ? scanIssue.getLocations().get(0).getLine() : 0;
        return DevAssistUtils.generateUniqueId(line, scanIssue.getTitle(), scanIssue.getSimilarityId());
    }
}
