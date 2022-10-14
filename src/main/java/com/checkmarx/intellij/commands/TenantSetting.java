package com.checkmarx.intellij.commands;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Handle tenant settings related operations with the wrapper
 */
public class TenantSetting {

    /**
     * Check if current tenant has permissions to scan from the IDE
     *
     * @return true if tenant has permissions to scan. false otherwise
     */
    @NotNull
    public static boolean isScanAllowed() throws
            CxConfig.InvalidCLIConfigException,
            IOException,
            URISyntaxException,
            CxException,
            InterruptedException {
        return CxWrapperFactory.build().ideScansEnabled();
    }
}
