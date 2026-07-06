package com.checkmarx.intellij.devassist.test.inspection;

import com.checkmarx.intellij.devassist.inspection.DevAssistScanStateHolder;
import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DevAssistScanStateHolderTest {

    private DevAssistScanStateHolder holder;

    @BeforeEach
    void setUp() {
        holder = new DevAssistScanStateHolder();
    }

    @Test
    void getTimeStamp_unknownPath_returnsNull() {
        assertNull(holder.getTimeStamp("/unknown/path.java"));
    }

    @Test
    void updateTimeStamp_thenGetTimeStamp_returnsUpdatedValue() {
        holder.updateTimeStamp("/path/file.java", 12345L);
        assertEquals(12345L, holder.getTimeStamp("/path/file.java"));
    }

    @Test
    void updateTimeStamp_overwritesExistingValue() {
        holder.updateTimeStamp("/path/file.java", 100L);
        holder.updateTimeStamp("/path/file.java", 200L);
        assertEquals(200L, holder.getTimeStamp("/path/file.java"));
    }

    @Test
    void updateTimeStamp_multiplePaths_tracksEachIndependently() {
        holder.updateTimeStamp("/a.java", 1L);
        holder.updateTimeStamp("/b.java", 2L);
        assertEquals(1L, holder.getTimeStamp("/a.java"));
        assertEquals(2L, holder.getTimeStamp("/b.java"));
    }

    @Test
    void getInstance_returnsServiceFromProject() {
        Project mockProject = mock(Project.class);
        when(mockProject.getService(DevAssistScanStateHolder.class)).thenReturn(holder);
        assertSame(holder, DevAssistScanStateHolder.getInstance(mockProject));
    }
}
