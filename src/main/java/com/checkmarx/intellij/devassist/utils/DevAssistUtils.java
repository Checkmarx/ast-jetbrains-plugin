package com.checkmarx.intellij.devassist.utils;

import com.checkmarx.intellij.devassist.common.ScannerType;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;

import java.util.List;

public class DevAssistUtils {

    private DevAssistUtils() {
    }

    private static GlobalScannerController global() {
        return ApplicationManager.getApplication().getService(GlobalScannerController.class);
    }

    public static boolean isScannerActive(String engineName) {
        if (engineName == null) return false;
        try {
            if (new GlobalSettingsComponent().isValid()) {
                ScannerType kind = ScannerType.valueOf(engineName.toUpperCase());
                return global().isScannerGloballyEnabled(kind);
            }

        } catch (IllegalArgumentException ex) {
            return false;
        }
        return false;
    }

    /**
     * Retrieves the text range for the specified line in the given document, trimming leading and trailing whitespace.
     *
     * @param document          the document from which the specified line's text range is to be retrieved
     * @param problemLineNumber the 1-based line number for which the text range is needed
     * @return a TextRange representing the trimmed start and end offsets of the specified line
     */
    public static TextRange getTextRangeForLine(Document document, int problemLineNumber) {
        // Convert to 0-based index for document API
        int lineIndex = problemLineNumber - 1;

        // Get the exact offsets for this line
        int lineStartOffset = document.getLineStartOffset(lineIndex);
        int lineEndOffset = document.getLineEndOffset(lineIndex);

        // Get the line text and trim whitespace for highlighting
        CharSequence chars = document.getCharsSequence();

        // Calculate leading spaces
        int trimmedStartOffset = lineStartOffset;
        while (trimmedStartOffset < lineEndOffset && Character.isWhitespace(chars.charAt(trimmedStartOffset))) {
            trimmedStartOffset++;
        }
        // Calculate trailing spaces
        int trimmedEndOffset = lineEndOffset;
        while (trimmedEndOffset > trimmedStartOffset && Character.isWhitespace(chars.charAt(trimmedEndOffset - 1))) {
            trimmedEndOffset--;
        }
        // Ensure a valid range (fallback to original if the line is all whitespace)
        if (trimmedStartOffset >= trimmedEndOffset) {
            return new TextRange(lineStartOffset, lineEndOffset);
        }
        return new TextRange(trimmedStartOffset, trimmedEndOffset);
    }

    /**
     * Checks if the given line number is out of range for the document.
     *
     * @param lineNumber the line number to check (1-based)
     * @param document   the document
     * @return true if the line number is out of range, false otherwise
     */
    public static boolean isLineOutOfRange(int lineNumber, Document document) {
        return lineNumber <= 0 || lineNumber > document.getLineCount();
    }

    public static void addToCxOneFindings(PsiFile file, List<ScanIssue> problemsList) {
        ProblemHolderService.getInstance(file.getProject())
                .addProblems(file.getVirtualFile().getPath(), problemsList);
    }
}
