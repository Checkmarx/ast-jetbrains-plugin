package com.checkmarx.intellij.cxdevassist.test.unit.utils;

import com.checkmarx.intellij.cxdevassist.utils.CxDevAssistConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

class CxDevAssistConstantsTest {

    @Test
    void constructor_ThrowsIllegalStateException() throws Exception {
        Constructor<CxDevAssistConstants> ctor = CxDevAssistConstants.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        InvocationTargetException ex = assertThrows(InvocationTargetException.class, ctor::newInstance);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void pluginName_IsExpectedValue() {
        assertEquals("Checkmarx Developer Assist", CxDevAssistConstants.PLUGIN_NAME);
    }

    @Test
    void pluginChildRealtimeSettingsId_IsExpectedValue() {
        assertEquals("settings.devassist.realtime", CxDevAssistConstants.PLUGIN_CHILD_REALTIME_SETTINGS_ID);
    }

    @Test
    void findingsWindowName_IsExpectedValue() {
        assertEquals("Checkmarx Developer Assist Findings", CxDevAssistConstants.FINDINGS_WINDOW_NAME);
    }

    @Test
    void ignoredFindingsWindowName_IsExpectedValue() {
        assertEquals("Ignored Findings", CxDevAssistConstants.IGNORED_FINDINGS_WINDOW_NAME);
    }

    @Test
    void devassistHelpLink_StartsWithHttps() {
        assertTrue(CxDevAssistConstants.DEVASSIST_HELP_LINK.startsWith("https://"),
                "Help link should start with https://");
    }

    @Test
    void devassistHelpLink_IsNotBlank() {
        assertFalse(CxDevAssistConstants.DEVASSIST_HELP_LINK.isBlank());
    }
}
