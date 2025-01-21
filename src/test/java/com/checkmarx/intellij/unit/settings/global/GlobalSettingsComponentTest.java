package com.checkmarx.intellij.unit.settings.global;

import com.checkmarx.intellij.settings.global.GlobalSettingsComponent;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.SettingsListener;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.fields.ExpandableTextField;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Font;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalSettingsComponentTest {

    @Mock
    private Application mockApplication;

    @Mock
    private MessageBus mockMessageBus;

    @Mock
    private GlobalSettingsState mockSettingsState;

    @Mock
    private GlobalSettingsSensitiveState mockSensitiveState;

    @Mock
    private EditorColorsManager mockEditorColorsManager;

    @Mock
    private EditorColorsScheme mockEditorColorsScheme;

    @Mock
    private PasswordSafe mockPasswordSafe;

    private GlobalSettingsComponent component;

    private MockedStatic<ApplicationManager> appManagerMock;
    private MockedStatic<GlobalSettingsState> settingsStateMock;
    private MockedStatic<GlobalSettingsSensitiveState> sensitiveMock;
    private MockedStatic<EditorColorsManager> editorColorsMock;
    private MockedStatic<PasswordSafe> passwordSafeMock;

    @BeforeEach
    void setUp() throws Exception {
        // Mock static methods
        appManagerMock = mockStatic(ApplicationManager.class);
        settingsStateMock = mockStatic(GlobalSettingsState.class);
        sensitiveMock = mockStatic(GlobalSettingsSensitiveState.class);
        editorColorsMock = mockStatic(EditorColorsManager.class);
        passwordSafeMock = mockStatic(PasswordSafe.class);

        // Configure static mocks
        appManagerMock.when(ApplicationManager::getApplication).thenReturn(mockApplication);
        settingsStateMock.when(GlobalSettingsState::getInstance).thenReturn(mockSettingsState);
        sensitiveMock.when(GlobalSettingsSensitiveState::getInstance).thenReturn(mockSensitiveState);
        passwordSafeMock.when(PasswordSafe::getInstance).thenReturn(mockPasswordSafe);
        editorColorsMock.when(EditorColorsManager::getInstance).thenReturn(mockEditorColorsManager);

        // Configure instance mocks
        when(mockApplication.getMessageBus()).thenReturn(mockMessageBus);
        when(mockEditorColorsManager.getGlobalScheme()).thenReturn(mockEditorColorsScheme);
        when(mockEditorColorsScheme.getFont(any(EditorFontType.class)))
            .thenReturn(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        // Initialize static fields using reflection
        setStaticField(GlobalSettingsComponent.class, "SETTINGS_STATE", mockSettingsState);
        setStaticField(GlobalSettingsComponent.class, "SENSITIVE_SETTINGS_STATE", mockSensitiveState);

        // Create component after all mocks are set up
        component = new GlobalSettingsComponent();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Reset static fields
        setStaticField(GlobalSettingsComponent.class, "SETTINGS_STATE", null);
        setStaticField(GlobalSettingsComponent.class, "SENSITIVE_SETTINGS_STATE", null);

        // Close static mocks
        appManagerMock.close();
        settingsStateMock.close();
        sensitiveMock.close();
        editorColorsMock.close();
        passwordSafeMock.close();
    }

    private void setStaticField(Class<?> targetClass, String fieldName, Object value) throws Exception {
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    @Test
    void isModified_WithModifiedAdditionalParameters_ReturnsTrue() {
        // Arrange
        when(mockSettingsState.getAdditionalParameters()).thenReturn("");
        component.reset();

        // Set text to additionalParametersField
        component.getAdditionalParametersField().setText("modified");
        
        // Act
        boolean result = component.isModified();

        // Assert
        assertTrue(result);
    }

    @Test
    void reset_ResetsAllFields() {
        // Arrange
        String additionalParams = "--test-params";
        boolean ascaEnabled = true;
        String apiKey = "test-api-key";

        when(mockSettingsState.getAdditionalParameters()).thenReturn(additionalParams);
        when(mockSettingsState.isAsca()).thenReturn(ascaEnabled);
        when(mockSensitiveState.getApiKey()).thenReturn(apiKey);

        // Act
        component.reset();

        // Assert
        JBPasswordField apiKeyField = component.getApiKeyField();
        ExpandableTextField additionalParamsField = component.getAdditionalParametersField();
        JBCheckBox ascaCheckbox = component.getAscaCheckBox();

        assertEquals(additionalParams, additionalParamsField.getText());
        assertEquals(ascaEnabled, ascaCheckbox.isSelected());
        assertEquals(apiKey, new String(apiKeyField.getPassword()));
    }
} 