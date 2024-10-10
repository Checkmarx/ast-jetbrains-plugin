package com.checkmarx.intellij.commands;

import com.checkmarx.ast.asca.ScanResult;
import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;

import java.io.IOException;
import java.net.URISyntaxException;

public class ASCA {
    public static ScanResult scanAsca(String path, boolean ascaLatestVersion, String agent)
            throws
            CxConfig.InvalidCLIConfigException,
            IOException,
            URISyntaxException,
            CxException,
            InterruptedException {
        return CxWrapperFactory.build().ScanAsca(path, ascaLatestVersion, agent);
    }

    public static ScanResult installAsca()
            throws CxConfig.InvalidCLIConfigException,
            IOException,
            URISyntaxException,
            CxException,
            InterruptedException {
        return CxWrapperFactory.build().ScanAsca("",true, Constants.JET_BRAINS_AGENT_NAME);
    }
}
