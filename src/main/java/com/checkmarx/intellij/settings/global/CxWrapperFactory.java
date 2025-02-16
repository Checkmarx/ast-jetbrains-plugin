package com.checkmarx.intellij.settings.global;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.ast.wrapper.CxWrapper;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URISyntaxException;

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
        builder.apiKey(sensitiveState.getApiKey());
        builder.additionalParameters("--debug " + state.getAdditionalParameters());

        return new CxWrapper(builder.build());
    }
}
