package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.remediation.CxOneAssistFix;
import com.checkmarx.intellij.devassist.remediation.IgnoreAllThisTypeFix;
import com.checkmarx.intellij.devassist.remediation.IgnoreVulnerabilityFix;
import com.checkmarx.intellij.devassist.remediation.ViewDetailsFix;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.ui.ProblemDescription;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ProblemBuilder class is a utility class responsible for constructing
 * ProblemDescriptor objects based on specific scan issues identified within a PsiFile.
 * It encapsulates the logic to derive necessary problem details such as text range,
 * description, and highlight type, delegating specific computations to an instance
 * of ProblemDecorator.
 * <p>
 * This class cannot be instantiated.
 */
public class ProblemBuilder {

    private static final Map<String, ProblemHighlightType> SEVERITY_HIGHLIGHT_TYPE_MAP = new HashMap<>();
    private static final ProblemDescription PROBLEM_DESCRIPTION_INSTANCE = new ProblemDescription();

    /*
     * Static initializer to initialize the mapping from severity levels to problem highlight types.
     */
    static {
        initSeverityToHighlightMap();
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private ProblemBuilder() {}

    /**
     * Initializes the mapping from severity levels to problem highlight types.
     */
    private static void initSeverityToHighlightMap() {
        SEVERITY_HIGHLIGHT_TYPE_MAP.put(SeverityLevel.MALICIOUS.getSeverity(), ProblemHighlightType.GENERIC_ERROR);
        SEVERITY_HIGHLIGHT_TYPE_MAP.put(SeverityLevel.CRITICAL.getSeverity(), ProblemHighlightType.GENERIC_ERROR);
        SEVERITY_HIGHLIGHT_TYPE_MAP.put(SeverityLevel.HIGH.getSeverity(), ProblemHighlightType.GENERIC_ERROR);
        SEVERITY_HIGHLIGHT_TYPE_MAP.put(SeverityLevel.MEDIUM.getSeverity(), ProblemHighlightType.WARNING);
        SEVERITY_HIGHLIGHT_TYPE_MAP.put(SeverityLevel.LOW.getSeverity(), ProblemHighlightType.WEAK_WARNING);
    }

    /**
     * Builds a ProblemDescriptor for the given scan issue.
     *
     * @param file       the PsiFile being inspected
     * @param manager    the InspectionManager
     * @param scanIssue  the scan issue
     * @param document   the document
     * @param problemLineNumber the line number where the problem was found
     * @param isOnTheFly whether the inspection is on-the-fly
     * @return a ProblemDescriptor instance
     */
    static ProblemDescriptor build(@NotNull PsiFile file, @NotNull InspectionManager manager,
                                   @NotNull ScanIssue scanIssue, @NotNull Document document,
                                   int problemLineNumber, boolean isOnTheFly) {
        TextRange problemRange = getTextRange(document ,scanIssue.getLocations());
        String description = PROBLEM_DESCRIPTION_INSTANCE.formatDescription(scanIssue);
        ProblemHighlightType highlightType = determineHighlightType(scanIssue);

        return manager.createProblemDescriptor(
                file,
                problemRange,
                description,
                highlightType,
                isOnTheFly,
                new CxOneAssistFix(scanIssue),
                new ViewDetailsFix(scanIssue),
                new IgnoreVulnerabilityFix(scanIssue),
                new IgnoreAllThisTypeFix(scanIssue)
        );
    }

    /**
     * Determines the highlight type for a specific scan detail.
     *
     * @param scanIssue the scan detail
     * @return the problem highlight type
     */
    private static ProblemHighlightType determineHighlightType(ScanIssue scanIssue) {
        return SEVERITY_HIGHLIGHT_TYPE_MAP.getOrDefault(scanIssue.getSeverity(), ProblemHighlightType.WEAK_WARNING);
    }


    /**
     * Retrieves the text range for a block of code defined by multiple locations.
     * If multiple locations are provided, it calculates the minimum start offset
     * and maximum end offset across all locations, trimming leading and trailing
     * whitespace. If only one location is provided, it returns the text range
     * for that specific line.
     *
     * @param document          the document from which the text range is to be retrieved
     * @param locations         the list of locations defining the block of code
     * @return a TextRange representing the start and end offsets of the block of code
     */
    public static TextRange getTextRange(Document document, List<Location> locations) {
        if (locations.size()>1) {
            // Ensure locations are sorted by line number (or by start index)
            locations.sort(Comparator.comparingInt(Location::getLine));

            Location first = locations.get(0);
            Location last = locations.get(locations.size() - 1);

            // FIRST location → minStartOffset
            TextRange firstRange = DevAssistUtils.getTextRangeForLine(document, first.getLine());
            int minStartOffset = firstRange.getStartOffset();

            // LAST location → maxEndOffset
            TextRange lastRange = DevAssistUtils.getTextRangeForLine(document, last.getLine());
            int maxEndOffset = lastRange.getStartOffset();

            return new TextRange(minStartOffset, maxEndOffset);
        } else {
            return DevAssistUtils.getTextRangeForLine(document, locations.get(0).getLine());
        }

    }

}
