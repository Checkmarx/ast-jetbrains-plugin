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
 * Formats descriptions for scan issues (OSS, ASCA, SECRETS, CONTAINERS).
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
    private static final String SECONDARY_SPAN_STYLE = "display:inline-block;vertical-align:middle;line-height:16px;font-size:11px;color:#ADADAD;font-family:system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif;";
    private static final String ICON_INLINE_STYLE = "display:inline-block;vertical-align:middle;max-height:16px;line-height:16px;";
    private static final String CELL_LINE_HEIGHT_STYLE = "line-height:16px;vertical-align:middle;";

    public ProblemDescription() {
        initIconsMap();
    }

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

    public static void reloadIcons() {
        initIconsMap();
    }

    public String formatDescription(ScanIssue scanIssue) {
        StringBuilder descBuilder = new StringBuilder();

        descBuilder.append("<html><body>");
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
        descBuilder.append("</body></html>");
        return descBuilder.toString();
    }

    private void buildOSSDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        buildPackageMessage(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    private void buildContainerDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        buildImageHeader(descBuilder, scanIssue);
        buildVulnerabilitySection(descBuilder, scanIssue);
    }

    private void buildSecretsDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        // Secrets: Severity icon + Title(bold) - Secret finding
        String icon = getStyledImage(scanIssue.getSeverity(), ICON_INLINE_STYLE);

        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon).append("</td>")
                .append("<td style='padding:0 2px 0 2px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
                .append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style=\"").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("\">")
                .append("<b>").append(escapeHtml(formatTitle(scanIssue.getTitle()))).append("</b>")
                .append(" - <span style=\"").append(SECONDARY_SPAN_STYLE).append("\">Secret finding</span></p>")
                .append("</td>")
                .append("</tr></table>");
    }

    private void buildASCADescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        String severityIcon = getStyledImage(scanIssue.getSeverity(), ICON_INLINE_STYLE);

        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
                .append("<td style='width:20px;padding:0 6px 0 0;vertical-align:middle;'>")
                .append(severityIcon)
                .append("</td>");

        descBuilder.append("<td style='padding:0 8px 0 6px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
                .append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<div style='display:flex;flex-direction:row;align-items:center;gap:6px;'>")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("</b> - ")
                .append(escapeHtml(scanIssue.getRemediationAdvise()))
                .append("<span style='").append(SECONDARY_SPAN_STYLE).append(" margin:0;'> - ")
                .append(scanIssue.getScanEngine().name())
                .append("</span>")
                .append("</div></td></tr></table><br>");
    }

    private String getIcon(String key) {
        return DESCRIPTION_ICON.getOrDefault(key, "");
    }

    private void buildDefaultDescription(StringBuilder descBuilder, ScanIssue scanIssue) {
        descBuilder.append("<div><b>").append(scanIssue.getTitle()).append("</b> -").append(scanIssue.getDescription());
    }

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
                .append("<td style='padding:0 2px 0 2px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
                .append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style=\"").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("\">")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("@")
                .append(escapeHtml(scanIssue.getPackageVersion())).append("</b>")
                .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>")
                .append(escapeHtml(scanIssue.getSeverity())).append(" ").append(escapeHtml(secondaryText))
                .append("</span></p>")
                .append("</td></tr></table>");
    }

    private void buildImageHeader(StringBuilder descBuilder, ScanIssue scanIssue) {
        String secondaryText = Constants.RealTimeConstants.SEVERITY_IMAGE;
        String icon = getStyledImage(CONTAINER, ICON_INLINE_STYLE);

        descBuilder.append("<table style='display:inline-table;vertical-align:middle;border-collapse:collapse;'><tr>")
                .append("<td style='padding:0 6px 0 0;vertical-align:middle;'>").append(icon).append("</td>")
                .append("<td style='padding:0 2px 0 2px;").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY)
                .append(CELL_LINE_HEIGHT_STYLE).append("'>")
                .append("<p style=\"").append(TITLE_FONT_SIZE).append(TITLE_FONT_FAMILY).append("\">")
                .append("<b>").append(escapeHtml(scanIssue.getTitle())).append("@")
                .append(escapeHtml(scanIssue.getImageTag())).append("</b>")
                .append(" - <span style='").append(SECONDARY_SPAN_STYLE).append("'>")
                .append(escapeHtml(scanIssue.getSeverity())).append(" ").append(escapeHtml(secondaryText))
                .append("</span></p>")
                .append("</td></tr></table>");
    }

    /**
     * Returns a styled <img/> HTML string based on the stored image tag in DESCRIPTION_ICON.
     * Appends extraStyle to the img tag's style attribute (if present) or inserts one.
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
            if (insertPos > 0) {
                String before = imgTag.substring(0, insertPos);
                String after = imgTag.substring(insertPos);
                return before + " style='" + extraStyle + "'" + after;
            }
            return imgTag;
        }
    }

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

    private Map<String, Long> getVulnerabilityCount(List<Vulnerability> vulnerabilityList) {
        return vulnerabilityList.stream()
                .map(Vulnerability::getSeverity)
                .collect(Collectors.groupingBy(severity -> severity, Collectors.counting()));
    }

    private static String getImage(String iconPath) {
        return iconPath.isEmpty() ? "" : "<img src='" + DevAssistUtils.themeBasedPNGIconForHtmlImage(iconPath) + "' style='vertical-align:middle;'/>";
    }

    private static String getSeverityCountIconKey(String severity) {
        return severity + COUNT;
    }

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
