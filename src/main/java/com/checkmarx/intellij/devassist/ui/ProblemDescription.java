package com.checkmarx.intellij.devassist.ui;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.devassist.utils.DevAssistConstants;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.devassist.utils.ScanEngine;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.diagnostic.Logger;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.checkmarx.intellij.devassist.utils.DevAssistConstants.SEPERATOR;
import static com.checkmarx.intellij.Utils.escapeHtml;
import static com.checkmarx.intellij.devassist.ui.ProblemDescription.InlineStyle.*;

/**
 * This class is responsible for handling and formatting descriptions of scan issues
 * including their severity, associated vulnerabilities, and remediation guidance.
 * It provides various utility methods to construct and format messages for
 * different types of issues.
 */
public final class ProblemDescription {

    private static final Map<String, String> DESCRIPTION_ICON = new LinkedHashMap<>();
    private static final Logger LOGGER = Utils.getLogger(ProblemDescription.class);

    private static final String COUNT = "COUNT";
    private static final String PACKAGE = "Package";
    private static final String DEV_ASSIST = "DevAssist";
    private static final String CONTAINER = "Container";

    /**
     * Constructs a {@code ProblemDescription} instance and initializes icon mappings.
     */
    public ProblemDescription() {
        initIconsMap();
    }

    /**
     * Initializes the mapping between severity / logical keys and their HTML icon representations.
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
        LOGGER.info("RTS: Icons reloading completed.");
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
        descBuilder.append("<html><body style='margin:0;padding:0;'>");

        // DevAssist image
        descBuilder.append(TABLE_WITH_TR).append("<td style='vertical-align:middle;'>")
                .append(DESCRIPTION_ICON.get(DEV_ASSIST)).append("</td></tr></table>");

        // For ASCA and IAC multiple violations
        appendMultipleViolationsTitle(descBuilder, scanIssue);

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
            case IAC:
                buildIACDescription(descBuilder, scanIssue);
                break;
            case CONTAINERS:
                buildContainerDescription(descBuilder, scanIssue);
                break;
            default:
                buildDefaultDescription(descBuilder, scanIssue);
        }
        if (scanIssue.getScanEngine() != ScanEngine.IAC && scanIssue.getScanEngine() != ScanEngine.ASCA) {
            buildRemediationActionsSection(descBuilder, scanIssue.getScanIssueId(), scanIssue.getScanEngine().name());
        }
        descBuilder.append("</body></html>");
        return descBuilder.toString();
    }

    /**
     * Appends multiple violations title for ASCA and IAC engines when there are multiple vulnerabilities.
     * This method adds a formatted title showing the number of violations detected.
     *
     * @param descBuilder the StringBuilder to append the title to
     * @param scanIssue   the ScanIssue containing information about vulnerabilities
     */
    private void appendMultipleViolationsTitle(StringBuilder descBuilder, ScanIssue scanIssue) {
        if (scanIssue.getVulnerabilities() == null || scanIssue.getVulnerabilities().size() <= 1) {
            return;
        }

        boolean isASCAOrIAC = scanIssue.getScanEngine() == ScanEngine.ASCA ||
                             scanIssue.getScanEngine() == ScanEngine.IAC;

        if (isASCAOrIAC) {
            descBuilder.append(TABLE_WITH_TR)
                    .append("<td style='padding:0 6px 0 0;'>")
                    .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                    .append(escapeHtml(scanIssue.getTitle()))
                    .append(" <span style='").append(SECONDARY_SPAN_STYLE).append("'>Checkmarx One Assist</span>")
                    .append("</p></td></tr></table>");
        }
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

    /**
     * Container description (image header + vulnerability counts).
     */
    private void buildContainerDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        buildImageHeader(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    /**
     * IAC description (image header + vulnerability description with Title).
     */
    private void buildIACDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        for (Vulnerability vulnerability : scanIssue.getVulnerabilities()) {
            String severityIcon = getStyledImage(vulnerability.getSeverity(), ICON_INLINE_STYLE);
            descBuilder.append(TABLE_WITH_TR_IAC_ASCA)
                    .append("<td style='width:20px;padding:0 6px 0 0;vertical-align:middle;'>")
                    .append(severityIcon)
                    .append("</td>");

            descBuilder.append("<td style='"
                    + "padding:0 4px;"
                    + "white-space:normal;"
                    + TITLE_FONT_SIZE
                    + TITLE_FONT_FAMILY
                    + CELL_LINE_HEIGHT_STYLE
                    + "'>");

            descBuilder.append("<div style='"
                    + "display:block;"
                    + "word-break:break-word;"
                    + "overflow-wrap:anywhere;"
                    + "'>");

            descBuilder.append("<b>")
                    .append(escapeHtml(vulnerability.getTitle()))
                    .append("</b>")
                    .append(" - ")
                    .append(escapeHtml(vulnerability.getActualValue()))
                    .append(" ")
                    .append(escapeHtml(vulnerability.getDescription()));

            descBuilder.append("<span style='")
                    .append(SECONDARY_SPAN_STYLE)
                    .append("'> IaC vulnerability</span>");

            descBuilder.append("</div></td></tr></table>");
            buildRemediationActionsSection(descBuilder, vulnerability.getVulnerabilityId(), scanIssue.getScanEngine().name());
        }
    }


    /**
     * Secrets description.
     * Format:
     * [Severity Icon] Title (bold) - Secret finding
     */
    private void buildSecretsDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        String icon = getStyledImage(scanIssue.getSeverity(), ICON_INLINE_STYLE);

        descBuilder.append(TABLE_WITH_TR)
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon).append("</td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(formatTitle(scanIssue.getTitle()))).append("</b>")
                .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>Secret finding</span>")
                .append("</p></td></tr></table>");
    }

