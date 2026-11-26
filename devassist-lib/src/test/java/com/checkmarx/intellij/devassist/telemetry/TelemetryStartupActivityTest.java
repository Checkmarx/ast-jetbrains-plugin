package com.checkmarx.intellij.devassist.telemetry;

import com.intellij.openapi.project.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;

class TelemetryStartupActivityTest {

    @Test
    @DisplayName("install tracking starts once per IDE session, not once per project")
    void runActivity_startsInstallTrackingOnce() {
        try (MockedStatic<TelemetryService> telemetry = mockStatic(TelemetryService.class)) {
            new TelemetryStartupActivity().runActivity(mock(Project.class));
            new TelemetryStartupActivity().runActivity(mock(Project.class));

            telemetry.verify(TelemetryService::startInstallTracking, times(1));
        }
    }
}
