package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * State object for not sensitive global settings for the plugin.
 */
@Getter
@Setter
@EqualsAndHashCode
@State(name = Constants.GLOBAL_SETTINGS_STATE_NAME, storages = @Storage(Constants.GLOBAL_SETTINGS_STATE_FILE))
public class GlobalSettingsState implements PersistentStateComponent<GlobalSettingsState> {

    public static GlobalSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalSettingsState.class);
    }

    @NotNull
    private String serverURL = "";

    private boolean useAuthURL;

    @NotNull
    private String authURL = "";

    @NotNull
    private String tenantName = "";

    @NotNull
    private String additionalParameters = "";

    @Override
    public @Nullable GlobalSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GlobalSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public void apply(@NotNull GlobalSettingsState state) throws ConfigurationException {
        if (Utils.isEmptyOrBlank(state.getServerURL())) {
            throw new ConfigurationException(Bundle.missingFieldMessage(Resource.SERVER_URL));
        }
        if (state.isUseAuthURL() && Utils.isEmptyOrBlank(state.getAuthURL())) {
            throw new ConfigurationException(Bundle.missingFieldMessage(Resource.AUTH_URL));
        }
        if (Utils.isEmptyOrBlank(state.getTenantName())) {
            throw new ConfigurationException(Bundle.missingFieldMessage(Resource.TENANT_NAME));
        }
        loadState(state);
    }
}

