package com.checkmarx.intellij.devassist.ignore;

import com.checkmarx.intellij.Utils;
import com.checkmarx.intellij.devassist.model.ScanIssue;
import com.checkmarx.intellij.devassist.problems.ProblemHolderService;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Project-level service.
 * Manages the ignore file (.checkmarxIgnored) within the project's workspace.
 * Handles reading, writing, and updating ignore entries.
 * Monitors the ignore file for changes and updates internal state accordingly.
 * Provides methods to ignore issues and update temporary ignore lists.
 */
@Service(Service.Level.PROJECT)
public final class IgnoreFileManager {

    private static final Logger LOGGER = Utils.getLogger(IgnoreFileManager.class);
    private static boolean skipFileWatcherForTests = false;
    private final Project project;
    private String workspacePath = "";
    private String workspaceRootPath = "";
    private Map<String, IgnoreEntry> ignoreData = new HashMap<>();
    private final Map<String, String> scannedFileMap = new HashMap<>();
    private Map<String, IgnoreEntry> previousIgnoreData = new HashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final Topic<IgnoreFileManager.IgnoreListener> IGNORE_TOPIC = new Topic<>("IGNORE_LIST_UPDATED", IgnoreFileManager.IgnoreListener.class);

    public interface IgnoreListener {
        void onIgnoreUpdated();
    }

    public static IgnoreFileManager getInstance(Project project) {
        return project.getService(IgnoreFileManager.class);
    }

    public IgnoreFileManager(Project project) {
        this.project = project;
        String basePath = project.getBasePath();
        if (basePath != null) {
            this.workspaceRootPath = basePath;
            this.workspacePath = Paths.get(basePath, ".idea").toString(); // or ".checkmarx"
            ensureIgnoreFileExists();
            loadIgnoreData();
            this.previousIgnoreData = copyIgnoredata(ignoreData);
            if (!skipFileWatcherForTests) {
                startFileWatcher();
            }
        }
    }

    public void updateIgnoreData(String vulnerabilityKey, IgnoreEntry newData) {
        if (newData == null) return;
        ignoreData.put(vulnerabilityKey, newData);
        saveIgnoreFile();
        updateIgnoreTempList();
    }

    /**
     * Ensures the ignored file exists;
     * Creates it if missing.
     * Logs a warning if creation fails.
     */

