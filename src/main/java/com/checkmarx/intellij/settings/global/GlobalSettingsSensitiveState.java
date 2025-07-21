package com.checkmarx.intellij.settings.global;

import com.checkmarx.intellij.Bundle;
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

import java.time.LocalDateTime;

/**
 * GlobalSettingsSensitiveState class responsible to store secrets, e.g., API key in secure storage.
 */
@Getter
@Setter
@EqualsAndHashCode
public class GlobalSettingsSensitiveState {

    private static final String API_KEY_CREDENTIALS_KEY = "APIKey";
    private static final String REFRESH_TOKEN_CREDENTIALS_KEY = "RefreshToken";

    private static final Logger LOGGER = Utils.getLogger(GlobalSettingsSensitiveState.class);
    private static final String NAMESPACE = GlobalSettingsSensitiveState.class.getSimpleName();

    public static GlobalSettingsSensitiveState getInstance() {
        return ApplicationManager.getApplication().getService(GlobalSettingsSensitiveState.class);
    }

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
    public void apply(@NotNull GlobalSettingsState settingsState, @NotNull GlobalSettingsSensitiveState state) {
        String msg = validate(settingsState, state);
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
        apiKey = loadSecret(API_KEY_CREDENTIALS_KEY);
        refreshToken = loadSecret(REFRESH_TOKEN_CREDENTIALS_KEY);
    }

    /**
     * Validating sensitive state contains secret or not based on authentication method
     *
     * @param settingsState GlobalSettingsState object which holding current state of authentication
     * @return true if secret present in secure storage otherwise false
     */
    public boolean isValid(@NotNull GlobalSettingsState settingsState) {
        return validate(settingsState, this) == null;
    }

    /**
     * Helper method to store sensitive fields in the {@link com.intellij.ide.passwordSafe.PasswordSafe}.
     */
    private void store() {
        saveSecret(API_KEY_CREDENTIALS_KEY, apiKey); //save an API key
        saveSecret(REFRESH_TOKEN_CREDENTIALS_KEY, refreshToken); // save refresh token
    }

    /**
     * Check whether secure storage contains secret or not based on authentication method
     *
     * @param settingsState  GlobalSettingsState object which holding current state of authentication
     * @param sensitiveState GlobalSettingsSensitiveState an object pointing to the current object
     * @return error message if secret not present in secure storage, otherwise null
     */
    private String validate(@NotNull GlobalSettingsState settingsState, @NotNull GlobalSettingsSensitiveState sensitiveState) {
        if (settingsState.isUseApiKey() && StringUtils.isBlank(sensitiveState.getApiKey())) {
            return Bundle.missingFieldMessage(Resource.API_KEY);
        } else if (!settingsState.isUseApiKey() && (StringUtils.isBlank(sensitiveState.getRefreshToken())
                || isTokenExpired(settingsState.getRefreshTokenExpiry()))) {
            return Bundle.missingFieldMessage(Resource.REFRESH_TOKEN);
        }
        return null;
    }

    /**
     * Checking refresh token expiry
     * @param tokenExpiryString - Expiry date of refresh token
     * @return true, if the refresh token is expired otherwise false
     */
    public boolean isTokenExpired(String tokenExpiryString){
        if (!StringUtils.isBlank(tokenExpiryString)){
            boolean isExpired = LocalDateTime.parse(tokenExpiryString).isBefore(LocalDateTime.now());
            LOGGER.warn("Refresh Token Expired: "+isExpired);
            return isExpired;
        }
        return false;
    }

    /**
     * Common method to generate unique credential attributes
     *
     * @param keyName - Unique key name of your secret which you want to store
     * @return CredentialAttributes
     */
    private CredentialAttributes getCredentialAttributes(final String keyName) {
        return new CredentialAttributes(CredentialAttributesKt.generateServiceName(NAMESPACE, keyName));
    }

    /**
     * Common method to save secret in secure storage using key name
     *
     * @param keyName - Unique specific key name which value you want to save
     */

    private void saveSecret(@NotNull final String keyName, final String secret) {
        CredentialAttributes credentialAttributes = getCredentialAttributes(keyName);
        PasswordSafe.getInstance().set(credentialAttributes, new Credentials(keyName, secret));
    }

    /**
     * Common method to load secret from secure storage using key name
     *
     * @param keyName - Unique specific key name which value you want to retrieve
     * @return Secret
     */
    private String loadSecret(@NotNull final String keyName) {
        Credentials credentials = PasswordSafe.getInstance().get(getCredentialAttributes(keyName));
        return credentials != null ? credentials.getPasswordAsString() : null;
    }

    /**
     * Common method to delete secret using key name
     *
     * @param keyName - Unique specific key name which value you want to delete
     */
    private void deleteSecret(@NotNull final String keyName) {
        PasswordSafe.getInstance().set(getCredentialAttributes(keyName), null);
    }

    /**
     * Save refresh token
     *
     * @param refreshToken - refresh token value
     */
    public void saveRefreshToken(final String refreshToken) {
        saveSecret(REFRESH_TOKEN_CREDENTIALS_KEY, refreshToken);
    }

    /**
     * Delete refresh token from the storage
     */
    public void deleteRefreshToken() {
        deleteSecret(REFRESH_TOKEN_CREDENTIALS_KEY);
        refreshToken = null;
    }
}
