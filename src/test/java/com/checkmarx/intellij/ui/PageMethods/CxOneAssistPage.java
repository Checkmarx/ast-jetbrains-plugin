package com.checkmarx.intellij.ui.PageMethods;

import org.junit.jupiter.api.Assertions;

import javax.xml.xpath.XPath;

import static com.checkmarx.intellij.ui.utils.RemoteRobotUtils.*;
import static com.checkmarx.intellij.ui.utils.UIHelper.*;
import static com.checkmarx.intellij.ui.utils.Xpath.*;

public class CxOneAssistPage {

    static String[] engineXpaths = {
            ASCA_ENGINE_SELECTION_CHECKBOX,
            OSS_REALTIME_ENGINE_CHECKBOX,
            SECRET_DETECTION_ENGINE_CHECKBOX,
            CONTAINER_REALTIME_ENGINE_CHECKBOX,
            IAC_REALTIME_ENGINE_CHECKBOX
    };

    public static void navigateToCxOneAssistPage() {
        // Implementation for navigating to CxOne Assist page
        locateAndClickOnButton(GO_TO_CXONE_ASSIST_LINK);
    }

    public static void validateCxOneAssistPageLoadedSuccessfully() {
        // Implementation for validating CxOne Assist page
        for (String xpath : engineXpaths) {
            hasAnyComponent(xpath);
        }
        validateAllEnginesAreSelected();
    }

    public static void selectAndUnSelectAllEngines(boolean select) {
        // Implementation for unselecting all engines
        for (String xpath : engineXpaths) {
            boolean value = isCheckboxSelected(xpath);

            if (value && !select){
                //Checkbox is selected and we want to unselect
                locateAndClickOnButton(xpath);
            }else if (!value && select){
                //Checkbox is unselected and we want to select
                locateAndClickOnButton(xpath);
            }
        }
    }

    public static void selectEngine(String engineName) {
        // Implementation for selecting a specific engine
        for (String xpath : engineXpaths) {
            boolean value = isCheckboxSelected(xpath);
            if (!value && xpath.contains(engineName)){
                locateAndClickOnButton(xpath);
            }
        }
    }

    public static void validateAllEnginesAreSelected() {
        // Implementation for validating a specific engine is selected
        for (String xpath : engineXpaths) {
            boolean value = isCheckboxSelected(xpath);
            Assertions.assertTrue(value, "Expected engine checkbox to be selected: " + xpath);
        }

    }
}
