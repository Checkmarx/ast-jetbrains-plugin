package com.checkmarx.intellij.devassist.problems;

import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.CxIcons;
import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.Location;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.utils.DevAssistUtils;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ProblemDecorator class responsible to provides utility methods for managing problem, highlighting and gutter icons.
 */
@Getter
public class ProblemDecorator {

    private static final Logger LOGGER = Utils.getLogger(ProblemDecorator.class);
    private final Map<String, Integer> severityHighlighterLayerMap = new HashMap<>();

    public ProblemDecorator() {
        initSeverityHighlighterLayerMap();
    }

    /**
     * Initializes the mapping from severity levels to highlighter layers.
     */
    private void initSeverityHighlighterLayerMap() {
        severityHighlighterLayerMap.put(Constants.MALICIOUS_SEVERITY, HighlighterLayer.ERROR);
        severityHighlighterLayerMap.put(Constants.CRITICAL_SEVERITY, HighlighterLayer.ERROR);
        severityHighlighterLayerMap.put(Constants.HIGH_SEVERITY, HighlighterLayer.ERROR);
        severityHighlighterLayerMap.put(Constants.MEDIUM_SEVERITY, HighlighterLayer.WARNING);
        severityHighlighterLayerMap.put(Constants.LOW_SEVERITY, HighlighterLayer.WEAK_WARNING);
    }

    /**
     * Adds a gutter icon at the line of the given PsiElement.
     */
    public void highlightLineAddGutterIconForProblem(@NotNull Project project, @NotNull PsiFile file,
                                                     ScanIssue scanIssue, boolean isProblem, int problemLineNumber) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (editor == null) return;

