package com.checkmarx.intellij.devassist.ui;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.util.SeverityLevel;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.Utils.escapeHtml;

/**
 * This class is responsible for handling and formatting descriptions of scan issues
 * including their severity, associated vulnerabilities, and remediation guidance.
 * It provides various utility methods to construct and format messages for
 * different types of issues.
 */
public class ProblemDescription {

    private static final int MAX_LINE_LENGTH = 150;
    private final Map<String, String> severityConfig = new LinkedHashMap<>();

    private static final String DIV = "<div>";
    private static final String DIV_BR = "</div><br>";

    //Tooltip description constants
    public static final String PACKAGE = "Package";
    public static final String RISK_PACKAGE = "risk package";
    public static final String SEVERITY_PACKAGE = "Severity Package";
    public static final String PACKAGE_DETECTED = "package detected";

    public ProblemDescription(){
        initIconsMap();
    }

    /**
     * Initializes the mapping from severity levels to severity-specific icons.
     */
    private void initIconsMap() {
        severityConfig.put(SeverityLevel.CRITICAL.getSeverity(), getImage(getIconPath(Constants.ImagePaths.CRITICAL_SEVERITY_PNG)));
        severityConfig.put(SeverityLevel.HIGH.getSeverity(), getImage(getIconPath(Constants.ImagePaths.HIGH_SEVERITY_PNG)));
        severityConfig.put(SeverityLevel.MEDIUM.getSeverity(), getImage(getIconPath(Constants.ImagePaths.MEDIUM_SEVERITY_PNG)));
        severityConfig.put(SeverityLevel.LOW.getSeverity(), getImage(getIconPath(Constants.ImagePaths.LOW_SEVERITY_PNG)));
        severityConfig.put(PACKAGE, getImage(getIconPath(Constants.ImagePaths.PACKAGE_PNG)));
    }

    public String formatDescription(ScanIssue scanIssue) {

        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append("<html><body><div style='display:flex;align-items:center;gap:4px;'>");
        descBuilder.append("<div><img src='").append(getIconPath(Constants.ImagePaths.DEV_ASSIST_PNG)).append("'/></div><br>");

        switch (scanIssue.getScanEngine()) {
            case OSS:
                buildOSSDescription(descBuilder, scanIssue);
                break;
            case ASCA:
                buildASCADescription(descBuilder, scanIssue);
                break;
            default:
                buildDefaultDescription(descBuilder, scanIssue);
        }
        descBuilder.append("</div></body></html>");
        return descBuilder.toString();
    }

