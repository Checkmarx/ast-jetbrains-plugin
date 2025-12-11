package com.checkmarx.intellij.devassist.ui;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;

import java.util.*;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.Constants.RealTimeConstants.SEPERATOR;
import static com.checkmarx.intellij.Utils.escapeHtml;

/**
 * This class is responsible for handling and formatting descriptions of scan issues
 * including their severity, associated vulnerabilities, and remediation guidance.
 * It provides various utility methods to construct and format messages for
 * different types of issues.
 */
public class ProblemDescription {

    private static final Map<String, String> DESCRIPTION_ICON = new LinkedHashMap<>();

    private static final String DIV = "<div>";
    private static final String DIV_BR = "</div><br>";
    private static final String COUNT = "COUNT";
    private static final String PACKAGE = "Package";
    private static final String DEV_ASSIST = "DevAssist";
    private static final String CONTAINER = "Container";
    private static final String TITLE_FONT_FAMILY = "font-family: menlo;";
    private static final String TITLE_FONT_SIZE = "font-size:11px;";
    private static final String SECONDARY_COLOUR = "color:#ADADAD;";

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
        DESCRIPTION_ICON.put(CONTAINER, getImage(Constants.ImagePaths.CONTAINER_PNG));
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
        descBuilder.append("<html><body><div style='display:flex;flex-direction:row;align-items:center;gap:5px;'>")
                .append(DIV).append("<table style='border-collapse:collapse;'><tr><td style='padding:0;'>")
                .append(DESCRIPTION_ICON.get(DEV_ASSIST)).append("</td></tr></table></div>");

        switch (scanIssue.getScanEngine()) {
            case OSS:
                buildOSSDescription(descBuilder, scanIssue);
                break;
            case ASCA:
                buildASCADescription(descBuilder, scanIssue);
                break;
            case SECRETS:
                buildSecretsDescription(descBuilder, scanIssue);
                break;
            case CONTAINERS:
                buildContainerDescription(descBuilder, scanIssue);
                break;
            default:
                buildDefaultDescription(descBuilder, scanIssue);
        }
       // buildRemediationActionsSection(descBuilder, scanIssue);
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
        buildPackageMessage(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    private void buildContainerDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        buildImageHeader(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }


    /**
     * Builds the Secrets description tooltip.
     *
     * @param descBuilder The StringBuilder to append the formatted HTML to.
     * @param scanIssue   The ScanIssue object containing details about the secret.
     */
    private void buildSecretsDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<div><table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
                // Column 1: Severity Icon
                .append("<td>").append(getIcon(scanIssue.getSeverity())).append("</td>")
                // Column 2: Title and Subtitle(- Secret finding)
                .append("<td>")
                .append("<p style=\"font-size:11px;\"><b>").append(escapeHtml(formatTitle(scanIssue.getTitle()))).append("</b> - Secret finding</p>")
                .append("</td>")
                .append("</tr></table></div>");
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
    private void buildPackageMessage(StringBuilder descBuilder, ScanIssue scanIssue) {
        String secondaryText = Constants.RealTimeConstants.SEVERITY_PACKAGE;
        String icon = getIcon(PACKAGE);
        if (scanIssue.getSeverity().equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity())) {
            secondaryText = PACKAGE;
            icon = getIcon(scanIssue.getSeverity());
        }
        descBuilder.append("<table style='border-collapse:collapse;'><tr><td style='padding:0;vertical-align:middle;'>")
                .append(icon).append("</td>")
                .append("<td style='").append(TITLE_FONT_FAMILY).append(TITLE_FONT_SIZE)
                .append("padding:0 2px 0 2px;vertical-align:middle;'><b>").append(scanIssue.getTitle()).append("@")
                .append(scanIssue.getPackageVersion()).append("</b></td>")
                .append("<td style='padding:0;vertical-align:middle;").append(SECONDARY_COLOUR).append("'> - ")
                .append(scanIssue.getSeverity()).append(" ").append(secondaryText)
                .append("</td></tr></table>");
    }

    private void buildImageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<table style='border-collapse:collapse;'><tr><td colspan=\"3\" style='padding:0;'><p style='font-size: 10px; margin:0;'>")
                .append(scanIssue.getSeverity()).append("-").append(Constants.RealTimeConstants.RISK_IMAGE)
                .append(" :  ").append(scanIssue.getTitle()).append("@").append(scanIssue.getImageTag()).append("</p></td></tr>");

        descBuilder.append("<tr><td style='padding:0;vertical-align:middle;'>").append(getIcon(CONTAINER)).append("</td>")
                .append("<td style='padding:0 4px 0 4px;vertical-align:middle;'><b>").append(scanIssue.getTitle()).append("@")
                .append(scanIssue.getImageTag()).append("</b></td><td style='padding:0;vertical-align:middle;'>")
                .append(scanIssue.getSeverity()).append(" ")
                .append(Constants.RealTimeConstants.SEVERITY_IMAGE)
                .append("</td></tr></table>");
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
        if (Objects.isNull(vulnerabilityList) || vulnerabilityList.isEmpty()) {
            return;
        }
        descBuilder.append(DIV).append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>");
        Map<String, Long> vulnerabilityCount = getVulnerabilityCount(vulnerabilityList);
        DESCRIPTION_ICON.forEach((severity, iconPath) -> {
            Long count = vulnerabilityCount.get(severity);
            if (count != null && count > 0) {
                descBuilder.append("<td style='padding:0;'>").append(getIcon(getSeverityCountIconKey(severity))).append("</td>")
                        .append("<td style='font-size:9px;color:#ADADAD;vertical-align:middle;padding:0 4px 0 1px;'>")
                        .append(count).append("</td>");
            }
        });
        descBuilder.append("</tr></table></div>");
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
     * Returns the key for the icon representing the specified severity with a count suffix.
     *
     * @param severity the severity
     * @return the key for the icon representing the specified severity with a count suffix
     */
    private static String getSeverityCountIconKey(String severity) {
        return severity + COUNT;
    }

    /**
     * Formats a kebab-case title into Title-Case (e.g., "generic-api-key" -> "Generic-Api-Key").
     *
     * @param title The kebab-case title string.
     * @return A formatted Title-Case string.
     */
    private String formatTitle(String title) {
        if (title == null || title.isEmpty()) {
            return "";
        }
        return Arrays.stream(title.split("-"))
                .map(word -> word.isEmpty() ? "" : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining("-"));
    }

    private void buildRemediationActionsSection(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<div><table style='border-collapse:collapse;'><tr>")
                .append("<td><a href=\"#cxonedevassist/copyfixprompt").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\">")
                .append(Constants.RealTimeConstants.FIX_WITH_CXONE_ASSIST).append("</a></td>")
                .append("<td><a href=\"#cxonedevassist/viewdetails").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\">")
                .append(Constants.RealTimeConstants.VIEW_DETAILS_FIX_NAME).append("</a></td>")
                .append("<td><a href=\"#cxonedevassist/ignorethis").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\">")
                .append(Constants.RealTimeConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME).append("</a></td>")
                .append("<td><a href=\"#cxonedevassist/ignoreallofthis").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\">")
                .append(Constants.RealTimeConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME).append("</a></td>")
                .append("</tr></table></div>");
    }
}
