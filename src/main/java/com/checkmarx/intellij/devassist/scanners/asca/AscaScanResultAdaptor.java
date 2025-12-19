package com.checkmarx.intellij.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter class for handling ASCA scan results and converting them into a standardized format
 * using the {@link com.checkmarx.intellij.devassist.common.ScanResult} interface.
 * This class wraps a {@link ScanResult} instance and provides methods to process and extract
 * meaningful scan issues based on ASCA findings detected in the files.
 */
public class AscaScanResultAdaptor implements com.checkmarx.intellij.devassist.common.ScanResult<ScanResult> {
    private static final Logger LOGGER = Utils.getLogger(AscaScanResultAdaptor.class);
    private final ScanResult ascaScanResult;
    private final String filePath;
    private final List<ScanIssue> scanIssues;

    /**
     * Constructs an instance of {@code AscaScanResultAdaptor} with the specified ASCA scan results.
     * This adapter allows conversion and processing of ASCA scan results into a standardized format.
     *
     * @param ascaScanResult the ASCA scan results to be wrapped by this adapter
     * @param filePath       the path of the file being scanned (needed for UI display)
     */
    public AscaScanResultAdaptor(ScanResult ascaScanResult, String filePath) {
        this.ascaScanResult = ascaScanResult;
        this.filePath = filePath;
        this.scanIssues = buildIssues();
    }

    /**
     * Retrieves the ASCA scan results wrapped by this adapter.
     *
     * @return the ASCA scan results instance containing the results of the ASCA scan
     */
    @Override
    public ScanResult getResults() {
        return ascaScanResult;
    }

    /**
     * Retrieves a list of scan issues discovered in the ASCA scan.
     * This method processes the scan details obtained from the scan results,
     * converts them into standardized scan issues, and returns the list.
     * If no scan details are found, an empty list is returned.
     *
     * @return a list of {@link ScanIssue} objects representing the ASCA findings, or an empty list if none are found
     */
    @Override
    public List<ScanIssue> getIssues() {
        return scanIssues;
    }

