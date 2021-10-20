package com.checkmarx.intellij.settings.global;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxWrapper;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Builds wrapper objects according to the current configuration.
 */
public class CxWrapperFactory {

    public static CxWrapper build() throws IOException, URISyntaxException, CxConfig.InvalidCLIConfigException {
        return build(GlobalSettingsState.getInstance(), GlobalSettingsSensitiveState.getInstance());
    }

    public static CxWrapper build(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState)
            throws IOException, CxConfig.InvalidCLIConfigException {
        final CxConfig.CxConfigBuilder builder = CxConfig.builder();

        builder.baseUri(state.getServerURL());
        if (state.isUseAuthURL()) {
            builder.baseAuthUri(state.getAuthURL());
        }

        if (StringUtils.isNotBlank(state.getTenantName())) {
            builder.tenant(state.getTenantName());
        }

        builder.apiKey(sensitiveState.getApiKey());

        builder.additionalParameters("--debug " + state.getAdditionalParameters());

        return new CxWrapper(builder.build());
    }
}
