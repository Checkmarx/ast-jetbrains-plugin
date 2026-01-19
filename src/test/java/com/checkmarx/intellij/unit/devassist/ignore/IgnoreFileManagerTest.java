package com.checkmarx.intellij.unit.devassist.ignore;

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
        // getIgnoreData() returns a copy for thread safety, so we check key presence
        assertTrue(manager.getIgnoreData().containsKey("vulnKey"));
        assertNotNull(manager.getIgnoreData().get("vulnKey"));
        Path ignoreFile = manager.getIgnoreFilePath();
        assertTrue(Files.exists(ignoreFile));
    }

    @Test
    void testGetAllIgnoreEntries() {
        IgnoreEntry entry = new IgnoreEntry();
        manager.updateIgnoreData("vulnKey", entry);
        List<IgnoreEntry> entries = manager.getAllIgnoreEntries();
        // getAllIgnoreEntries() returns entries from the internal map, check size instead of reference
        assertFalse(entries.isEmpty());
        assertEquals(1, entries.size());
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

}


