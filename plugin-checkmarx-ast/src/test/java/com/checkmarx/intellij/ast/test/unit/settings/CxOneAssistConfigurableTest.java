package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.intellij.ast.settings.CxOneAssistConfigurable;
import com.checkmarx.intellij.common.settings.SettingsComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class CxOneAssistConfigurableTest {

    private void injectSettingsComponent(CxOneAssistConfigurable configurable, SettingsComponent mock) throws Exception {
        Field field = CxOneAssistConfigurable.class.getDeclaredField("settingsComponent");
        field.setAccessible(true);
        field.set(configurable, mock);
    }

    @Test
    void getId_AndHelpTopic_AreConsistent() {
        CxOneAssistConfigurable configurable = new CxOneAssistConfigurable();
        assertEquals(configurable.getId(), configurable.getHelpTopic());
        assertTrue(configurable.getId().endsWith(".assist"));
    }

    @Test
    void getDisplayName_IsNonEmpty() {
        CxOneAssistConfigurable configurable = new CxOneAssistConfigurable();
        assertNotNull(configurable.getDisplayName());
        assertFalse(configurable.getDisplayName().isBlank());
    }

    @Test
    void isModified_AndApply_DelegateToSettingsComponent() throws Exception {
        CxOneAssistConfigurable configurable = new CxOneAssistConfigurable();
        SettingsComponent mockSettings = Mockito.mock(SettingsComponent.class);
        Mockito.when(mockSettings.isModified()).thenReturn(true);
        injectSettingsComponent(configurable, mockSettings);

        assertTrue(configurable.isModified());
        configurable.apply();
        Mockito.verify(mockSettings).apply();
    }

    @Test
    void reset_DelegatesToSettingsComponent() throws Exception {
        CxOneAssistConfigurable configurable = new CxOneAssistConfigurable();
        SettingsComponent mockSettings = Mockito.mock(SettingsComponent.class);
        injectSettingsComponent(configurable, mockSettings);

        configurable.reset();
        Mockito.verify(mockSettings).reset();
    }

    @Test
    void isModified_WhenSettingsComponentIsNull_ReturnsFalse() {
        // settingsComponent is null by default (createComponent not called)
        CxOneAssistConfigurable configurable = new CxOneAssistConfigurable();
        assertFalse(configurable.isModified(),
                "isModified should return false when settingsComponent has not been initialized");
    }

    @Test
    void applyAndReset_WhenSettingsComponentIsNull_DoNotThrow() {
        // Covers the null-guard branches in apply() and reset()
        CxOneAssistConfigurable configurable = new CxOneAssistConfigurable();
        assertDoesNotThrow(configurable::apply,
                "apply() should be a no-op and not throw when settingsComponent is null");
        assertDoesNotThrow(configurable::reset,
                "reset() should be a no-op and not throw when settingsComponent is null");
    }
}

