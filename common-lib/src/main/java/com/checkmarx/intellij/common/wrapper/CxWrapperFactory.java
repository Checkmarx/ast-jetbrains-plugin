package com.checkmarx.intellij.common.wrapper;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.utils.Utils;

import java.io.IOException;

/**
 * Builds wrapper objects according to the current configuration.
 */
public class CxWrapperFactory {

    public static CxWrapper build() throws CxException, IOException {
        return build(GlobalSettingsState.getInstance(), GlobalSettingsSensitiveState.getInstance());
    }

    public static CxWrapper build(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState)
            throws CxException, IOException {
        final CxConfig.CxConfigBuilder builder = CxConfig.builder();

        builder.agentName(Constants.JET_BRAINS_AGENT_NAME);
        if(isCredentialExpired(state, sensitiveState)){
            Utils.notifySessionExpired();
            return new CxWrapper(builder.build());
        }
        if (state.isApiKeyEnabled()) {
            builder.apiKey(sensitiveState.getApiKey());
        } else {
            builder.apiKey(sensitiveState.getRefreshToken());
            builder.clientId(Constants.AuthConstants.OAUTH_IDE_CLIENT_ID);
        }
        builder.additionalParameters(state.getAdditionalParameters());
        return new CxWrapper(builder.build());
    }

    /**
     * Checking if credential is expired or not based on login method.
     * If a user is logged in using an API key, then its check API key expiry else checks refresh token expiry.
     * If credentials are expired, then show a session expiration message and publish new state.
     *
     * @param state          GlobalSettingsState object contains current plugin state
     * @param sensitiveState GlobalSettingsSensitiveState object contains encrypted credentials
     * @return true, if credentials expired else false
     */
    private static boolean isCredentialExpired(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState) {
        if (state.isApiKeyEnabled()) {
            return false;
        } else return sensitiveState.isTokenExpired(state.getRefreshTokenExpiry());
    }
}
