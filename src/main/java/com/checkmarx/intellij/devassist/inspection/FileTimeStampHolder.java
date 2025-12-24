package com.checkmarx.intellij.devassist.inspection;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A service that maintains a map of file paths to their corresponding modification timestamps.
 */
@Service(Service.Level.PROJECT)
public final class FileTimeStampHolder {

    private final Map<String, Long> fileTimeStamp = new ConcurrentHashMap<>();

    /**
     * Returns the instance of this service for the given project.
     */
    public static FileTimeStampHolder getInstance(@NotNull Project project) {
        return project.getService(FileTimeStampHolder.class);
    }

    public Map<String, Long> getFileTimeStampMap() {
        return fileTimeStamp;
    }

    // Utility method to get a single timestamp
    public Long getTimeStamp(String filePath) {
        return fileTimeStamp.get(filePath);
    }

    public void updateTimeStamp(String filePath, Long compositeStamp) {
        fileTimeStamp.put(filePath, compositeStamp);
    }
}
