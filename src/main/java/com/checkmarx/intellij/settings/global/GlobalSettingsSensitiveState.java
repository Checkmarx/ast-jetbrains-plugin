package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Bundle;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Resource;
import com.checkmarx.intellij.Utils;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * State object for sensitive plugin global settings.
 * Currently, only stores the API Key.
 */
@Getter
@Setter
@EqualsAndHashCode
public class GlobalSettingsSensitiveState {

    public static GlobalSettingsSensitiveState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalSettingsSensitiveState.class);
    }

    private static final CredentialAttributes apiKeyAttr = new CredentialAttributes(
            CredentialAttributesKt.generateServiceName(GlobalSettingsSensitiveState.class.getSimpleName(),
                                                       Constants.API_KEY_CREDENTIALS_KEY)
    );
    private String apiKey;

    public GlobalSettingsSensitiveState() {
        reset();
    }

    /**
     * Apply state and store in {@link PasswordSafe}.
     *
     * @param state state to apply
     * @throws ConfigurationException if field validation fails
     */
    public void apply(@NotNull GlobalSettingsSensitiveState state) throws ConfigurationException {
        if (Utils.isEmptyOrBlank(state.getApiKey())) {
            throw new ConfigurationException(Bundle.missingFieldMessage(Resource.API_KEY));
        }
        XmlSerializerUtil.copyBean(state, this);
        store();
    }

    /**
     * Reset state to what is contained in {@link PasswordSafe}.
     */
    public void reset() {
        apiKey = PasswordSafe.getInstance().getPassword(apiKeyAttr);
    }

    /**
     * Helper method to store sensitive fields in the {@link com.intellij.ide.passwordSafe.PasswordSafe}.
     */
    private void store() {
        PasswordSafe.getInstance().set(apiKeyAttr, new Credentials(null, apiKey));
    }
}