    private void ensureIgnoreFileExists() {
        try {
            Path dir = Paths.get(workspacePath);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path ignoreFile = getIgnoreFilePath();
            if (!Files.exists(ignoreFile)) {
                Files.write(ignoreFile, "{}\n".getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to ensure ignore file exists", e);
        }
    }

    public void loadIgnoreData() {
        Path ignoreFile = getIgnoreFilePath();
        if (!Files.exists(ignoreFile)) {
            LOGGER.info(String.format("RTS-Ignore: Ignore file doesn't exist: %s", ignoreFile));
            ignoreData = new HashMap<>();
            return;
        }
        try (InputStream inputStream = Files.newInputStream(ignoreFile)) {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, IgnoreEntry> data = mapper.readValue(inputStream,
                    new TypeReference<Map<String, IgnoreEntry>>() {});
            ignoreData.clear();
            ignoreData.putAll(data);
        } catch (IOException e) {
            LOGGER.warn("Failed to read ignore file: " + ignoreFile, e);
            ignoreData = new HashMap<>();
        }
    }


    /**
     * Returns all ignore entries.
     * @return list of ignore entries.
     */
    public List<IgnoreEntry> getAllIgnoreEntries() {
        return new ArrayList<>(ignoreData.values());
    }

    /**
     * Returns the ignore data map for this project.
     * This is an instance method to ensure project-level isolation.
     * @return the ignore data map
     */
    public Map<String, IgnoreEntry> getIgnoreData() {
        return ignoreData;
    }

    /**
     * Saves the current ignore data to the ignore file.
     * Writes the ignore data as formatted JSON to the file specified by {@link #getIgnoreFilePath()}.
     * Creates a new file if it doesn't exist, or truncates the existing file.
     * Notifies all subscribers about the update via the message bus.
     * Logs a warning if saving fails.
     */
    private void saveIgnoreFile() {
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(ignoreData); // implement
            Files.writeString(getIgnoreFilePath(), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            project.getMessageBus().syncPublisher(IGNORE_TOPIC).onIgnoreUpdated();
        } catch (IOException e) {
            LOGGER.warn("RTS-Ignore: Exception occurred while adding ignore entry into file", e);
        }
    }


    /**
     * Updates the temporary ignore list file based on active ignore entries.
     * Creates a list of temporary items from active ignore entries, categorized by their type (OSS, Secrets, IAC, Containers, ASCA).
     * For each entry type:
     * - OSS: adds package manager, name and version
     * - Secrets: adds package name and secret value
     * - IAC: adds package name and similarity ID
     * - Containers: adds image name and image tag
     * - ASCA: adds file name, line number and rule ID for each active file
     * The temporary list is then saved to a JSON file at the path specified by {@link #getTempListPath()}.
     */
    public void updateIgnoreTempList() {
        List<TempItem> tempList = new ArrayList<>();
        LOGGER.debug(String.format("RTS-Ignore: Updating temp list with %d ignore entries", ignoreData.size()));
        for (IgnoreEntry entry : ignoreData.values()) {
            boolean hasActive = entry.files.stream().anyMatch(f -> f.active);
            if (!hasActive) continue;
            switch (entry.type) {
                case OSS:
                    tempList.add(TempItem.forOss(entry.packageManager, entry.packageName, entry.packageVersion));
                    break;
                case SECRETS:
                    tempList.add(TempItem.forSecret(entry.packageName, entry.secretValue));
                    break;
                case IAC:
                    tempList.add(TempItem.forIac(entry.packageName, entry.similarityId));
                    break;
                case CONTAINERS:
                    tempList.add(TempItem.forContainer(entry.imageName, entry.imageTag));
                    break;
                case ASCA:
                    for (IgnoreEntry.FileReference file : entry.files) {
                        if (!file.active) continue;
                        String originalPath = Paths.get(workspaceRootPath, file.path).toAbsolutePath().toString();
                        String scannedTempPath = scannedFileMap.getOrDefault(originalPath, originalPath);
                        tempList.add(TempItem.forAsca(
                                Paths.get(scannedTempPath).getFileName().toString(),
                                file.line,
                                entry.ruleId
                                ));
                    }
                    break;
                default:
                    break;
            }
        }
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(tempList);
            Files.writeString(getTempListPath(), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error(String.format("RTS-Ignore: Failed to update temp list: %s", e.getMessage()));
        }
    }

    /**
     * Revives a previously ignored package by setting all its file references to inactive.
     * This makes the vulnerability visible again in future scans.
     *
     * @param entryToRevive The unique key identifying the ignored package
     * @return true if the package was found and revived, false otherwise
     */
    public boolean reviveEntry(IgnoreEntry entryToRevive) {
        String entryKey = ignoreData.entrySet().stream()
                .filter(e ->  matchesEntry(e.getValue(), entryToRevive))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (entryKey == null) {
            LOGGER.warn("RTS-Ignore: Entry not found in ignoreData map");
            return false;
        }
        IgnoreEntry actualEntry = ignoreData.get(entryKey);
        String packageName = entryToRevive.getPackageName(); // Save before modification
        for (IgnoreEntry.FileReference file : actualEntry.getFiles()) {
            file.setActive(false);
        }
        saveIgnoreFile();
        updateIgnoreTempList();
        handleFileChange();
        project.getMessageBus().syncPublisher(IGNORE_TOPIC).onIgnoreUpdated();
        LOGGER.info("RTS-Ignore: Revived package: " + packageName);
        return true;
    }


    public Path getIgnoreFilePath() {
        return Paths.get(workspacePath, ".checkmarxIgnored");
    }

    /**
     * Deletes the ignore file (.checkmarxIgnored) and the temporary ignore list file.
     * Called when the user no longer has a valid license (platform-only license).
     * This ensures that ignored findings are cleared when the feature is not available.
     */
    public void deleteIgnoreFiles() {
        try {
            Path ignoreFilePath = getIgnoreFilePath();
            if (Files.exists(ignoreFilePath)) {
                Files.delete(ignoreFilePath);
                LOGGER.info("RTS-Ignore: Deleted ignore file at " + ignoreFilePath);
            }

            Path tempListPath = Paths.get(workspacePath, ".checkmarxIgnoredTempList.json");
            if (Files.exists(tempListPath)) {
                Files.delete(tempListPath);
                LOGGER.info("RTS-Ignore: Deleted temp list file at " + tempListPath);
            }

            // Clear in-memory data
            ignoreData.clear();
            previousIgnoreData.clear();

            // Notify listeners that ignore data has changed
            project.getMessageBus().syncPublisher(IGNORE_TOPIC).onIgnoreUpdated();
        } catch (IOException e) {
            LOGGER.error("RTS-Ignore: Failed to delete ignore files: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the path to the temporary ignore list.
     * Creates the file if it doesn't exist.
     * @return path to the temporary ignore list.
     *
     */
    public Path getTempListPath() {
        Path tempListPath = Paths.get(workspacePath, ".checkmarxIgnoredTempList.json");
        if (Files.exists(tempListPath)) {
            try {
                // Validate it's a valid JSON array
                if (Files.readString(tempListPath).trim().isEmpty()) {
                    Files.writeString(tempListPath, "[]", StandardCharsets.UTF_8,
                            StandardOpenOption.TRUNCATE_EXISTING);
                }
                return tempListPath;
            } catch (IOException e) {
                LOGGER.warn("Failed to validate temp list: " + tempListPath, e);
                createEmptyTempList(tempListPath);
            }
        } else {
            createEmptyTempList(tempListPath);
        }
        return tempListPath;  // Guaranteed to exist and contain []
    }

    private void createEmptyTempList(Path tempListPath) {
        try {
            Files.createDirectories(tempListPath.getParent());
            Files.writeString(tempListPath, "[]", StandardCharsets.UTF_8);
            LOGGER.debug(String.format("RTS-Ignore: Created empty temp list at %s", tempListPath));
        } catch (IOException e) {
            LOGGER.error("Failed to create empty temp list", e);
        }
    }

    /**
     * normalizes the given file path to be relative to the project's workspace root.
     * @param filePath
     * @return
     */
    public String normalizePath(String filePath) {
        return Path.of(workspaceRootPath)
                .relativize(Paths.get(filePath))
                .toString()
                .replace("\\", "/");
    }

    /**
     * Callback method to keep watch on the temp files edited directly.
     */
    private void startFileWatcher() {
        if (ApplicationManager.getApplication() == null) {
            return;
        }
        VirtualFile ignoreFile = LocalFileSystem.getInstance().findFileByIoFile(getIgnoreFilePath().toFile());
        if (ignoreFile == null) {
            return;
        }
        VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
            @Override
            public void contentsChanged(@NotNull VirtualFileEvent event) {
                if (event.getFile().equals(ignoreFile)) {
                    handleFileChange();
                }
            }
        }, project);
    }

    private void handleFileChange() {
        loadIgnoreData();
        detectAndHandleActiveChanges();
        previousIgnoreData = copyIgnoredata(ignoreData);
        project.getMessageBus().syncPublisher(IGNORE_TOPIC).onIgnoreUpdated();
    }


    private void detectAndHandleActiveChanges() {
        List<ActiveFile> previousActiveFiles = getActiveFilesList(previousIgnoreData);
        List<ActiveFile> currentActiveFiles = getActiveFilesList(ignoreData);

        List<ActiveFile> deactivatedFiles = previousActiveFiles.stream()
                .filter(prev -> currentActiveFiles.stream()
                        .noneMatch(cur -> cur.packageKey.equals(prev.packageKey) && cur.path.equals(prev.path)))
                .collect(Collectors.toList());
        if (!deactivatedFiles.isEmpty()) {
            for (ActiveFile f : deactivatedFiles) {
                removeIgnoredEntryWithoutTempUpdate(f.packageKey, f.path);
            }
            updateIgnoreTempList();
        }
            // Remove entries where all files are inactive
            List<String> keysToRemove = new ArrayList<>();
            for (Map.Entry<String, IgnoreEntry> entry : ignoreData.entrySet()) {
                boolean hasActive = entry.getValue().files.stream().anyMatch(f -> f.active);
                if (!hasActive) {
                    keysToRemove.add(entry.getKey());
                }
            }
            if (!keysToRemove.isEmpty()) {
                for (String key : keysToRemove) {
                    ignoreData.remove(key);
                }
                saveIgnoreFile();
            }

    }

    private static final class ActiveFile {
        final String packageKey;
        final String path;

        ActiveFile(String packageKey, String path) {
            this.packageKey = packageKey;
            this.path = path;
        }
    }

    private List<ActiveFile> getActiveFilesList(Map<String, IgnoreEntry> data) {
        List<ActiveFile> result = new ArrayList<>();
        for (Map.Entry<String, IgnoreEntry> e : data.entrySet()) {
            for (IgnoreEntry.FileReference fileRef : e.getValue().files) {
                if (fileRef.active) {
                    result.add(new ActiveFile(e.getKey(), fileRef.path));
                }
            }
        }
        return result;
    }

    private void removeIgnoredEntryWithoutTempUpdate(String packageKey, String filePath) {
        IgnoreEntry entry = ignoreData.get(packageKey);
        if (entry == null) return;
        entry.files.removeIf(fileRef -> fileRef.path.equals(filePath));
        if (entry.files.isEmpty()) {
            ignoreData.remove(packageKey);
        }
        saveIgnoreFile();

    }

    private Map<String, IgnoreEntry> copyIgnoredata(Map<String, IgnoreEntry> src) {
        // Deep copy via JSON round-trip
        try {
            String json = MAPPER.writeValueAsString(src);
            return MAPPER.readValue(json, new TypeReference<Map<String, IgnoreEntry>>() {});
        } catch (IOException e) {
            LOGGER.warn("Failed to deep copy ignoreData, falling back to shallow copy", e);
            return new HashMap<>(src);
        }
    }

    // Helper method to match entries by properties
    public boolean matchesEntry(IgnoreEntry entry1, IgnoreEntry entry2) {
        if (entry1.getType() != entry2.getType()) return false;
        // Match based on type-specific unique identifiers
        switch (entry1.getType()) {
            case OSS:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getPackageVersion(), entry2.getPackageVersion()) &&
                        Objects.equals(entry1.getPackageManager(), entry2.getPackageManager());
            case CONTAINERS:
                return Objects.equals(entry1.getImageName(), entry2.getImageName()) &&
                        Objects.equals(entry1.getImageTag(), entry2.getImageTag());
            case SECRETS:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getSecretValue(), entry2.getSecretValue());
            case IAC:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getSimilarityId(), entry2.getSimilarityId());
            case ASCA:
                return Objects.equals(entry1.getPackageName(), entry2.getPackageName()) &&
                        Objects.equals(entry1.getRuleId(), entry2.getRuleId());
            default:
                return false;
        }
    }

    /**
     * Saves the current ignore data to disk.
     * This is a public wrapper for the private saveIgnoreFile method.
     * Used when ignore data is modified directly (e.g., line number updates).
     */
    public void saveIgnoreDataToDisk() {
        saveIgnoreFile();
        updateIgnoreTempList();
    }
}
