package com.checkmarx.intellij.devassist.utils;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;

/**
 * Utility class for common operations.
 */
public class DevAssistUtils {
    private static final Logger LOGGER = Utils.getLogger(DevAssistUtils.class);


    private DevAssistUtils() {
        // Private constructor to prevent instantiation
    }

    public static GlobalScannerController globalScannerController() {
        return GlobalScannerController.getInstance();
    }

    /**
     * Checks if the scanner with the given name is active.
     *
     * @param engineName the name of the scanner to check
     * @return true if the scanner is active, false otherwise
     */
    public static boolean isScannerActive(String engineName) {
        if (engineName == null) return false;
        try {
            if (GlobalSettingsState.getInstance().isAuthenticated()) {
                ScanEngine kind = ScanEngine.valueOf(engineName.toUpperCase());
                return globalScannerController().isScannerGloballyEnabled(kind);
            }
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return false;
    }

    /**
     * Checks if any scanner is enabled.
     *
     * @return true if any scanner is enabled, false otherwise
     */
    public static boolean isAnyScannerEnabled() {
        return globalScannerController().checkAnyScannerEnabled();
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

    /**
     * Wraps the given text into lines at word boundaries without exceeding a defined maximum line length.
     * If a word exceeds the specified line length, it will be placed on a new line.
     *
     * @param text the input text to be wrapped into lines
     * @return the text with line breaks added to wrap it at word boundaries
     */
    public static String wrapTextAtWord(String text, int maxLineLength) {
        StringBuilder result = new StringBuilder();
        int lineLength = 0;
        for (String word : text.split(" ")) {
            if (lineLength > 0) {
                // Add a space before the word if not at the start of a line
                result.append(" ");
                lineLength++;
            }
            if (lineLength + word.length() > maxLineLength) {
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

    /**
     * Checks if the scan package is a problem.
     *
     * @param severity - the severity of the scan package e.g. "high", "medium", "low", etc.
     * @return true if the scan package is a problem, false otherwise
     */
    public static boolean isProblem(String severity) {
        if (severity.equalsIgnoreCase(SeverityLevel.OK.getSeverity())) {
            return false;
        } else return !severity.equalsIgnoreCase(SeverityLevel.UNKNOWN.getSeverity());
    }

    /**
     * Returns a resource URL string suitable for embedding in an <img src='...'> tag
     * for the given simple icon key (e.g. "critical", "high", "package", "malicious").
     *
     * @param iconPath severity or logical icon path
     * @return external form URL or empty string if not found
     */
    public static String themeBasedPNGIconForHtmlImage(String iconPath) {
        if (iconPath == null || iconPath.isEmpty()) {
            return "";
        }
        boolean dark = isDarkTheme();
        // Try the dark variant first if in a dark theme.
        String candidate = iconPath + (dark ? "_dark" : "") + ".png";
        URL res = DevAssistUtils.class.getResource(candidate);
        if (res == null && dark) {
            // Fallback to the light variant.
            candidate = iconPath + ".png";
            res = DevAssistUtils.class.getResource(candidate);
        }
        return res != null ? res.toExternalForm() : "";
    }

    /**
     * Checks if the IDE is in a dark theme.
     *
     * @return true if in a dark theme, false otherwise
     */
    public static boolean isDarkTheme() {
        return UIUtil.isUnderDarcula();
    }

    /**
     * Returns the full textual content of the given {@link PsiFile}, including both
     * unsaved in-editor changes and updates made to the underlying file outside the IDE.
     * <p>
     * This method first attempts to read from the associated {@link Document}, ensuring
     * that any unsaved modifications in the editor are included. If no document is
     * associated with the PSI file, the content is loaded directly from the
     * {@link VirtualFile}, ensuring externally modified file content is retrieved.
     * <p>
     * All operations are performed inside a read action as required by the IntelliJ Platform.
     *
     * @param file the PSI file whose content should be read
     * @return the full file text, or {@code null} if the file cannot be accessed
     */

    public static String getFileContent(@NotNull PsiFile file) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {

            Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
            if (document != null) {
                return document.getText();
            }
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile == null) {
                LOGGER.warn("Virtual file is null for PsiFile: " + file.getName());
                return null;
            }
            try {
                return VfsUtil.loadText(virtualFile);
            } catch (IOException e) {
                LOGGER.warn("Failed to load content from file: " + virtualFile.getPath(), e);
                return null;
            }
        });
    }


    /**
     * Copies the given text to the system clipboard and shows a notification on success.
     *
     * @param text the text to copy
     */
    public static boolean copyToClipboardWithNotification(@NotNull String text, String notificationTitle,
                                                          String content, Project project) {
        StringSelection stringSelection = new StringSelection(text);
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            Utils.showNotification(notificationTitle, content,
                    NotificationType.INFORMATION,
                    project);
            return true;
        } catch (Exception exception) {
            LOGGER.debug("Failed to copy remediation text to clipboard: ", exception);
            Utils.showNotification(notificationTitle, "Failed to copy text to clipboard.", NotificationType.ERROR, null);
            return false;
        }
    }

}
