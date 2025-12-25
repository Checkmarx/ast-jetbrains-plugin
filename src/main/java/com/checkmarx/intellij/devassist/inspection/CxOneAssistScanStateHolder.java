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
public final class CxOneAssistScanStateHolder {

    private final Map<String, Long> fileTimeStamp = new ConcurrentHashMap<>();

    /**
     * Returns the instance of this service for the given project.
     */
    public static CxOneAssistScanStateHolder getInstance(@NotNull Project project) {
        return project.getService(CxOneAssistScanStateHolder.class);
    }

    // Utility method to get a single timestamp using virtual file path
    public Long getTimeStamp(String filePath) {
        return fileTimeStamp.get(filePath);
    }

    // Utility method to update a single timestamp
    public void updateTimeStamp(String filePath, Long compositeStamp) {
        fileTimeStamp.put(filePath, compositeStamp);
    }

}
