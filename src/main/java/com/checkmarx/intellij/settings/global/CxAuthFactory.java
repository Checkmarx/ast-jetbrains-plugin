package com.checkmarx.intellij.settings.global;

import com.checkmarx.ast.exceptions.CxException;
import com.checkmarx.ast.scans.CxAuth;
import com.checkmarx.ast.scans.CxScanConfig;
import com.checkmarx.intellij.CLI;
import com.checkmarx.intellij.Utils;

import java.io.IOException;
import java.net.URISyntaxException;

public class CxAuthFactory {

    public static CxAuth build() throws IOException, URISyntaxException, CxException {
        return build(GlobalSettingsState.getInstance(), GlobalSettingsSensitiveState.getInstance());
    }

    public static CxAuth build(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState)
            throws IOException, URISyntaxException, CxException {
        final CxScanConfig scan = new CxScanConfig();

        scan.setBaseUri(state.getServerURL());
        if (state.isUseAuthURL()) {
            scan.setBaseAuthUri(state.getAuthURL());
        }

        if (Utils.isNotEmptyOrBlank(state.getTenantName())) {
            scan.setTenant(state.getTenantName());
        }

        scan.setApiKey(sensitiveState.getApiKey());

        scan.setPathToExecutable(CLI.getTempBinary());

        scan.setAdditionalParameters(state.getAdditionalParameters());

        return new CxAuth(scan, null);
    }
}
