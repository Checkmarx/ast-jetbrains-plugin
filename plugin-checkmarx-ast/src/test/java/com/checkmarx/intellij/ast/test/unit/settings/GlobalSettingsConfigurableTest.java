package com.checkmarx.intellij.ast.test.unit.settings;

import com.checkmarx.intellij.ast.settings.GlobalSettingsConfigurable;
import com.checkmarx.intellij.common.settings.SettingsComponent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class GlobalSettingsConfigurableTest {

    private void injectSettingsComponent(GlobalSettingsConfigurable configurable, SettingsComponent mock) throws Exception {
        Field field = GlobalSettingsConfigurable.class.getDeclaredField("settingsComponent");
        field.setAccessible(true);
        field.set(configurable, mock);
    }

    @Test
    void getId_AndHelpTopic_AreConsistent() {
        GlobalSettingsConfigurable configurable = new GlobalSettingsConfigurable();
        assertEquals(configurable.getId(), configurable.getHelpTopic());
    }

    @Test
    void getDisplayName_IsNonEmpty() {
        GlobalSettingsConfigurable configurable = new GlobalSettingsConfigurable();
        assertNotNull(configurable.getDisplayName());
        assertFalse(configurable.getDisplayName().isBlank());
    }

    @Test
    void isModified_AndApply_DelegateToSettingsComponent() throws Exception {
        GlobalSettingsConfigurable configurable = new GlobalSettingsConfigurable();
        SettingsComponent mockSettings = Mockito.mock(SettingsComponent.class);
        Mockito.when(mockSettings.isModified()).thenReturn(true);
        injectSettingsComponent(configurable, mockSettings);

        assertTrue(configurable.isModified());
        configurable.apply();
        Mockito.verify(mockSettings).apply();
    }

    @Test
    void reset_DelegatesToSettingsComponent() throws Exception {
        GlobalSettingsConfigurable configurable = new GlobalSettingsConfigurable();
        SettingsComponent mockSettings = Mockito.mock(SettingsComponent.class);
        injectSettingsComponent(configurable, mockSettings);

        configurable.reset();
        Mockito.verify(mockSettings).reset();
    }
}
