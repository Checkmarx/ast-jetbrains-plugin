package com.checkmarx.intellij.ast.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.common.wrapper.CxWrapperFactory;
import lombok.NonNull;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Handle triage related operations with the wrapper
 */
public class Triage {

    public static List<com.checkmarx.ast.predicate.Predicate> triageShow(@NonNull UUID projectId, String similarityId, String scanType)
            throws
            IOException,
            InterruptedException,
            CxException {

        return CxWrapperFactory.build().triageShow(projectId, similarityId, scanType);
    }

    public static void triageUpdate(@NonNull UUID projectId, String similarityId, String scanType, String state, String comment, String severity)
            throws
            IOException,
            InterruptedException,
            CxException {

        CxWrapperFactory.build().triageUpdate(projectId, similarityId, scanType, state, comment, severity);
    }
}
