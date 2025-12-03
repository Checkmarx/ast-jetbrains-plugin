package com.checkmarx.intellij.devassist.ui;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;

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

    private static final int MAX_LINE_LENGTH = 120;
    private static final Map<String, String> DESCRIPTION_ICON = new LinkedHashMap<>();

    private static final String DIV = "<div>";
    private static final String DIV_BR = "</div><br>";
    private static final String COUNT = "COUNT";
    private static final String PACKAGE = "Package";
    private static final String DEV_ASSIST = "DevAssist";

    public ProblemDescription() {
        initIconsMap();
    }

    /**
     * Initializes the mapping from severity levels to severity-specific icons.
     */
    private static void initIconsMap() {
        DESCRIPTION_ICON.put(SeverityLevel.MALICIOUS.getSeverity(), getImage(Constants.ImagePaths.MALICIOUS_PNG));
        DESCRIPTION_ICON.put(SeverityLevel.CRITICAL.getSeverity(), getImage(Constants.ImagePaths.CRITICAL_PNG));
        DESCRIPTION_ICON.put(SeverityLevel.HIGH.getSeverity(), getImage(Constants.ImagePaths.HIGH_PNG));
        DESCRIPTION_ICON.put(SeverityLevel.MEDIUM.getSeverity(), getImage(Constants.ImagePaths.MEDIUM_PNG));
        DESCRIPTION_ICON.put(SeverityLevel.LOW.getSeverity(), getImage(Constants.ImagePaths.LOW_PNG));

        DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.CRITICAL.getSeverity()), getImage(Constants.ImagePaths.CRITICAL_16_PNG));
        DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.HIGH.getSeverity()), getImage(Constants.ImagePaths.HIGH_16_PNG));
        DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.MEDIUM.getSeverity()), getImage(Constants.ImagePaths.MEDIUM_16_PNG));
        DESCRIPTION_ICON.put(getSeverityCountIconKey(SeverityLevel.LOW.getSeverity()), getImage(Constants.ImagePaths.LOW_16_PNG));

        DESCRIPTION_ICON.put(PACKAGE, getImage(Constants.ImagePaths.PACKAGE_PNG));
        DESCRIPTION_ICON.put(DEV_ASSIST, getImage(Constants.ImagePaths.DEV_ASSIST_PNG));
    }

    /**
     * Reloads the mapping from severity levels to severity-specific icons.
     */
    public static void reloadIcons() {
        initIconsMap();
    }

    /**
     * Formats a description for the given scan issue, incorporating details such as
     * relevant icon, scan engine information, and issue details in an HTML structure.
     * Depending on the scan engine type, it delegates the construction of the specific
     * description sections to corresponding helper methods.
     *
     * @param scanIssue the ScanIssue object containing information about the identified issue,
     *                  including details like its severity, title, vulnerabilities, and scan engine.
     * @return a String representing the formatted HTML description of the scan issue,
     * with visual elements included for improved readability.
     */
    public String formatDescription(ScanIssue scanIssue) {

        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append("<html><body><div style='display:flex;flex-direction:row;align-items:center;gap:10px;'>")
                .append(DIV).append("<table style='border-collapse:collapse;'><tr><td style='padding:0;'>")
                .append(DESCRIPTION_ICON.get(DEV_ASSIST)).append("</td></tr></table></div>");
        switch (scanIssue.getScanEngine()) {
            case OSS:
                buildOSSDescription(descBuilder, scanIssue);
                break;
            case ASCA:
                buildASCADescription(descBuilder, scanIssue);
                break;
            case CONTAINERS:
                buildContainerDescription(descBuilder,scanIssue);
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

    private void buildContainerDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        buildImageHeader(descBuilder, scanIssue);
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
        descBuilder.append("<table style='table-layout:fixed;border-collapse:collapse;'><tr>")
                .append("<td style='width:20px;vertical-align:top;padding:0;'><div style='flex:0 0 auto; display:flex; align-items:flex-start;'>")
                .append(getIcon(scanIssue.getSeverity()))
                .append("</div></td>");
        descBuilder.append("<div style='display:flex;flex-direction:row;align-items:flex-start;gap:10px;'><div>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("</b> - ")
                .append(escapeHtml(scanIssue.getRemediationAdvise())).append("<span style='color: gray; margin:0;'> - ")
                .append(scanIssue.getScanEngine().name())
                .append("</span></div></tr></table><br>");
    }

    /**
     * Returns the icon path for the specified key.
     *
     * @param key the key for the icon path
     * @return the icon path
     */
    private String getIcon(String key) {
        return DESCRIPTION_ICON.getOrDefault(key, "");
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
        descBuilder.append("<table style='border-collapse:collapse;'><tr><td colspan=\"3\" style='padding:0;'><p style='font-size: 10px; margin:0;'>")
                .append(scanIssue.getSeverity()).append("-").append(Constants.RealTimeConstants.RISK_PACKAGE)
                .append(" :  ").append(scanIssue.getTitle()).append("@").append(scanIssue.getPackageVersion()).append("</p></td></tr>");

        descBuilder.append("<tr><td style='padding:0;vertical-align:middle;'>").append(getIcon(PACKAGE)).append("</td>")
                .append("<td style='padding:0 4px 0 4px;vertical-align:middle;'><b>").append(scanIssue.getTitle()).append("@")
                .append(scanIssue.getPackageVersion()).append("</b></td><td style='padding:0;vertical-align:middle;'> - ")
                .append(scanIssue.getSeverity()).append(" ")
                .append(Constants.RealTimeConstants.SEVERITY_PACKAGE)
                .append("</td></tr></table>");
    }

    private void buildImageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<table style='border-collapse:collapse;'><tr><td colspan=\"3\" style='padding:0;'><p style='font-size: 10px; margin:0;'>")
                .append(scanIssue.getSeverity()).append("-").append(Constants.RealTimeConstants.RISK_PACKAGE)
                .append(" :  ").append(scanIssue.getTitle()).append("@").append(scanIssue.getImageTag()).append("</p></td></tr>");

        descBuilder.append("<tr><td style='padding:0;vertical-align:middle;'>").append(getIcon(PACKAGE)).append("</td>")
                .append("<td style='padding:0 4px 0 4px;vertical-align:middle;'><b>").append(scanIssue.getTitle()).append("@")
                .append(scanIssue.getImageTag()).append("</b></td><td style='padding:0;vertical-align:middle;'>")
                .append(scanIssue.getSeverity()).append(" ")
                .append(Constants.RealTimeConstants.SEVERITY_PACKAGE)
                .append("</td></tr></table>");
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
        descBuilder.append("<table style='border-collapse:collapse;'><tr><td colspan=\"3\" style='padding:0;'>");
        buildMaliciousPackageHeader(descBuilder, scanIssue);
        descBuilder.append("</td></tr><tr><td>").append(getIcon(scanIssue.getSeverity())).append("</td>")
                .append("<td><span><b>").append(scanIssue.getTitle()).append("@").append(scanIssue.getPackageVersion()).append("</b></span>")
                .append("<span> - ").append(scanIssue.getSeverity()).append(" ").append(PACKAGE)
                .append("</span><td></tr></table>");
    }

    /**
     * Builds the malicious package header section of a scan issue description and appends it to the provided StringBuilder.
     *
     * @param descBuilder the StringBuilder to which the formatted malicious package header will be appended
     * @param scanIssue   he ScanIssue object containing details about the malicious package
     */
    private void buildMaliciousPackageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<p style='font-size: 10px; margin:0;'>").append(scanIssue.getSeverity())
                .append(" ").append(Constants.RealTimeConstants.PACKAGE_DETECTED).append(" :  ")
                .append(scanIssue.getTitle()).append("@").append(scanIssue.getPackageVersion()).append("</p>");
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
            descBuilder.append("<p style='margin-left: 5px; margin-top:4px; margin-bottom:0;'>");
            findVulnerabilityBySeverity(vulnerabilityList, scanIssue.getSeverity())
                    .ifPresent(vulnerability ->
                            descBuilder.append(escapeHtml(vulnerability.getDescription()))
                    );
            descBuilder.append(DIV_BR).append("<p>");
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
        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>");
        Map<String, Long> vulnerabilityCount = getVulnerabilityCount(vulnerabilityList);
        DESCRIPTION_ICON.forEach((severity, iconPath) -> {
            Long count = vulnerabilityCount.get(severity);
            if (count != null && count > 0) {
                descBuilder.append("<td style='padding:0;'>").append(getIcon(getSeverityCountIconKey(severity))).append("</td>")
                        .append("<td style='vertical-align:middle;padding:0 6px 0 2px;'>")
                        .append(count).append("</td>");


            }
        });
        descBuilder.append("</tr></table>");
    }

    /**
     * Generates an HTML image element based on the provided icon name.
     *
     * @param iconPath the path to the image file that will be used in the HTML content
     * @return a String representing an HTML image element with the provided icon path
     */
    private static String getImage(String iconPath) {
        // Add vertical-align:middle and remove default spacing; display:inline-block ensures tight layout.
        return iconPath.isEmpty() ? "" : "<img src='" + DevAssistUtils.themeBasedPNGIconForHtmlImage(iconPath) + "'/>";
    }

    /**
     * Wraps the provided text at the word boundary.
     *
     * @param text the text to be wrapped
     * @return the wrapped text
     */
    private String wrapText(String text) {
        return text.length() < MAX_LINE_LENGTH ? text : DevAssistUtils.wrapTextAtWord(text, MAX_LINE_LENGTH);
    }

    /**
     * Returns the key for the icon representing the specified severity with a count suffix.
     *
     * @param severity the severity
     * @return the key for the icon representing the specified severity with a count suffix
     */
    private static String getSeverityCountIconKey(String severity) {
        return severity + COUNT;
    }
}
