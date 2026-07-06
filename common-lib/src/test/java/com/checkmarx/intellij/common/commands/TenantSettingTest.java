package com.checkmarx.intellij.common.commands;

import com.checkmarx.ast.tenant.TenantSetting;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantSettingTest {

    @Mock
    private GlobalSettingsState mockState;

    @Mock
    private GlobalSettingsSensitiveState mockSensitive;

    @Mock
    private CxWrapper mockWrapper;

    // ---- isAuthenticated ----

    @Test
    void isAuthenticated_whenTrue_returnsTrue() {
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class)) {
            gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            when(mockState.isAuthenticated()).thenReturn(true);

            assertTrue(com.checkmarx.intellij.common.commands.TenantSetting.isAuthenticated());
        }
    }

    @Test
    void isAuthenticated_whenFalse_returnsFalse() {
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class)) {
            gsStatic.when(GlobalSettingsState::getInstance).thenReturn(mockState);
            when(mockState.isAuthenticated()).thenReturn(false);

            assertFalse(com.checkmarx.intellij.common.commands.TenantSetting.isAuthenticated());
        }
    }

    @Test
    void isAuthenticated_whenExceptionThrown_returnsFalse() {
        try (MockedStatic<GlobalSettingsState> gsStatic = mockStatic(GlobalSettingsState.class)) {
            gsStatic.when(GlobalSettingsState::getInstance).thenThrow(new RuntimeException("state error"));

            assertFalse(com.checkmarx.intellij.common.commands.TenantSetting.isAuthenticated());
        }
    }

    // ---- isScanAllowed ----

    @Test
    void isScanAllowed_whenEnabled_returnsTrue() throws Exception {
        try (MockedStatic<CxWrapperFactory> wfStatic = mockStatic(CxWrapperFactory.class)) {
            wfStatic.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ideScansEnabled()).thenReturn(true);

            assertTrue(com.checkmarx.intellij.common.commands.TenantSetting.isScanAllowed());
        }
    }

    @Test
    void isScanAllowed_whenDisabled_returnsFalse() throws Exception {
        try (MockedStatic<CxWrapperFactory> wfStatic = mockStatic(CxWrapperFactory.class)) {
            wfStatic.when(CxWrapperFactory::build).thenReturn(mockWrapper);
            when(mockWrapper.ideScansEnabled()).thenReturn(false);

            assertFalse(com.checkmarx.intellij.common.commands.TenantSetting.isScanAllowed());
        }
    }

    // ---- isAiMcpServerEnabled ----

    @Test
    void isAiMcpServerEnabled_whenEnabled_returnsTrue() throws Exception {
        try (MockedStatic<CxWrapperFactory> wfStatic = mockStatic(CxWrapperFactory.class)) {
            wfStatic.when(() -> CxWrapperFactory.build(mockState, mockSensitive)).thenReturn(mockWrapper);
            when(mockWrapper.aiMcpServerEnabled()).thenReturn(true);

            assertTrue(com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(mockState, mockSensitive));
        }
    }

    @Test
    void isAiMcpServerEnabled_whenDisabled_returnsFalse() throws Exception {
        try (MockedStatic<CxWrapperFactory> wfStatic = mockStatic(CxWrapperFactory.class)) {
            wfStatic.when(() -> CxWrapperFactory.build(mockState, mockSensitive)).thenReturn(mockWrapper);
            when(mockWrapper.aiMcpServerEnabled()).thenReturn(false);

            assertFalse(com.checkmarx.intellij.common.commands.TenantSetting.isAiMcpServerEnabled(mockState, mockSensitive));
        }
    }

    // ---- getTenantSettingsMap ----

    @Test
    void getTenantSettingsMap_withSettings_returnsPopulatedMap() throws Exception {
        TenantSetting setting = mock(TenantSetting.class);
        when(setting.getKey()).thenReturn("scan.config.plugins.cxdevassist");
        when(setting.getValue()).thenReturn("true");

        try (MockedStatic<CxWrapperFactory> wfStatic = mockStatic(CxWrapperFactory.class)) {
            wfStatic.when(() -> CxWrapperFactory.build(mockState, mockSensitive)).thenReturn(mockWrapper);
            when(mockWrapper.tenantSettings()).thenReturn(List.of(setting));

            Map<String, String> result = com.checkmarx.intellij.common.commands.TenantSetting.getTenantSettingsMap(mockState, mockSensitive);

            assertNotNull(result);
            assertEquals("true", result.get("scan.config.plugins.cxdevassist"));
        }
    }

    @Test
    void getTenantSettingsMap_withEmptySettings_returnsEmptyMap() throws Exception {
        try (MockedStatic<CxWrapperFactory> wfStatic = mockStatic(CxWrapperFactory.class)) {
            wfStatic.when(() -> CxWrapperFactory.build(mockState, mockSensitive)).thenReturn(mockWrapper);
            when(mockWrapper.tenantSettings()).thenReturn(Collections.emptyList());

            Map<String, String> result = com.checkmarx.intellij.common.commands.TenantSetting.getTenantSettingsMap(mockState, mockSensitive);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ---- constant keys ----

    @Test
    void constantKeys_haveExpectedValues() {
        assertEquals("scan.config.plugins.cxdevassist", com.checkmarx.intellij.common.commands.TenantSetting.KEY_DEV_ASSIST);
        assertEquals("scan.config.plugins.cxoneassist", com.checkmarx.intellij.common.commands.TenantSetting.KEY_ONE_ASSIST);
    }
}