            if (!Objects.equals(editor.getDocument(), PsiDocumentManager.getInstance(project).getDocument(file))) {
                // Only decorate the active editor of this file
                return;
            }
            MarkupModel markupModel = editor.getMarkupModel();
            boolean isFirstLocation = true;
            for (Location location : scanIssue.getLocations()) {
                int targetLine = location.getLine();
                highlightLocationInEditor(editor, markupModel, targetLine, scanIssue, isFirstLocation, isProblem, problemLineNumber);
                isFirstLocation = false;
            }
        });
    }

    /**
     * Highlights a specific location in the editor and optionally adds a gutter icon.
     *
     * @param editor        the editor instance
     * @param markupModel   the markup model for highlighting
     * @param targetLine    the line number to highlight (1-based)
     * @param scanIssue     the scan package containing severity information
     * @param addGutterIcon whether to add a gutter icon for this location
     */
    private void highlightLocationInEditor(Editor editor, MarkupModel markupModel, int targetLine,
                                           ScanIssue scanIssue, boolean addGutterIcon, boolean isProblem, int problemLineNumber) {
        try {
            TextRange textRange = DevAssistUtils.getTextRangeForLine(editor.getDocument(), targetLine);
            TextAttributes textAttributes = createTextAttributes(scanIssue.getSeverity());

            RangeHighlighter highlighter = markupModel.addLineHighlighter(
                    targetLine - 1, 0, null);

            if (isProblem) {
                highlighter = markupModel.addRangeHighlighter(
                        textRange.getStartOffset(),
                        textRange.getEndOffset(),
                        determineHighlighterLayer(scanIssue),
                        textAttributes,
                        HighlighterTargetArea.EXACT_RANGE
                );
            }
            boolean alreadyHasGutterIcon = isAlreadyHasGutterIcon(markupModel, editor, problemLineNumber);
            if (addGutterIcon && !alreadyHasGutterIcon) {
                addGutterIcon(highlighter, scanIssue.getSeverity());
            } else if (isProblem && alreadyHasGutterIcon) {
                // Update to most severe gutter icon if already exists for the same line
                updateMostSeverityGutterIcon(markupModel, highlighter, editor, scanIssue, problemLineNumber);
            }
        } catch (Exception e) {
            LOGGER.debug("RTS-Decorator: Exception occurred while highlighting line: {} , Exception: {} ",
                    targetLine, e.getMessage());
        }
    }

    /**
     * Creates text attributes for error highlighting with wave underscore effect.
     *
     * @return the configured text attributes
     */
    private TextAttributes createTextAttributes(String severity) {
        TextAttributes errorAttrs = EditorColorsManager.getInstance()
                .getGlobalScheme().getAttributes(getCodeInsightColors(severity));

        TextAttributes attr = new TextAttributes();
        attr.setEffectType(EffectType.WAVE_UNDERSCORE);
        attr.setEffectColor(errorAttrs.getEffectColor());
        attr.setForegroundColor(errorAttrs.getForegroundColor());
        attr.setBackgroundColor(null);
        return attr;
    }

    /**
     * Gets the CodeInsightColors key based on severity.
     *
     * @param severity the severity
     * @return the text attributes key for the given severity
     */
    private TextAttributesKey getCodeInsightColors(String severity) {
        if (severity.equalsIgnoreCase(SeverityLevel.MALICIOUS.getSeverity()) ||
                severity.equalsIgnoreCase(SeverityLevel.CRITICAL.getSeverity())
                || severity.equalsIgnoreCase(SeverityLevel.HIGH.getSeverity())) {
            return CodeInsightColors.ERRORS_ATTRIBUTES;
        } else if (severity.equalsIgnoreCase(SeverityLevel.MEDIUM.getSeverity())) {
            return CodeInsightColors.WARNINGS_ATTRIBUTES;
        } else {
            return CodeInsightColors.WEAK_WARNING_ATTRIBUTES;
        }
    }

    /**
     * Adds a gutter icon to the highlighter.
     *
     * @param highlighter the highlighter
     * @param severity    the severity
     */
    private void addGutterIcon(RangeHighlighter highlighter, String severity) {
        highlighter.setGutterIconRenderer(new GutterIconRenderer() {

            @Override
            public @NotNull Icon getIcon() {
                return getGutterIconBasedOnStatus(severity);
            }

            @Override
            public @NotNull Alignment getAlignment() {
                return Alignment.LEFT;
            }

            @Override
            public String getTooltipText() {
                return severity;
            }

            @Override
            public boolean equals(Object obj) {
                return obj == this;
            }

            @Override
            public int hashCode() {
                return System.identityHashCode(this);
            }
        });
    }

    /**
     * Checks if the highlighter already has a gutter icon for the given line.
     *
     * @param markupModel the markup model
     * @param editor      the editor
     * @param line        the line
     * @return true if the highlighter already has a gutter icon for the given line, false otherwise
     * @apiNote this method is particularly used to avoid adding duplicate gutter icons in the file for duplicate dependencies.
     */
    private boolean isAlreadyHasGutterIcon(MarkupModel markupModel, Editor editor, int line) {
        return Arrays.stream(markupModel.getAllHighlighters())
                .anyMatch(highlighter -> {
                    GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
                    if (renderer == null) return false;
                    int existingLine = editor.getDocument().getLineNumber(highlighter.getStartOffset()) + 1;
                    // Match if highlighter covers the same PSI element region
                    return existingLine == line;
                });
    }

    /**
     * Gets the gutter icon for the given severity.
     *
     * @param severity the severity
     * @return the severity icon
     */
    public Icon getGutterIconBasedOnStatus(String severity) {
        switch (SeverityLevel.fromValue(severity)) {
            case MALICIOUS:
                return CxIcons.Small.MALICIOUS;
            case CRITICAL:
                return CxIcons.Small.CRITICAL;
            case HIGH:
                return CxIcons.Small.HIGH;
            case MEDIUM:
                return CxIcons.Small.MEDIUM;
            case LOW:
                return CxIcons.Small.LOW;
            case OK:
                return CxIcons.Small.OK;
            default:
                return CxIcons.Small.UNKNOWN;
        }
    }

    /**
     * Determines the highlighter layer for a specific scan detail.
     *
     * @param scanIssue the scan detail
     * @return the highlighter layer
     */
    public Integer determineHighlighterLayer(ScanIssue scanIssue) {
        return severityHighlighterLayerMap.getOrDefault(scanIssue.getSeverity(), HighlighterLayer.WEAK_WARNING);
    }

    /**
     * Removes all existing gutter icons from the markup model in the given editor.
     *
     * @param project the file to remove the gutter icons from.
     */
    public static void removeAllGutterIcons(Project project) {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
                if (editor == null) return;

                MarkupModel markupModel = editor.getMarkupModel();
                if (markupModel.getAllHighlighters().length > 0) {
                    markupModel.removeAllHighlighters();
                }
            });
        } catch (Exception e) {
            LOGGER.debug("RTS-Decorator: Exception occurred while removing gutter icons for: {} ",
                    e.getMessage());
        }
    }

    /**
     * Restores problems for the given file.
     *
     * @param project       the project
     * @param psiFile       the psi file
     * @param scanIssueList the scan issue list
     */
    public void restoreGutterIcons(Project project, PsiFile psiFile, List<ScanIssue> scanIssueList, Document document) {
        removeAllGutterIcons(project);
        for (ScanIssue scanIssue : scanIssueList) {
            try {
                int problemLineNumber = scanIssue.getLocations().get(0).getLine();
                PsiElement elementAtLine = DevAssistUtils.getPsiElement(psiFile, document, problemLineNumber);
                if (Objects.isNull(elementAtLine)) {
                    LOGGER.debug("RTS-Decorator: Skipping to add gutter icon, Failed to find PSI element for line : {}",
                            problemLineNumber, scanIssue.getTitle());
                    continue;
                }
                boolean isProblem = DevAssistUtils.isProblem(scanIssue.getSeverity().toLowerCase());
                highlightLineAddGutterIconForProblem(project, psiFile, scanIssue, isProblem, problemLineNumber);
            } catch (Exception e) {
                LOGGER.debug("RTS-Decorator: Exception occurred while restoring gutter icons for: {} ",
                        psiFile.getName(), scanIssue.getTitle(), e.getMessage());
            }
        }

    }

    /**
     * Updates the most severe gutter icon for a specific line in the editor.
     * This method identifies the severity of scan issues related to a file and updates the gutter
     * icon to reflect the highest severity level encountered on that specific line.
     *
     * @param markupModel       the markup model for managing highlighters
     * @param highlighter       the range highlighter associated with the current issue
     * @param editor            the editor instance being used
     * @param scanIssue         the scan issue containing severity and other details
     * @param problemLineNumber the line number in the editor where the scan issue is located
     */
    private void updateMostSeverityGutterIcon(MarkupModel markupModel, RangeHighlighter highlighter, Editor editor, ScanIssue scanIssue, int problemLineNumber) {
        try {
            VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
            if (Objects.isNull(file)) {
                LOGGER.debug("RTS-Decorator: VirtualFile not found for the given editor.");
                return;
            }
            ProblemHolderService problemHolderService = ProblemHolderService.getInstance(Objects.requireNonNull(editor.getProject()));
            if (Objects.isNull(problemHolderService)) {
                LOGGER.debug("RTS-Decorator: ProblemHolderService not found for the given project.");
                return;
            }
            List<ScanIssue> scanIssueList = problemHolderService.getScanIssueByFile(file.getPath());
            if (scanIssueList.isEmpty()) {
                LOGGER.debug("RTS-Decorator: No scan issues found for the given file-path: %s.", file.getPath());
                return;
            }
            List<ScanIssue> sameLineIssueList = scanIssueList.stream()
                    .filter(issue -> issue.getLocations().get(0).getLine() == problemLineNumber
                            && !Objects.equals(issue.getScanIssueId(), scanIssue.getScanIssueId())
                            && !Objects.equals(issue.getSeverity(), scanIssue.getSeverity()))
                    .collect(Collectors.toList());

            String mostSeverity = !sameLineIssueList.isEmpty() ?
                    DevAssistUtils.getSeverityBasedOnPrecedence(sameLineIssueList, scanIssue.getSeverity())
                    : scanIssue.getSeverity();

            if (Objects.nonNull(mostSeverity) && !mostSeverity.isEmpty()
                    && !mostSeverity.equalsIgnoreCase(scanIssue.getSeverity())
                    && removeExistingGutterIconForLine(markupModel, editor, problemLineNumber)) {
                addGutterIcon(highlighter, mostSeverity);
                LOGGER.debug("RTS: Most severe issue severity: {} for the given scan issue: {}", mostSeverity, scanIssue.getTitle());
            }
        } catch (Exception exception) {
            LOGGER.debug("RTS: Exception occurred while retrieving most severity to update gutter icon.", exception);
        }
    }

    /**
     * Removes an existing gutter icon for a specific line in the editor.
     * Iterates through all highlighters associated with the given markup model
     * to find and remove the gutter icon on the specified line number.
     *
     * @param markupModel       the markup model containing highlighters
     * @param editor            the editor instance to access the document
     * @param problemLineNumber the 1-based line number where the gutter icon needs to be removed
     * @return true if a gutter icon was removed, false otherwise
     */
    private boolean removeExistingGutterIconForLine(MarkupModel markupModel, Editor editor, int problemLineNumber) {
        boolean removed = false;
        try {
            for (RangeHighlighter highlighter : markupModel.getAllHighlighters()) {
                GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
                if (renderer != null) {
                    int highlighterLine = editor.getDocument().getLineNumber(highlighter.getStartOffset()) + 1;
                    if (highlighterLine == problemLineNumber) {
                        highlighter.setGutterIconRenderer(null);
                        removed = true;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("RTS-Decorator: Exception occurred while removing gutter icons to update most severity icon for line: {} ",
                    problemLineNumber, e.getMessage());
            return false;
        }
        return removed;
    }
}
