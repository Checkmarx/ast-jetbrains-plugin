package com.checkmarx.intellij.ui;

import com.automation.remarks.junit5.Video;
import com.intellij.remoterobot.fixtures.JTreeFixture;
import com.intellij.remoterobot.search.locators.Locators;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class TestAsca extends BaseUITest {

    public void clickAscaCheckbox(){
        openSettings();
        waitFor(() -> hasAnyComponent(ASCA_CHECKBOX));
        click(ASCA_CHECKBOX);
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
    }

    public void validateAscaRunning(){
        openSettings();
        waitFor(() -> hasAnyComponent(ASCA_INSTALL_SUCCESS));
        Assertions.assertTrue(hasAnyComponent(ASCA_INSTALL_SUCCESS));
    }

    @Test
    @Video
    public void testClickAscaCheckbox() {
        clickAscaCheckbox();
        click(ASCA_CHECKBOX);
        click(OK_BTN);
    }

    @Test
    @Video
    public void clickAscaCheckbox_ExitSetting_OpenSetting_ValidateAscaRunning_Success() {
        clickAscaCheckbox();
        click(OK_BTN);
        validateAscaRunning();
        click(OK_BTN);
    }

    @Test
    @Video
    public void AscaActivated_EditFileWithVulnerabilities() {
//        clickAscaCheckbox();
//        click(OK_BTN);
        getInspectedProblems();
    }
}