    /**
     * ASCA description.
     * Format:
     * [Title for multiple issues]
     * [Severity Icon] Title (bold) - description - SAST vulnerability
     */
    private void buildASCADescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        for (Vulnerability vulnerability : scanIssue.getVulnerabilities()) {
            String severityIcon = getStyledImage(vulnerability.getSeverity(), ICON_INLINE_STYLE);
            descBuilder.append(TABLE_WITH_TR_IAC_ASCA)
                    .append("<td style='width:20px;padding:0 6px 0 0;vertical-align:middle;'>")
                    .append(severityIcon)
                    .append("</td>");
            descBuilder.append("<td style='padding:0 6px 0 6px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
                    .append(CELL_LINE_HEIGHT_STYLE).append("'>")
                    .append("<div style='display:flex;flex-direction:row;align-items:center;gap:6px;'>")
                    .append("<p style=\"").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("\">")
                    .append("<b>").append(escapeHtml(vulnerability.getTitle())).append("</b>")
                    .append(" - ")
                    .append(escapeHtml(vulnerability.getDescription()))
                    .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>SAST vulnerability</span>")
                    .append("</p>")
                    .append("</div></td></tr></table>");
            buildRemediationActionsSection(descBuilder, vulnerability.getVulnerabilityId(), scanIssue.getScanEngine().name());
        }
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
    /**
     * Default fallback description.
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
        String secondaryText = DevAssistConstants.SEVERITY_PACKAGE;
        String iconKey = PACKAGE;
        if (scanIssue.getSeverity().equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity())) {
            secondaryText = PACKAGE;
            iconKey = scanIssue.getSeverity();
        }

        String icon = getStyledImage(iconKey, ICON_INLINE_STYLE);

        descBuilder.append(TABLE_WITH_TR)
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon).append("</td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("@")
                .append(escapeHtml(scanIssue.getPackageVersion())).append("</b>")
                .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>")
                .append(escapeHtml(scanIssue.getSeverity())).append(" ").append(escapeHtml(secondaryText))
                .append("</span></p></td></tr></table>");
    }

    /**
     * Container image header.
     */
    private void buildImageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
        String icon = getStyledImage(CONTAINER, ICON_INLINE_STYLE);

        descBuilder.append(TABLE_WITH_TR)
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon).append("</td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("@")
                .append(escapeHtml(scanIssue.getImageTag())).append("</b>")
                .append("</p></td></tr></table>");
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
        if (vulnerabilityList == null || vulnerabilityList.isEmpty()) {
            return;
        }
        descBuilder.append("<div>").append(TABLE_WITH_TR);
        Map<String, Long> vulnerabilityCount = getVulnerabilityCount(vulnerabilityList);
        DESCRIPTION_ICON.forEach((severity, iconPath) -> {
            Long count = vulnerabilityCount.get(severity);
            if (count != null && count > 0) {
                descBuilder.append("<td style='padding:0;'>")
                        .append(DESCRIPTION_ICON.get(getSeverityCountIconKey(severity)))
                        .append("</td>")
                        .append("<td style='font-size:9px;color:#ADADAD;vertical-align:middle;padding:0 4px 0 1px;'>")
                        .append(count)
                        .append("</td>");
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
        return iconPath.isEmpty() ? "" : "<img src='" + DevAssistUtils.themeBasedPNGIconForHtmlImage(iconPath) + "' style='vertical-align:middle;'/>";
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
                .map(word -> word.isEmpty()
                        ? ""
                        : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining("-"));
    }

    /**
     * Injects inline styles into an existing HTML image tag.
     */
    private String getStyledImage(String key, String extraStyle) {
        String imgTag = DESCRIPTION_ICON.getOrDefault(key, "");
        if (imgTag == null || imgTag.isEmpty()) {
            return "";
        }
        if (imgTag.contains("style='")) {
            return imgTag.replaceFirst("style='", "style='" + extraStyle);
        } else if (imgTag.contains("style=\"")) {
            return imgTag.replaceFirst("style=\"", "style=\"" + extraStyle);
        } else {
            int insertPos = imgTag.indexOf("/>");
            return insertPos > 0
                    ? imgTag.substring(0, insertPos) + " style='" + extraStyle + "'" + imgTag.substring(insertPos)
                    : imgTag;
        }
    }

    /**
     * Builds the remediation actions section of the description.
     *
     * @param descBuilder {@link StringBuilder} object to add the remediation actions section to.
     * @param scanIssueId {@link String} object containing the remediation actions section data.
     */
    private void buildRemediationActionsSection(StringBuilder descBuilder, String scanIssueId, String engineName) {
        descBuilder.append("<table style='display:block;margin:0;border-collapse:collapse;border-spacing:0;padding:0;'><tr>")
                // Fix with Copilot - Opens GitHub Copilot Chat in Agent mode
                .append("<td style='padding:0 10px 0 0;margin:0;'>")
                .append("<a href=\"#cxonedevassist/fixwithcopilot").append(SEPERATOR).append(scanIssueId).append(SEPERATOR).append(engineName).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap; margin:0; padding:0; font-weight: bold;'>")
                .append(DevAssistConstants.FIX_WITH_COPILOT)
                .append("</a></td>")
                // Fix with Augment - Opens Augment Code Chat in Agent mode
                .append("<td style='padding:0 10px 0 0;margin:0;'>")
                .append("<a href=\"#cxonedevassist/fixwithaugment").append(SEPERATOR).append(scanIssueId).append(SEPERATOR).append(engineName).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap; margin:0; padding:0; font-weight: bold;'>")
                .append(DevAssistConstants.FIX_WITH_AUGMENT)
                .append("</a></td>")
                // Copy fix prompt - Copies prompt to clipboard
                .append("<td style='padding:0 10px 0 0;margin:0;'>")
                .append("<a href=\"#cxonedevassist/copyfixprompt").append(SEPERATOR).append(scanIssueId).append(SEPERATOR).append(engineName).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap; margin:0; padding:0;'>")
                .append(DevAssistConstants.FIX_WITH_CXONE_ASSIST)
                .append("</a></td>")
                // View details
                .append("<td style='padding:0 10px 0 0;margin:0;'>")
                .append("<a href=\"#cxonedevassist/viewdetails").append(SEPERATOR).append(scanIssueId).append(SEPERATOR).append(engineName).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap; margin:0; padding:0;'>")
                .append(DevAssistConstants.VIEW_DETAILS_FIX_NAME)
                .append("</a></td>")
                .append("</tr></table><br>");
    }

    /**
     * Inline styles used in the problem description HTML formatting.
     */
    static class InlineStyle {

        private InlineStyle() {
        }

        static final String TABLE_WITH_TR = "<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>";
        static final String TABLE_WITH_TR_IAC_ASCA = "<table cellspacing='0' cellpadding='0' " +
                "style='display:table;" +
                "border-collapse:collapse;" +
                "table-layout:fixed;" +
                "width:460px;'><tr>";
        static final String TITLE_FONT_FAMILY = "font-family: menlo;";
        static final String TITLE_FONT_SIZE = "font-size:11px;";
        static final String CELL_LINE_HEIGHT_STYLE = "line-height:16px;vertical-align:middle;";
        static final String SECONDARY_SPAN_STYLE =
                "display:inline-block;vertical-align:middle;line-height:16px;font-size:11px;color:#ADADAD;"
                        + "font-family:system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif;";

        /**
         * Default inline severity icon style used consistently across all engines.
         */
        static final String ICON_INLINE_STYLE =
                "display:inline-block;vertical-align:middle;max-height:16px;line-height:16px;";
    }
}
