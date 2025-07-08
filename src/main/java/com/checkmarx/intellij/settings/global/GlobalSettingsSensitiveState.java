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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * GlobalSettingsSensitiveState class responsible to store secrets, e.g., API key in secure storage.
 */
@Getter
@Setter
@EqualsAndHashCode
public class GlobalSettingsSensitiveState {

    private static final Logger LOGGER = Utils.getLogger(GlobalSettingsSensitiveState.class);

    private static final String NAMESPACE = GlobalSettingsSensitiveState.class.getSimpleName();

    public static GlobalSettingsSensitiveState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalSettingsSensitiveState.class);
    }

    private static final CredentialAttributes apiKeyAttr = new CredentialAttributes(
            CredentialAttributesKt.generateServiceName(GlobalSettingsSensitiveState.class.getSimpleName(),
                                                       Constants.API_KEY_CREDENTIALS_KEY)
    );

    private String apiKey;

    private String refreshToken;

    public GlobalSettingsSensitiveState() {
        reset();
    }

    /**
     * Apply state and store in {@link PasswordSafe}.
     *
     * @param state state to apply
     */
    public void apply(@NotNull GlobalSettingsSensitiveState state) {
        String msg = validate(state);
        if (msg != null) {
            LOGGER.warn(msg);
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

    public boolean isValid() {
        return validate(this) == null;
    }

    /**
     * Helper method to store sensitive fields in the {@link com.intellij.ide.passwordSafe.PasswordSafe}.
     */
    private void store() {
        PasswordSafe.getInstance().set(apiKeyAttr, new Credentials(null, apiKey));
    }

    private static String validate(@NotNull GlobalSettingsSensitiveState state) {
        if (StringUtils.isBlank(state.getApiKey())) {
            return Bundle.missingFieldMessage(Resource.API_KEY);
        }
        return null;
    }

    /**
     * Common method to generate unique credential attributes
     *
     * @param keyName - Unique key name of your secret which you want to store
     * @return CredentialAttributes
     */
    public static CredentialAttributes getCredentialAttributes(final String keyName) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName(NAMESPACE, keyName));
    }

    /**
     * Common method to save secret in secure storage using key name
     * @param keyName - Unique specific key name which value you want to save
     */

    public void saveSecret(@NotNull final String keyName, final String secret) {
        CredentialAttributes credentialAttributes = getCredentialAttributes(keyName);
        PasswordSafe.getInstance().set(credentialAttributes,  new Credentials(keyName, secret));
    }

    /**
     * Common method to load secret from secure storage using key name
     * @param keyName - Unique specific key name which value you want to retrieve
     * @return Secret
     */
    public String loadSecret(@NotNull final String keyName) {
        Credentials credentials = PasswordSafe.getInstance().get(getCredentialAttributes(keyName));
        return credentials != null ? credentials.getPasswordAsString() : null;
    }

    /**
     * Common method to delete secret using key name
     * @param keyName - Unique specific key name which value you want to delete
     */
    public void deleteSecret(@NotNull final String keyName) {
        PasswordSafe.getInstance().set(getCredentialAttributes(keyName), null);
    }

    /**
     * Save refresh token
     * @param refreshToken - refresh token value
     */
    public void saveRefreshToken(final String refreshToken){
        saveSecret(Constants.REFRESH_TOKEN_CREDENTIALS_KEY, refreshToken);
    }
}