    /**
     * Builds a list of ScanIssue objects from the ASCA scan results.
     * Groups multiple vulnerabilities on the same line and sorts them by severity.
     *
     * @return a list of ScanIssue objects
     */
    private List<ScanIssue> buildIssues() {
        if (ascaScanResult == null || ascaScanResult.getScanDetails() == null) {
            LOGGER.debug("ASCA adaptor: No scan results or scan details available");
            return Collections.emptyList();
        }

        List<ScanDetail> scanDetails = ascaScanResult.getScanDetails();
        if (scanDetails.isEmpty()) {
            return Collections.emptyList();
        }

        // Group scan details by line number, then sort by severity precedence
        Map<Integer, List<ScanDetail>> groupedIssues = scanDetails.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        ScanDetail::getLine,
                        Collectors.collectingAndThen(Collectors.toList(), detailsList -> {
                            detailsList.sort(Comparator.comparingInt(detail ->
                                    SeverityLevel.fromValue(mapSeverity(detail.getSeverity())).getPrecedence()));
                            return detailsList;
                        })
                ));

        List<ScanIssue> issues = groupedIssues.values().stream()
                .map(this::createScanIssueForGroup)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LOGGER.debug("ASCA adaptor: Converted " + issues.size() + " grouped scan issues for file: " + filePath);
        return issues;
    }

    /**
     * Creates a ScanIssue from a group of ASCA scan details that are on the same line.
     * If multiple details are present, creates one ScanIssue with multiple vulnerabilities.
     *
     * @param ascaScanDetails the list of ASCA scan details for the same line (already sorted by severity)
     * @return a {@link ScanIssue} representing the ASCA finding(s), or {@code null} if conversion fails
     */
    private ScanIssue createScanIssueForGroup(List<ScanDetail> ascaScanDetails) {
        if (ascaScanDetails == null || ascaScanDetails.isEmpty()) {
            return null;
        }

        try {
            ScanIssue scanIssue = getScanIssue(ascaScanDetails);

            // Add all vulnerabilities to the scan issue
            for (int i = 0; i < ascaScanDetails.size(); i++) {
                ScanDetail detail = ascaScanDetails.get(i);
                String vulnerabilityId = (i == 0) ? scanIssue.getScanIssueId() : null;
                scanIssue.getVulnerabilities().add(createVulnerability(detail, vulnerabilityId));
            }

            // Add location information (use the first detail's line number)
            Location location = new Location();
            location.setLine(ascaScanDetails.get(0).getLine());
            scanIssue.getLocations().add(location);
            scanIssue.setProblematicLineNumber(location.getLine());

            LOGGER.debug("ASCA adaptor: Successfully created ScanIssue group with " +
                    ascaScanDetails.size() + " vulnerabilities on line " + location.getLine());
            return scanIssue;
        } catch (Exception e) {
            LOGGER.warn("ASCA adaptor: Failed to convert scan details group to ScanIssue", e);
            return null;
        }
    }

    /**
     * Creates a ScanIssue with appropriate title and basic properties from a group of ASCA scan details.
     *
     * @param ascaScanDetails the list of ASCA scan details (already sorted by severity)
     * @return a ScanIssue with basic properties set
     */
    private @NotNull ScanIssue getScanIssue(List<ScanDetail> ascaScanDetails) {
        ScanIssue scanIssue = new ScanIssue();
        ScanDetail firstDetail = ascaScanDetails.get(0); // Highest severity (already sorted)

        // Set title based on whether there are multiple issues on the same line
        if (ascaScanDetails.size() > 1) {
            scanIssue.setTitle(ascaScanDetails.size() + Constants.RealTimeConstants.MULTIPLE_ASCA_ISSUES);
        } else {
            scanIssue.setTitle(firstDetail.getRuleName());
        }

        // Use the first (highest severity) detail for primary issue properties
        scanIssue.setDescription(firstDetail.getDescription());
        scanIssue.setSeverity(mapSeverity(firstDetail.getSeverity()));
        scanIssue.setRemediationAdvise(firstDetail.getRemediationAdvise());
        scanIssue.setFilePath(this.filePath);
        scanIssue.setScanEngine(ScanEngine.ASCA);

        // Generate unique ID based on line, title, and description
        scanIssue.setScanIssueId(DevAssistUtils.generateUniqueId(
                firstDetail.getLine(),
                scanIssue.getTitle(),
                scanIssue.getDescription()
        ));

        return scanIssue;
    }

    /**
     * Creates a Vulnerability object from a ASCA scan detail.
     *
     * @param scanDetail the ASCA scan detail
     * @param overrideId optional vulnerability ID to use instead of generating one
     * @return a Vulnerability object
     */
    private Vulnerability createVulnerability(ScanDetail scanDetail, String overrideId) {
        Vulnerability vulnerability = new Vulnerability();

        // Generate or use provided vulnerability ID
        String vulnerabilityId = DevAssistUtils.generateUniqueId(
                scanDetail.getLine(),
                scanDetail.getRuleName(),
                scanDetail.getDescription()
        );
        if (overrideId != null && !overrideId.isBlank()) {
            vulnerabilityId = overrideId;
        }

        vulnerability.setVulnerabilityId(vulnerabilityId);
        vulnerability.setCve(scanDetail.getRuleName());
        vulnerability.setDescription(scanDetail.getDescription());
        vulnerability.setSeverity(mapSeverity(scanDetail.getSeverity()));
        vulnerability.setRemediationAdvise(scanDetail.getRemediationAdvise());
        vulnerability.setTitle(scanDetail.getRuleName());

        return vulnerability;
    }

    /**
     * Maps ASCA severity levels to standardized severity strings that match the icon system.
     *
     * @param ascaSeverity the ASCA severity level
     * @return standardized severity string (Capitalized format: "Critical", "High", "Medium", "Low")
     */
    private String mapSeverity(String ascaSeverity) {
        if (ascaSeverity == null) {
            return SeverityLevel.MEDIUM.getSeverity();
        } else if (ascaSeverity.equalsIgnoreCase("info")) {
            return SeverityLevel.LOW.getSeverity(); // Map info to Low for icon display
        }
        // retrieve the severity from enum to ensure valid severity levels
        SeverityLevel severityLevel = SeverityLevel.fromValue(ascaSeverity.toLowerCase()); // Validate severity
        return severityLevel == SeverityLevel.UNKNOWN
                ? SeverityLevel.MEDIUM.getSeverity()
                : severityLevel.getSeverity();
    }
}
