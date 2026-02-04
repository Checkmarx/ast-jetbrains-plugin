package com.checkmarx.intellij.ast.test.unit.devassist.ignore;

import com.checkmarx.intellij.devassist.ignore.IgnoreEntry;
import com.checkmarx.intellij.devassist.ignore.IgnoreFileManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IgnoreFileManagerTest {

    private IgnoreFileManager manager;
    private Path workspacePath;


    @TempDir
    Path tempDir;

    @Mock
    Project project;

    @Mock
    MessageBus messageBus;

    @Mock
    IgnoreFileManager.IgnoreListener ignoreListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(project.getBasePath()).thenReturn(tempDir.toString());
        when(project.getMessageBus()).thenReturn(messageBus);
        when(project.getBasePath()).thenReturn(tempDir.toString());
        when(messageBus.syncPublisher(
                Mockito.<Topic<IgnoreFileManager.IgnoreListener>>any()
        )).thenReturn(ignoreListener);

        manager = new IgnoreFileManager(project);
    }



    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
        }
    }


    @Test
    void testEnsureIgnoreFileExistsAndLoadIgnoreData() {
        Path ignoreFile = manager.getIgnoreFilePath();
        assertTrue(Files.exists(ignoreFile));
        manager.updateIgnoreData("key1", new IgnoreEntry());
        manager.loadIgnoreData();
        assertNotNull(manager.getIgnoreData());
    }

    @Test
    void testUpdateIgnoreDataAndSaveIgnoreFile() {
        IgnoreEntry entry = new IgnoreEntry();
        manager.updateIgnoreData("vulnKey", entry);
        assertEquals(entry, manager.getIgnoreData().get("vulnKey"));
        Path ignoreFile = manager.getIgnoreFilePath();
        assertTrue(Files.exists(ignoreFile));
    }

    @Test
    void testGetAllIgnoreEntries() {
        IgnoreEntry entry = new IgnoreEntry();
        manager.updateIgnoreData("vulnKey", entry);
        List<IgnoreEntry> entries = manager.getAllIgnoreEntries();
        assertTrue(entries.contains(entry));
    }

    @Test
    void testUpdateTempListAndGetTempListPath() throws IOException {
        manager.updateIgnoreTempList();
        Path tempListPath = manager.getTempListPath();
        assertTrue(Files.exists(tempListPath));
        assertTrue(Files.readString(tempListPath).contains("["));
    }

    @Test
    void testNormalizePath() {
        Path file = tempDir.resolve("src/file.js");
        String normalized = manager.normalizePath(file.toString());

        assertEquals("src/file.js", normalized.replace("\\", "/"));
    }

    @Test
    void constructor_skipsFileWatcher_whenNoApplication() {
        // In unit tests, ApplicationManager.getApplication() == null
        new IgnoreFileManager(project);
    }

    // ========== REVIVE FUNCTIONALITY TESTS ==========

    @Test
    void testReviveEntry_setsAllFilesToInactive() {
        // Arrange
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("test-package");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        entry.setPackageManager("npm");
        entry.setPackageVersion("1.0.0");

        IgnoreEntry.FileReference file1 = new IgnoreEntry.FileReference("src/file1.js", true, 10);
        IgnoreEntry.FileReference file2 = new IgnoreEntry.FileReference("src/file2.js", true, 20);
        entry.setFiles(new ArrayList<>(Arrays.asList(file1, file2)));

        manager.updateIgnoreData("test-key", entry);

        // Act
        boolean result = manager.reviveEntry(entry);

        // Assert
        assertTrue(result, "Revive should succeed");
        // After revive, entries with all inactive files are removed from the map
        IgnoreEntry revivedEntry = manager.getIgnoreData().get("test-key");
        assertNull(revivedEntry, "Entry should be removed from map after all files become inactive");
    }

    @Test
    void testReviveEntry_returnsFalseForNonExistentEntry() {
        // Arrange
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("non-existent");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);

        // Act
        boolean result = manager.reviveEntry(entry);

        // Assert
        assertFalse(result, "Revive should fail for non-existent entry");
    }

    @Test
    void testReviveEntry_savesFileAfterRevive() throws IOException {
        // Arrange
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("test-package");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        entry.setPackageManager("npm");
        entry.setPackageVersion("1.0.0");

        IgnoreEntry.FileReference file = new IgnoreEntry.FileReference("src/file.js", true, 10);
        entry.setFiles(new ArrayList<>(List.of(file)));

        manager.updateIgnoreData("test-key", entry);

        // Act
        manager.reviveEntry(entry);

        // Assert
        // After revive, entry with all inactive files is removed from the map
        // So the file should be saved without this entry
        Path ignoreFile = manager.getIgnoreFilePath();
        String content = Files.readString(ignoreFile);
        // The entry should be removed from the file
        assertFalse(content.contains("test-package"), "Entry should be removed from file after revive");
    }

    @Test
    void testReviveEntry_matchesEntryByOssProperties() {
        // Arrange
        IgnoreEntry entry1 = new IgnoreEntry();
        entry1.setPackageName("lodash");
        entry1.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        entry1.setPackageManager("npm");
        entry1.setPackageVersion("4.17.21");
        entry1.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("file.js", true, 1))));

        manager.updateIgnoreData("oss-key", entry1);

        // Create a different instance with same properties
        IgnoreEntry entryToRevive = new IgnoreEntry();
        entryToRevive.setPackageName("lodash");
        entryToRevive.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        entryToRevive.setPackageManager("npm");
        entryToRevive.setPackageVersion("4.17.21");

        // Act
        boolean result = manager.reviveEntry(entryToRevive);

        // Assert
        assertTrue(result, "Should match entry by OSS properties");
        // Entry should be removed after all files become inactive
        assertNull(manager.getIgnoreData().get("oss-key"), "Entry should be removed from map");
    }

    @Test
    void testReviveEntry_matchesEntryByContainerProperties() {
        // Arrange
        IgnoreEntry entry = new IgnoreEntry();
        entry.setImageName("nginx");
        entry.setImageTag("1.19");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.CONTAINERS);
        entry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("Dockerfile", true, 1))));

        manager.updateIgnoreData("container-key", entry);

        IgnoreEntry entryToRevive = new IgnoreEntry();
        entryToRevive.setImageName("nginx");
        entryToRevive.setImageTag("1.19");
        entryToRevive.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.CONTAINERS);

        // Act
        boolean result = manager.reviveEntry(entryToRevive);

        // Assert
        assertTrue(result, "Should match entry by Container properties");
        // Entry should be removed after all files become inactive
        assertNull(manager.getIgnoreData().get("container-key"), "Entry should be removed from map");
    }

    @Test
    void testReviveEntry_matchesEntryBySecretsProperties() {
        // Arrange
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("AWS_KEY");
        entry.setSecretValue("secret123");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.SECRETS);
        entry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("config.js", true, 5))));

        manager.updateIgnoreData("secret-key", entry);

        IgnoreEntry entryToRevive = new IgnoreEntry();
        entryToRevive.setPackageName("AWS_KEY");
        entryToRevive.setSecretValue("secret123");
        entryToRevive.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.SECRETS);

        // Act
        boolean result = manager.reviveEntry(entryToRevive);

        // Assert
        assertTrue(result, "Should match entry by Secrets properties");
        // Entry should be removed after all files become inactive
        assertNull(manager.getIgnoreData().get("secret-key"), "Entry should be removed from map");
    }

    @Test
    void testReviveEntry_matchesEntryByIacProperties() {
        // Arrange
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("S3 Bucket Public");
        entry.setSimilarityId("sim-123");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.IAC);
        entry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("main.tf", true, 15))));

        manager.updateIgnoreData("iac-key", entry);

        IgnoreEntry entryToRevive = new IgnoreEntry();
        entryToRevive.setPackageName("S3 Bucket Public");
        entryToRevive.setSimilarityId("sim-123");
        entryToRevive.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.IAC);

        // Act
        boolean result = manager.reviveEntry(entryToRevive);

        // Assert
        assertTrue(result, "Should match entry by IAC properties");
        // Entry should be removed after all files become inactive
        assertNull(manager.getIgnoreData().get("iac-key"), "Entry should be removed from map");
    }

    @Test
    void testReviveEntry_matchesEntryByAscaProperties() {
        // Arrange
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("SQL Injection");
        entry.setRuleId(1077);
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.ASCA);
        entry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("app.java", true, 42))));

        manager.updateIgnoreData("asca-key", entry);

        IgnoreEntry entryToRevive = new IgnoreEntry();
        entryToRevive.setPackageName("SQL Injection");
        entryToRevive.setRuleId(1077);
        entryToRevive.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.ASCA);

        // Act
        boolean result = manager.reviveEntry(entryToRevive);

        // Assert
        assertTrue(result, "Should match entry by ASCA properties");
        // Entry should be removed after all files become inactive
        assertNull(manager.getIgnoreData().get("asca-key"), "Entry should be removed from map");
    }

    @Test
    void testUpdateIgnoreTempList_skipsEntriesWithNoActiveFiles() throws IOException {
        // Arrange
        IgnoreEntry activeEntry = new IgnoreEntry();
        activeEntry.setPackageName("active-package");
        activeEntry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        activeEntry.setPackageManager("npm");
        activeEntry.setPackageVersion("1.0.0");
        activeEntry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("file1.js", true, 1))));

        IgnoreEntry inactiveEntry = new IgnoreEntry();
        inactiveEntry.setPackageName("inactive-package");
        inactiveEntry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        inactiveEntry.setPackageManager("npm");
        inactiveEntry.setPackageVersion("2.0.0");
        inactiveEntry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("file2.js", false, 2))));

        manager.updateIgnoreData("active-key", activeEntry);
        manager.updateIgnoreData("inactive-key", inactiveEntry);

        // Act
        manager.updateIgnoreTempList();

        // Assert
        Path tempListPath = manager.getTempListPath();
        String content = Files.readString(tempListPath);
        assertTrue(content.contains("active-package"), "Temp list should contain active entry");
        assertFalse(content.contains("inactive-package"), "Temp list should NOT contain inactive entry");
    }

    @Test
    void testReviveEntry_withSingleFile_shouldSetInactive() {
        // Arrange - Entry with only one file
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("single-file-package");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        entry.setPackageManager("npm");
        entry.setPackageVersion("1.0.0");
        entry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("single.js", true, 10))));

        manager.updateIgnoreData("single-key", entry);

        // Act
        boolean result = manager.reviveEntry(entry);

        // Assert
        assertTrue(result, "Revive should succeed");
        // After revive, entry with all inactive files is removed from the map
        IgnoreEntry revivedEntry = manager.getIgnoreData().get("single-key");
        assertNull(revivedEntry, "Entry should be removed from map after all files become inactive");
    }

    @Test
    void testReviveEntry_withMultipleFiles_shouldSetAllInactive() {
        // Arrange - Entry with multiple files
        IgnoreEntry entry = new IgnoreEntry();
        entry.setPackageName("multi-file-package");
        entry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.ASCA);
        entry.setRuleId(123);

        List<IgnoreEntry.FileReference> files = new ArrayList<>();
        files.add(new IgnoreEntry.FileReference("file1.java", true, 10));
        files.add(new IgnoreEntry.FileReference("file2.java", true, 20));
        files.add(new IgnoreEntry.FileReference("file3.java", true, 30));
        entry.setFiles(files);

        manager.updateIgnoreData("multi-key", entry);

        // Act
        boolean result = manager.reviveEntry(entry);

        // Assert
        assertTrue(result, "Revive should succeed");
        // After revive, entry with all inactive files is removed from the map
        IgnoreEntry revivedEntry = manager.getIgnoreData().get("multi-key");
        assertNull(revivedEntry, "Entry should be removed from map after all files become inactive");
    }

    @Test
    void testReviveEntry_doesNotMatchDifferentType() {
        // Arrange
        IgnoreEntry ossEntry = new IgnoreEntry();
        ossEntry.setPackageName("test-package");
        ossEntry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.OSS);
        ossEntry.setPackageManager("npm");
        ossEntry.setPackageVersion("1.0.0");
        ossEntry.setFiles(new ArrayList<>(List.of(new IgnoreEntry.FileReference("file.js", true, 1))));

        manager.updateIgnoreData("oss-key", ossEntry);

        // Try to revive with different type
        IgnoreEntry containerEntry = new IgnoreEntry();
        containerEntry.setPackageName("test-package");
        containerEntry.setType(com.checkmarx.intellij.devassist.utils.ScanEngine.CONTAINERS);

        // Act
        boolean result = manager.reviveEntry(containerEntry);

        // Assert
        assertFalse(result, "Should not match entry with different type");
        assertTrue(manager.getIgnoreData().get("oss-key").getFiles().get(0).isActive(),
                   "Original entry should remain active");
    }

}


