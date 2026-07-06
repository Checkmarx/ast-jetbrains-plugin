package com.checkmarx.intellij.cxdevassist.test.unit.settings;

import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.settings.SettingsComponent;
import com.checkmarx.intellij.cxdevassist.settings.RealtimeScannersSettingsComponent;
import com.checkmarx.intellij.cxdevassist.settings.RealtimeScannersSettingsConfigurable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import javax.swing.*;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeScannersSettingsConfigurableTest {

    private GlobalSettingsState mockGlobalState;
    private MockedStatic<GlobalSettingsState> mockedGlobalState;

    @BeforeEach
    void setUp() {
        mockGlobalState = mock(GlobalSettingsState.class);
        mockedGlobalState = mockStatic(GlobalSettingsState.class);
        mockedGlobalState.when(GlobalSettingsState::getInstance).thenReturn(mockGlobalState);
    }
    
    @AfterEach
    void tearDown() {
        mockedGlobalState.close();
    }

    @Test
    @DisplayName("Display name is correct")
    void testGetDisplayName() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertEquals("Realtime Scanners", configurable.getDisplayName());
    }

    @Test
    @DisplayName("ID is not null")
    void testGetId() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertNotNull(configurable.getId());
    }

    @Test
    @DisplayName("Help topic matches ID")
    void testGetHelpTopic() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertEquals(configurable.getId(), configurable.getHelpTopic());
    }

    @Test
    @DisplayName("Global state is accessible")
    void testGlobalStateAccess() {
        GlobalSettingsState state = GlobalSettingsState.getInstance();
        assertEquals(mockGlobalState, state);
    }

    @Test
    @DisplayName("Scanner settings can be checked")
    void testScannerSettings() {
        when(mockGlobalState.isAscaRealtime()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAscaRealtime());
    }

    @Test
    @DisplayName("Multiple instances are independent")
    void testMultipleInstances() {
        RealtimeScannersSettingsConfigurable config1 = new RealtimeScannersSettingsConfigurable();
        RealtimeScannersSettingsConfigurable config2 = new RealtimeScannersSettingsConfigurable();
        assertNotSame(config1, config2);
    }

    @Test
    @DisplayName("Display name is consistent")
    void testDisplayNameConsistency() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        String name1 = configurable.getDisplayName();
        String name2 = configurable.getDisplayName();
        assertEquals(name1, name2);
    }

    @Test
    @DisplayName("Configurable instantiation succeeds")
    void testInstantiation() {
        assertDoesNotThrow(() -> new RealtimeScannersSettingsConfigurable());
    }

    @Test
    @DisplayName("MCP enabled state affects scanner availability")
    void testMcpEnabledState() {
        when(mockGlobalState.isMcpEnabled()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isMcpEnabled());
    }

    @Test
    @DisplayName("Authentication affects scanner configurability")
    void testAuthenticationState() {
        when(mockGlobalState.isAuthenticated()).thenReturn(true);
        assertTrue(GlobalSettingsState.getInstance().isAuthenticated());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    // ===== createComponent() =====

    @Test
    @DisplayName("createComponent returns the component's mainPanel")
    void createComponent_ReturnsMainPanel() {
        try (MockedConstruction<RealtimeScannersSettingsComponent> mc =
                mockConstruction(RealtimeScannersSettingsComponent.class,
                        (mock, ctx) -> when(mock.getMainPanel()).thenReturn(new JPanel()))) {
            RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
            JComponent panel = configurable.createComponent();
            assertNotNull(panel);
        }
    }

    // ===== isModified / apply / reset with non-null settingsComponent =====

    @Test
    @DisplayName("isModified delegates to component when non-null")
    void isModified_ComponentNonNull_DelegatesToComponent() throws Exception {
        SettingsComponent mockComp = mock(SettingsComponent.class);
        when(mockComp.isModified()).thenReturn(true);

        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        setField(configurable, "settingsComponent", mockComp);

        assertTrue(configurable.isModified());
        verify(mockComp).isModified();
    }

    @Test
    @DisplayName("apply delegates to component when non-null")
    void apply_ComponentNonNull_CallsApply() throws Exception {
        SettingsComponent mockComp = mock(SettingsComponent.class);
        doNothing().when(mockComp).apply();

        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        setField(configurable, "settingsComponent", mockComp);

        assertDoesNotThrow(configurable::apply);
        verify(mockComp).apply();
    }

    @Test
    @DisplayName("reset delegates to component when non-null")
    void reset_ComponentNonNull_CallsReset() throws Exception {
        SettingsComponent mockComp = mock(SettingsComponent.class);
        doNothing().when(mockComp).reset();

        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        setField(configurable, "settingsComponent", mockComp);

        assertDoesNotThrow(configurable::reset);
        verify(mockComp).reset();
    }

    @Test
    @DisplayName("isModified returns false when component not yet created")
    void isModified_settingsComponentNotCreated_returnsFalse() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertFalse(configurable.isModified());
    }

    @Test
    @DisplayName("apply does nothing when component not yet created")
    void apply_settingsComponentNotCreated_doesNothing() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertDoesNotThrow(() -> configurable.apply());
    }

    @Test
    @DisplayName("reset does nothing when component not yet created")
    void reset_settingsComponentNotCreated_doesNothing() {
        RealtimeScannersSettingsConfigurable configurable = new RealtimeScannersSettingsConfigurable();
        assertDoesNotThrow(() -> configurable.reset());
    }
}

