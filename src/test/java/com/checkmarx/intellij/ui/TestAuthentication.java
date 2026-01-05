package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.PageMethods.CheckmarxSettingsPage.*;

public class TestAuthentication extends BaseUITest{
    @Test
    @Video
    public void testASTSuccessAuthentication() {
        // Test successfully connection
        testASTConnection(true);
    }

    @Test
    @Video
    public void testASTFailedAuthentication() {
        // Test wrong connection
        testASTConnection(false);
    }
}
