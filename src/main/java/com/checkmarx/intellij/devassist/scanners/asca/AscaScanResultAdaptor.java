package com.checkmarx.intellij.devassist.scanners.asca;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.asca.ScanDetail;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    /**
     * Constructs an instance of {@code AscaScanResultAdaptor} with the specified ASCA scan results.
     * This adapter allows conversion and processing of ASCA scan results into a standardized format.
     *
     * @param ascaScanResult the ASCA scan results to be wrapped by this adapter
     * @param filePath the path of the file being scanned (needed for UI display)
     */
    public AscaScanResultAdaptor(ScanResult ascaScanResult, String filePath) {
        this.ascaScanResult = ascaScanResult;
        this.filePath = filePath;
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
        if (ascaScanResult == null || ascaScanResult.getScanDetails() == null) {
            LOGGER.debug("ASCA adaptor: No scan results or scan details available");
            return Collections.emptyList();
        }

        List<ScanIssue> issues = ascaScanResult.getScanDetails().stream()
                .filter(Objects::nonNull)
                .map(this::convertToScanIssue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LOGGER.debug("ASCA adaptor: Converted " + issues.size() + " scan details to issues for file: " + filePath);
        return issues;
    }

    /**
     * Converts a single ASCA scan detail into a standardized {@link ScanIssue}.
     *
     * @param scanDetail the ASCA scan detail to convert
     * @return a {@link ScanIssue} representing the ASCA finding, or {@code null} if conversion fails
     */
    private ScanIssue convertToScanIssue(ScanDetail scanDetail) {
        try {
            String originalSeverity = scanDetail.getSeverity();
            String mappedSeverity = mapSeverity(originalSeverity);

            LOGGER.debug("ASCA adaptor: Converting scan detail - Rule: " + scanDetail.getRuleName() +
                        ", Original Severity: " + originalSeverity + ", Mapped Severity: " + mappedSeverity +
                        ", Line: " + scanDetail.getLine());

            ScanIssue issue = new ScanIssue();

            // Set basic issue information
            issue.setScanEngine(ScanEngine.ASCA);
            issue.setTitle(scanDetail.getRuleName());
            issue.setDescription(scanDetail.getDescription());
            issue.setSeverity(mappedSeverity);
            issue.setRemediationAdvise(scanDetail.getRemediationAdvise());
            issue.setFilePath(this.filePath);

            // Create vulnerability with ASCA-specific information - properly populate all fields
            Vulnerability vulnerability = new Vulnerability();
            vulnerability.setCve( scanDetail.getRuleName());
            vulnerability.setId(scanDetail.getRuleName());
            vulnerability.setDescription(scanDetail.getDescription());
            vulnerability.setSeverity(mappedSeverity);
            vulnerability.setRemediationAdvise(scanDetail.getRemediationAdvise());

            issue.getVulnerabilities().add(vulnerability);

            // Create location information
            Location location = new Location();
            location.setLine(scanDetail.getLine());
            issue.getLocations().add(location);

            LOGGER.debug("ASCA adaptor: Successfully created ScanIssue for " + scanDetail.getRuleName() +
                        " with severity " + mappedSeverity);
            return issue;
        } catch (Exception e) {
            LOGGER.warn("ASCA adaptor: Failed to convert scan detail to ScanIssue", e);
            return null;
        }
    }

    /**
     * Maps ASCA severity levels to standardized severity strings that match the icon system.
     *
     * @param ascaSeverity the ASCA severity level
     * @return standardized severity string (Capitalized format: "Critical", "High", "Medium", "Low")
     */
    private String mapSeverity(String ascaSeverity) {
        if (ascaSeverity == null) {
            return "Medium";
        }

        String severity = ascaSeverity.toLowerCase();
        switch (severity) {
            case "critical":
                return "Critical";
            case "high":
                return "High";
            case "medium":
                return "Medium";
            case "low":
                return "Low";
            case "info":
                return "Low"; // Map info to Low for icon display
            default:
                return "Medium";
        }
    }
}
