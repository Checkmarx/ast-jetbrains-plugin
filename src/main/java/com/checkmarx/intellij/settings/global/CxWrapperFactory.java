package com.checkmarx.intellij.settings.global;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxWrapper;
import com.checkmarx.intellij.Execution;
import com.checkmarx.intellij.Utils;

import java.io.IOException;
import java.net.URISyntaxException;

public class CxWrapperFactory {

    public static CxWrapper build() throws IOException, URISyntaxException, CxConfig.InvalidCLIConfigException {
        return build(GlobalSettingsState.getInstance(), GlobalSettingsSensitiveState.getInstance());
    }

    public static CxWrapper build(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState)
            throws IOException, CxConfig.InvalidCLIConfigException, URISyntaxException {
        final CxConfig.CxConfigBuilder builder = CxConfig.builder();

        builder.baseUri(state.getServerURL());
        if (state.isUseAuthURL()) {
            builder.baseAuthUri(state.getAuthURL());
        }

        if (Utils.isNotEmptyOrBlank(state.getTenantName())) {
            builder.tenant(state.getTenantName());
        }

        builder.apiKey(sensitiveState.getApiKey());

        builder.pathToExecutable(Execution.getTempBinary());

        builder.additionalParameters(state.getAdditionalParameters());

        return new CxWrapper(builder.build());
    }
}
