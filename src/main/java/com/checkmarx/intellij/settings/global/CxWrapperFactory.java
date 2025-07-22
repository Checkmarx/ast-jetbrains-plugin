package com.checkmarx.intellij.settings.global;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.Utils;

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

        if (state.isApiKeyEnabled()) {
            builder.apiKey(sensitiveState.getApiKey());
        } else {
            if (sensitiveState.isTokenExpired(state.getRefreshTokenExpiry())) {
                Utils.notifySessionExpired();
            }
            builder.apiKey(sensitiveState.getRefreshToken());
            builder.clientId(Constants.AuthConstants.OAUTH_IDE_CLIENT_ID);
        }
        builder.additionalParameters(state.getAdditionalParameters());
        return new CxWrapper(builder.build());
    }
}
