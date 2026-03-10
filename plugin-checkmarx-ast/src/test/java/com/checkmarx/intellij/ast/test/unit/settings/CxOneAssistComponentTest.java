package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.intellij.ast.settings.CxOneAssistComponent;
import com.checkmarx.intellij.common.utils.Constants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for the static formatting helper in {@link CxOneAssistComponent}.
 * The component itself depends on IntelliJ platform services; to keep these tests platform-free
 * we validate the pure formatting logic via reflection.
 */
class CxOneAssistComponentTest {

    private static String formatTitle(String raw) throws Exception {
        Method m = CxOneAssistComponent.class.getDeclaredMethod("formatTitle", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, raw);
    }

    @Test
    void formatTitle_WithNull_ReturnsEmptyHtml() throws Exception {
        String result = formatTitle(null);
        assertEquals("<html></html>", result);
    }

    @Test
    void formatTitle_WithoutColon_WrapsEntireText() throws Exception {
        String result = formatTitle("Simple Title");
        assertEquals(String.format(Constants.HTML_WRAPPER_FORMAT, "Simple Title"), result);
    }

    @Test
    void formatTitle_WithColonAndValue_BoldsTextAfterColon() throws Exception {
        String result = formatTitle("Prefix: Value Part");
        assertEquals(String.format(Constants.HTML_WRAPPER_FORMAT, "Prefix: <b>Value Part</b>"), result);
    }

    @Test
    void formatTitle_WithColonAtEnd_TreatsAsNoValue() throws Exception {
        String result = formatTitle("Heading:");
        assertEquals(String.format(Constants.HTML_WRAPPER_FORMAT, "Heading:"), result);
    }
}

