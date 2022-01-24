package com.checkmarx.intellij.commands;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import lombok.NonNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

/**
 * Handle triage related operations with the wrapper
 */
public class Triage {

    public static List<com.checkmarx.ast.predicate.Predicate> triageShow(@NonNull UUID projectId, String similarityId, String scanType)
            throws
            IOException,
            URISyntaxException,
            InterruptedException,
            CxConfig.InvalidCLIConfigException,
            CxException {

        return CxWrapperFactory.build().triageShow(projectId, similarityId, scanType);
    }

    public static void triageUpdate(@NonNull UUID projectId, String similarityId, String scanType, String state, String comment, String severity)
            throws
            IOException,
            URISyntaxException,
            InterruptedException,
            CxConfig.InvalidCLIConfigException,
            CxException {

        CxWrapperFactory.build().triageUpdate(projectId, similarityId, scanType, state, comment, severity);
    }
}
