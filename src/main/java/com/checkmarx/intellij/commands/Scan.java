package com.checkmarx.intellij.commands;

import com.checkmarx.ast.exceptions.CxException;
import com.checkmarx.intellij.settings.global.CxAuthFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Handle scan related operations with the CLI wrapper
 */
public class Scan {

    /**
     * Get latest scan id, independent of project.
     *
     * @return scan id
     */
    @NotNull
    public static String getLatestScanId() throws IOException, URISyntaxException, InterruptedException, CxException {
        return CxAuthFactory.build().cxAstScanList().getScanObjectList().get(0).getID();
    }
}