    /**
     * Builds the OSS description for the provided scan issue and appends it to the given StringBuilder.
     * This method incorporates severity-specific formatting, including handling for malicious packages,
     * and assembles the description with the package header and vulnerability details.
     *
     * @param descBuilder the StringBuilder to which the formatted OSS description will be appended
     * @param scanIssue   the ScanIssue object containing information about the scanned issue,
     *                    including its severity, vulnerabilities, and related details
     */
    private void buildOSSDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        if (scanIssue.getSeverity().equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity())) {
            buildMaliciousPackageMessage(descBuilder, scanIssue);
            return;
        }
        buildPackageHeader(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    /**
     * Builds the ASCA description for the provided scan issue and appends it to the given StringBuilder.
     * This method formats details about the scan issue, including its title, remediation advice,
     * and scanning engine information.
     *
     * @param descBuilder the StringBuilder to which the formatted ASCA description will be appended
     * @param scanIssue   the ScanIssue object containing details about the issue, such as its title,
     *                    remediation advice, and the scanning engine responsible for detecting the issue
     */
    private void buildASCADescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append(DIV).append(getIconBasedOnSeverity(scanIssue.getSeverity()))
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("</b> - ")
                .append(wrapTextAtWord(escapeHtml(scanIssue.getRemediationAdvise()))).append("<br>")
                .append("<font color='gray'>").append(scanIssue.getScanEngine().name()).append("</font></div><br><hr>");
    }

    String getIconBasedOnSeverity(String severity) {
        return severityConfig.getOrDefault(severity, "");
    }

    /**
     * Builds the default description for a scan issue and appends it to the provided StringBuilder.
     * This method formats basic details about the scan issue, including its title and description.
     *
     * @param descBuilder the StringBuilder to which the formatted default description will be appended
     * @param scanIssue   the ScanIssue object containing details about the issue such as title and description
     */
    private void buildDefaultDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<div><b>").append(scanIssue.getTitle()).append("</b> -").append(scanIssue.getDescription());
    }

    /**
     * Builds the package header section of a description for a scan issue and appends it to the provided StringBuilder.
     * This method formats information about the scan issue's severity, title, and package version,
     * and includes an associated image icon representing the issue.
     *
     * @param descBuilder the StringBuilder to which the formatted package header information will be appended
     * @param scanIssue   the ScanIssue object containing details about the issue such as severity, title, and package version
     */
    private void buildPackageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append(DIV).append(scanIssue.getSeverity()).append("-").append(RISK_PACKAGE)
                .append(":  ").append(scanIssue.getTitle()).append("@").append(scanIssue.getPackageVersion())
                .append(" - <font color='gray'>").append(scanIssue.getScanEngine().name()).append("</font></div><br>");
        descBuilder.append(DIV).append(severityConfig.get(PACKAGE))
                .append("<b>").append(scanIssue.getTitle()).append("@").append(scanIssue.getPackageVersion()).append("</b>")
                .append(" - ").append(scanIssue.getSeverity()).append(" ").append(SEVERITY_PACKAGE).append("</div><br><br>");
    }

    /**
     * Builds a malicious package message and appends it to the provided StringBuilder.
     * This method formats details about a detected malicious package based on its
     * severity, title, and package version, and includes a corresponding icon representing
     * the severity of the issue.
     *
     * @param descBuilder the StringBuilder to which the formatted malicious package message will be appended
     * @param scanIssue   the ScanIssue object containing details about the malicious package, such as its severity,
     *                    title, and package version
     */
    private void buildMaliciousPackageMessage(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append(DIV).append(scanIssue.getSeverity()).append(" ").append(PACKAGE_DETECTED)
                .append(":  ").append(scanIssue.getTitle())
                .append("@").append(scanIssue.getPackageVersion()).append(DIV_BR);
        descBuilder.append(DIV).append(getImage(getIconPath(Constants.ImagePaths.MALICIOUS_SEVERITY_PNG)))
                .append("<b>").append(scanIssue.getTitle()).append("@").append(scanIssue.getPackageVersion()).append("</b>")
                .append(" - ").append(scanIssue.getSeverity()).append(" ").append(PACKAGE).append(DIV_BR);
        descBuilder.append(DIV_BR);
    }

    /**
     * Builds the vulnerability section of a scan issue description and appends it to the provided StringBuilder.
     * This method processes the list of vulnerabilities associated with the scan issue, categorizes them by severity,
     * and includes detailed descriptions for specific vulnerabilities where applicable.
     *
     * @param descBuilder the StringBuilder to which the formatted vulnerability section will be appended
     * @param scanIssue   the ScanIssue object containing details about the scan, including associated vulnerabilities
     */
    private void buildVulnerabilitySection(StringBuilder descBuilder, ScanIssue scanIssue) {
        List<Vulnerability> vulnerabilityList = scanIssue.getVulnerabilities();
        if (vulnerabilityList != null && !vulnerabilityList.isEmpty()) {
            descBuilder.append(DIV);
            buildVulnerabilityIconWithCountMessage(descBuilder, vulnerabilityList);
            descBuilder.append("</div><br><div>");
            findVulnerabilityBySeverity(vulnerabilityList, scanIssue.getSeverity())
                    .ifPresent(vulnerability ->
                            descBuilder.append(wrapTextAtWord(escapeHtml(vulnerability.getDescription()))).append("<br>")
                    );
            descBuilder.append("</div><br><hr>");
        }
    }

    /**
     * Finds a vulnerability matching the specified severity level.
     *
     * @param vulnerabilityList the list of vulnerabilities to search
     * @param severity          the severity level to match
     * @return an Optional containing the matching vulnerability, or empty if not found
     */
    private Optional<Vulnerability> findVulnerabilityBySeverity(List<Vulnerability> vulnerabilityList, String severity) {
        return vulnerabilityList.stream()
                .filter(vulnerability -> vulnerability.getSeverity().equalsIgnoreCase(severity))
                .findFirst();
    }

    /**
     * Calculates the count of vulnerabilities grouped by their severity levels.
     * This method processes a list of vulnerabilities, retrieves their severity,
     * and returns a map where the keys are severity levels and the values are the counts.
     *
     * @param vulnerabilityList the list of vulnerabilities to be grouped and counted by severity
     * @return a map where the key is the severity level and the value is the count of vulnerabilities at that severity
     */
    private Map<String, Long> getVulnerabilityCount(List<Vulnerability> vulnerabilityList) {
        return vulnerabilityList.stream()
                .map(Vulnerability::getSeverity)
                .collect(Collectors.groupingBy(severity -> severity, Collectors.counting()));
    }

    /**
     * Builds a message representing the count of vulnerabilities categorized by severity level
     * and appends it to the provided description builder. This method uses severity icons
     * and corresponding counts formatted in a specific style.
     *
     * @param descBuilder       the StringBuilder to which the formatted vulnerability count message will be appended
     * @param vulnerabilityList the list of vulnerabilities to be processed for counting and categorizing by severity level
     */
    private void buildVulnerabilityIconWithCountMessage(StringBuilder descBuilder, List<Vulnerability> vulnerabilityList) {
        if (vulnerabilityList.isEmpty()) {
            return;
        }
        Map<String, Long> vulnerabilityCount = getVulnerabilityCount(vulnerabilityList);
        severityConfig.forEach((severity, iconPath) -> {
            Long count = vulnerabilityCount.get(severity);
            if (count != null && count > 0) {
                descBuilder.append(iconPath)
                        .append(count)
                        .append("&nbsp;&nbsp;&nbsp;&nbsp;");
            }
        });
    }

    private String getImage(String iconPath) {
        return "<img src='" + iconPath + "'/>&nbsp;&nbsp;";
    }

    private String getIconPath(String iconPath) {
        URL res = ProblemDescription.class.getResource(iconPath);
        return (res != null) ? res.toExternalForm() : "";
    }

    public static String wrapTextAtWord(String text) {
        StringBuilder result = new StringBuilder();
        int lineLength = 0;
        for (String word : text.split(" ")) {
            if (lineLength > 0) {
                // Add a space before the word if not at the start of a line
                result.append(" ");
                lineLength++;
            }
            if (lineLength + word.length() > MAX_LINE_LENGTH) {
                // Start a new line before adding the word
                result.append("\n");
                result.append(word);
                lineLength = word.length();
            } else {
                result.append(word);
                lineLength += word.length();
            }
        }
        return result.toString();
    }
}
