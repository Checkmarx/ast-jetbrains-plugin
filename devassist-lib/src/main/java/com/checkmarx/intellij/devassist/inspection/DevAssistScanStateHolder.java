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
public final class DevAssistScanStateHolder {

    private final Map<String, Long> fileModifiedTimeStamp = new ConcurrentHashMap<>();

    /**
     * Returns the instance of this service for the given project.
     */
    public static DevAssistScanStateHolder getInstance(@NotNull Project project) {
        return project.getService(DevAssistScanStateHolder.class);
    }

    // Utility method to get a single timestamp using virtual file path
    public Long getTimeStamp(String filePath) {
        return fileModifiedTimeStamp.get(filePath);
    }

    // Utility method to update a single timestamp
    public void updateTimeStamp(String filePath, Long compositeStamp) {
        fileModifiedTimeStamp.put(filePath, compositeStamp);
    }
}
