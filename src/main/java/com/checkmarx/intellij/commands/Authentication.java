package com.checkmarx.intellij.commands;

import com.checkmarx.intellij.settings.global.CxAuthFactory;
import com.checkmarx.intellij.settings.global.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Handle authentication related operations with the CLI wrapper
 */
public class Authentication {

    /**
     * Validate the connection to AST with the current configured settings.
     *
     * @param state          current state
     * @param sensitiveState current sensitive state
     * @return exit code from auth validate
     * @throws IOException          when getting the binary fails
     * @throws URISyntaxException   when the URI to the binary is malformed
     * @throws InterruptedException when the call to the CLI is interrupted
     */
    public static int validateConnection(GlobalSettingsState state, GlobalSettingsSensitiveState sensitiveState)
            throws IOException, URISyntaxException, InterruptedException {

        return CxAuthFactory.build(state, sensitiveState).cxAuthValidate();
    }
}
