package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.PageMethods.OSSRealTimeScanPage.*;
import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class TestOSSRealTime extends BaseUITest {

    @Test
    @Video
    @DisplayName("Verify OSS Real-Time Scan is enabled and start scanning as soon as user login")
    public void testAutoStartOssScanOnLoginWhenEnabled() {
        //To verify OSS Real-Time Scan is enabled and start scanning as soon as user login
        enableRealTimeScanIfDisabled(OSS_REALTIME_CHECKBOX);
        //Verify OSS Real-Time scan auto starts after login
        waitFor(() -> hasAnyComponent(SCAN_PROGRESS_BAR));
        Assertions.assertTrue(hasAnyComponent(SCAN_PROGRESS_BAR));
    }

    @Test
    @Video
    @DisplayName("Verify OSS vulnerabilities are displayed in the Issues Tree after scan completion")
    public void testDisplayOssFindingsAfterScanCompletion() {
        //To verify OSS vulnerabilities are displayed in the Issues Tree after scan completion
        enableRealTimeScanIfDisabled(OSS_REALTIME_CHECKBOX);
        //Wait for existing OSS scans to complete
        waitFor(() -> !hasAnyComponent(SCAN_PROGRESS_BAR));
        //Open checkmarx one assist findings tab
        clickSafe(CX_ASSIST_FINDING_TAB);
        //Verify Issues tree should not be empty and OSS vulnerabilities files are displayed in the Issues Tree
        verifyOssFindingsTreeNotEmpty();
        //Move back to Scan Results tab to avoid interference with other tests
        clickSafe(SCAN_RESULTS_TAB);
    }
}
