package com.checkmarx.intellij.common.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.common.settings.GlobalSettingsSensitiveState;
import com.checkmarx.intellij.common.settings.GlobalSettingsState;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;

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
    public static String validateConnection(GlobalSettingsState state,
                                            GlobalSettingsSensitiveState sensitiveState)
            throws
            IOException,
            URISyntaxException,
            InterruptedException,
            CxException {

        return CxWrapperFactory.build(state, sensitiveState).authValidate();
    }
}
