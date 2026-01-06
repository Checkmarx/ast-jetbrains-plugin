package com.checkmarx.intellij.devassist.utils;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.configuration.GlobalScannerController;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.model.Vulnerability;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.util.SeverityLevel;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static java.lang.String.format;

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


    public static String getContainerTool() {
        return GlobalSettingsState.getInstance().getContainersTool();
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
     * @param textToCopy          the text to copy
     * @param project             the project in which the notification should be shown
     * @param notificationTitle   the title of the notification
     * @param notificationContent the content of the notification
     */
    public static boolean copyToClipboardWithNotification(@NotNull String textToCopy, String notificationTitle,
                                                          String notificationContent, Project project) {
        try {
            ApplicationManager.getApplication().invokeLater(() -> {
                CopyPasteManager.getInstance().setContents(new StringSelection(textToCopy));
                Utils.showNotification(notificationTitle, notificationContent,
                        NotificationType.INFORMATION,
                        project);
            });
            return true;
        } catch (Exception exception) {
            LOGGER.debug("Failed to copy text to clipboard: ", exception);
            Utils.showNotification(notificationTitle, "Failed to copy text to clipboard.", NotificationType.ERROR, null);
            return false;
        }
    }

    /**
     * Gets the PsiElement at the start of the specified line number in the given PsiFile and Document.
     *
     * @param file       PsiFile
     * @param document   Document
     * @param lineNumber line number
     * @return PsiElement
     */
    public static PsiElement getPsiElement(PsiFile file, Document document, int lineNumber) {
        try {
            return file.findElementAt(document.getLineStartOffset(lineNumber - 1)); // Convert to 0-based index
        } catch (Exception e) {
            LOGGER.warn(format("Exception occurred while getting PsiElement for line number: %s", lineNumber), e);
            return null;
        }
    }

    /**
     * Generate a unique id for scan issue.
     *
     * @return a unique id
     */
    public static String generateUniqueId(int line, String title, String description) {
        String input = line + title + description;
        return DevAssistUtils.encodeBase64(input);
    }

    public static boolean isDockerComposeFile(@NotNull String filePath) {
        return Paths.get(filePath).getFileName().toString().toLowerCase().contains(DevAssistConstants.DOCKER_COMPOSE);
    }

    public static boolean isDockerFile(@NotNull String filePath) {
        return Paths.get(filePath).getFileName().toString().toLowerCase().contains(DevAssistConstants.DOCKERFILE);
    }

    public static String getFileExtension(@NotNull PsiFile psiFile) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (!vFile.exists()) {
            return null;
        }
        return vFile.getExtension();
    }

    public static boolean isYamlFile(@NotNull PsiFile psiFile) {
        String fileExtension = DevAssistUtils.getFileExtension(psiFile);
        return Objects.nonNull(fileExtension) && DevAssistConstants.CONTAINER_HELM_EXTENSION.contains(fileExtension.toLowerCase());
    }

    /**
     * Encode the input string using Base64.
     *
     * @param input String to be encoded
     * @return Base64 encoded string
     */
    public static String encodeBase64(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Determines the severity of an issue based on precedence by comparing the given severity
     * level with the severities of issues in the provided list. It returns the least severe
     * level that has a higher precedence than the provided severity.
     *
     * @param scanIssueList the list of {@code ScanIssue} objects, each containing a severity level
     * @param severity      the severity level to compare against, such as "Critical", "High", "Medium", etc.
     * @return the severity level from the list that has the highest precedence (lower number),
     * or the provided {@code severity} if no such severity exists in the list
     */
    public static String getSeverityBasedOnPrecedence(List<ScanIssue> scanIssueList, String severity) {
        int existingSeverityPrecedence = SeverityLevel.fromValue(severity).getPrecedence();
        return scanIssueList.stream()
                .map(ScanIssue::getSeverity)
                .map(SeverityLevel::fromValue)
                .filter(level -> level.getPrecedence() < existingSeverityPrecedence)
                .min(Comparator.comparingInt(SeverityLevel::getPrecedence))
                .map(SeverityLevel::getSeverity)
                .orElse(severity);
    }

    /**
     * Returns the vulnerability details for the given vulnerability id.
     *
     * @param scanIssue       scan issue containing vulnerabilities details
     * @param vulnerabilityId - vulnerability id
     * @return Vulnerability - vulnerability details
     */
    public static Vulnerability getVulnerabilityDetails(ScanIssue scanIssue, String vulnerabilityId) {
        if (Objects.isNull(scanIssue.getVulnerabilities()) || scanIssue.getVulnerabilities().isEmpty()) {
            LOGGER.warn(format("No vulnerabilities found in scan issue object for scan engine: %s.", scanIssue.getScanEngine().name()));
            return null;
        }
        return scanIssue.getVulnerabilities().stream()
                .filter(vulnerability -> vulnerability.getVulnerabilityId().equals(vulnerabilityId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Extracts the prefix from the input string
     * (e.g., "/AIAssistantInput" from "/AIAssistantInput-f85ebab5-dca9-4805-a452-2e189acff4b3.chatInput")
     * and checks if it is present in the AI assistant generated files list.
     *
     * @param input      the input string
     * @param prefixList the list of prefixes to check against
     * @return true if the prefix is present in the list, false otherwise
     */
    public static boolean isAIAssistantEvent(String input, List<String> prefixList) {
        if (input == null || prefixList == null) return false;
        int dashIndex = input.indexOf('-');
        String prefix = dashIndex > 0 ? input.substring(0, dashIndex) : input;
        return prefixList.contains(prefix);
    }

    /**
     * Get the currently selected file in the editor, if its not found then get most recently focused open file
     *
     * @param project - currently open project
     * @return VirtualFile
     */
    public static VirtualFile getCurrentSelectedFile(Project project) {
        if (isProjectDisposed(project)) {
            LOGGER.warn("Project is disposed of. Cannot get current selected file.");
            return null;
        }
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        if (Objects.isNull(fileEditorManager)) {
            LOGGER.warn("File editor manager is null. Cannot get current selected file.");
            return null;
        }
        FileEditor editor = fileEditorManager.getSelectedEditor();
        if (Objects.isNull(editor) || Objects.isNull(editor.getFile())) {
            LOGGER.warn("Selected file editor is null or no active file in the editor. Cannot get selected active file.");
            VirtualFile[] virtualFiles = getCurrentOpenFiles(project);
            return virtualFiles.length > 0 ? virtualFiles[0] : null; // return most recently focused an open file
        }
        return editor.getFile();
    }

    /**
     * Get the currently active file in the editor
     *
     * @param project - currently open project
     * @return VirtualFile[]
     */
    public static VirtualFile[] getCurrentOpenFiles(Project project) {
        try {
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            if (Objects.isNull(fileEditorManager)) {
                LOGGER.warn("File editor manager is null. Cannot get current active file.");
                return new VirtualFile[0];
            }
            return fileEditorManager.getSelectedFiles();
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while getting current active file.", e);
            return new VirtualFile[0];
        }
    }

    /**
     * Checks if the project is disposed of.
     *
     * @param project - Jetbrains project
     * @return true if the project is disposed, false otherwise.
     */
    public static boolean isProjectDisposed(Project project) {
        return Objects.isNull(project) || project.isDisposed();
    }

    /**
     * Checks if the virtual file is a GitHub Copilot or AI Assistant generated file.
     * E.g., On opening or typing prompt in the chat of GitHub Copilot, AI Assistant it's generating
     * the fake file with the name Dummy.txt, or AIAssistant so ignoring those files to be scanned.
     *
     * @param filePath - VirtualFile path.
     * @return true if the file is a GitHub Copilot/AI agent - generated file, false otherwise.
     */
    public static boolean isAIAgentEvent(String filePath) {
        boolean isFilePathMatched = DevAssistConstants.AI_AGENT_FILES.stream()
                .anyMatch(agentFile -> agentFile.equals(filePath));
        if (!isFilePathMatched) {
            return DevAssistUtils.isAIAssistantEvent(filePath, DevAssistConstants.AI_AGENT_FILES);
        }
        return true;
    }
}
