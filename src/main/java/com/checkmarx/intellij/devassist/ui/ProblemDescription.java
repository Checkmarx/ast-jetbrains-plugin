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
import static com.checkmarx.intellij.devassist.ui.ProblemDescription.InlineHtml.*;

/**
 * Formats HTML descriptions for scan issues produced by different scan engines
 * such as OSS, ASCA, Secrets, and Containers.
 *
 * <p>
 * This class is responsible for:
 * <ul>
 *   <li>Rendering severity and engine-specific icons</li>
 *   <li>Formatting issue titles and secondary metadata</li>
 *   <li>Displaying remediation guidance</li>
 *   <li>Rendering vulnerability severity counts</li>
 * </ul>
 * </p>
 */
public class ProblemDescription {

    private static final Map<String, String> DESCRIPTION_ICON = new LinkedHashMap<>();

    private static final String DIV = "<div>";
    private static final String COUNT = "COUNT";
    private static final String PACKAGE = "Package";
    private static final String DEV_ASSIST = "DevAssist";
    private static final String CONTAINER = "Container";

    private static final String TITLE_FONT_FAMILY = "font-family: menlo;";
    private static final String TITLE_FONT_SIZE = "font-size:11px;";
    private static final String CELL_LINE_HEIGHT_STYLE = "line-height:16px;vertical-align:middle;";

    private static final String SECONDARY_SPAN_STYLE =
            "display:inline-block;vertical-align:middle;line-height:16px;font-size:11px;color:#ADADAD;"
                    + "font-family:system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif;";

    /** Default inline icon style used consistently across all engines. */
    private static final String ICON_INLINE_STYLE =
            "display:inline-block;vertical-align:middle;max-height:16px;line-height:16px;";

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
     * Reloads icon mappings, typically invoked when the IDE theme changes.
     */
    public static void reloadIcons() {
        initIconsMap();
    }

    /**
     * Builds the complete HTML description for a scan issue.
     *
     * @param scanIssue the {@link ScanIssue} containing all issue metadata
     * @return formatted HTML description string
     */
    public String formatDescription(ScanIssue scanIssue) {
        StringBuilder descBuilder = new StringBuilder();
        descBuilder.append("<html><body>");

        // DevAssist image
        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>")
                .append(DESCRIPTION_ICON.get(DEV_ASSIST))
                .append("</td></tr></table>");

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
        //buildRemediationActionsSection(descBuilder, scanIssue);
        descBuilder.append("</body></html>");
        return descBuilder.toString();
    }

    /** OSS description (package + vulnerability counts). */
    private void buildOSSDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        buildPackageMessage(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    /** Container description (image header + vulnerability counts). */
    private void buildContainerDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        buildImageHeader(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    /**
     * Secrets description.
     * Format:
     * [Severity Icon] Title (bold) - Secret finding
     */
    private void buildSecretsDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        String icon = getStyledImage(scanIssue.getSeverity(), ICON_INLINE_STYLE);

        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
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
     * [Severity Icon] Title (bold) - remediation advice - SAST vulnerability
     */
    private void buildASCADescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        String icon = getStyledImage(scanIssue.getSeverity(), ICON_INLINE_STYLE);

        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon).append("</td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("</b>")
                .append(" - ").append(escapeHtml(scanIssue.getRemediationAdvise()))
                .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>SAST vulnerability</span>")
                .append("</p></td></tr></table>");
    }

    /** Default fallback description. */
    private void buildDefaultDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<div><b>")
                .append(scanIssue.getTitle())
                .append("</b> - ")
                .append(scanIssue.getDescription())
                .append("</div>");
    }

    /** Package header for OSS. */
    private void buildPackageMessage(StringBuilder descBuilder, ScanIssue scanIssue) {
        String secondaryText = Constants.RealTimeConstants.SEVERITY_PACKAGE;
        String iconKey = PACKAGE;

        if (scanIssue.getSeverity().equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity())) {
            secondaryText = PACKAGE;
            iconKey = scanIssue.getSeverity();
        }

        String icon = getStyledImage(iconKey, ICON_INLINE_STYLE);

        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
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

    /** Container image header. */
    private void buildImageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
        String icon = getStyledImage(CONTAINER, ICON_INLINE_STYLE);

        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon).append("</td>")
                .append("<td style='padding:0 2px 0 2px;")
                .append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style='margin:0;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("'>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("@")
                .append(escapeHtml(scanIssue.getImageTag())).append("</b>")
                .append("</p></td></tr></table>");
    }

    /** Vulnerability count section. */
    private void buildVulnerabilitySection(StringBuilder descBuilder, ScanIssue scanIssue) {
        List<Vulnerability> vulnerabilityList = scanIssue.getVulnerabilities();
        if (vulnerabilityList == null || vulnerabilityList.isEmpty()) {
            return;
        }

        descBuilder.append(DIV).append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>");
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
     * Groups vulnerabilities by severity.
     */
    private Map<String, Long> getVulnerabilityCount(List<Vulnerability> vulnerabilityList) {
        return vulnerabilityList.stream()
                .map(Vulnerability::getSeverity)
                .collect(Collectors.groupingBy(severity -> severity, Collectors.counting()));
    }

    /**
     * Returns an HTML image tag for the given icon path.
     */
    private static String getImage(String iconPath) {
        return iconPath.isEmpty()
                ? ""
                : "<img src='" + DevAssistUtils.themeBasedPNGIconForHtmlImage(iconPath)
                + "' style='vertical-align:middle;'/>";
    }

    /**
     * Returns the severity count icon key.
     */
    private static String getSeverityCountIconKey(String severity) {
        return severity + COUNT;
    }

    /**
     * Formats a kebab-case title into title case.
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
     * @param scanIssue   {@link ScanIssue} object containing the remediation actions section data.
     */
    private void buildRemediationActionsSection(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append(TABLE_WITH_TR)
                .append("<td>")
                .append("<a href=\"#cxonedevassist/copyfixprompt").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap;'>")
                .append(Constants.RealTimeConstants.FIX_WITH_CXONE_ASSIST)
                .append("</a></td>")
                .append("<td style='padding: 5px;'>")
                .append("<a href=\"#cxonedevassist/viewdetails").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap;'>")
                .append(Constants.RealTimeConstants.VIEW_DETAILS_FIX_NAME)
                .append("</a></td>")
                .append("<td style='padding: 5px;'>")
                .append("<a href=\"#cxonedevassist/ignorethis").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap;'>")
                .append(Constants.RealTimeConstants.IGNORE_THIS_VULNERABILITY_FIX_NAME)
                .append("</a></td>")
                .append("<td style='padding: 5px;'>")
                .append("<a href=\"#cxonedevassist/ignoreallofthis").append(SEPERATOR).append(scanIssue.getScanIssueId()).append("\" ")
                .append("style='text-decoration: none; color: #4470EC; font-family: inter; white-space: nowrap;'>")
                .append(Constants.RealTimeConstants.IGNORE_ALL_OF_THIS_TYPE_FIX_NAME)
                .append("</a></td>")
                .append("</tr></table>");
    }

    /**
     * Holds inline HTML constants used for formatting descriptions.
     */
    static final class InlineHtml {

        private InlineHtml() {}

        static final String TABLE_WITH_TR = "<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>";
    }


}
