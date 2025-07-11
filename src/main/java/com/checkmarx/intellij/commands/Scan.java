package com.checkmarx.intellij.commands;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.Constants;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handle scan related operations with the wrapper
 */
public class Scan {

    /**
     * Get latest scan id, independent of project.
     *
     * @return scan id
     */
    @NotNull
    public static String getLatestScanId() throws
            IOException,
            CxException,
            InterruptedException {
        return CxWrapperFactory.build().scanList().get(0).getId();
    }

    /**
     * Get scan list for a specific project.
     *
     * @param projectId id for project
     * @param branch    branch name
     * @return scan list for project
     */
    @NotNull
    public static List<com.checkmarx.ast.scan.Scan> getList(String projectId, String branch)
            throws
            IOException,
            URISyntaxException,
            InterruptedException,
            CxException {

        return CxWrapperFactory.build()
                               .scanList(String.format("project-id=%s,branch=%s,limit=20000,statuses=Completed",
                                                       projectId,
                                                       branch));
    }

    /**
     * Get scan info by scan id
     *
     * @param scanId scan id
     * @return scan object
     */
    public static com.checkmarx.ast.scan.Scan scanShow(String scanId)
            throws
            IOException,
            URISyntaxException,
            InterruptedException,
            CxException {
        return CxWrapperFactory.build().scanShow(UUID.fromString(scanId));
    }

    @NotNull
    public static com.checkmarx.ast.scan.Scan scanCreate(String sourcePath, String projectName, String branchName) throws
            IOException,
            CxException,
            InterruptedException {

        Map<String, String> scanArguments = new HashMap<>();
        scanArguments.put("-s", sourcePath);
        scanArguments.put("--project-name", projectName);
        scanArguments.put("--branch", branchName);
        scanArguments.put("--agent", Constants.JET_BRAINS_AGENT_NAME);

        String additionalParameters = "--async --sast-incremental --resubmit";

        return CxWrapperFactory.build().scanCreate(scanArguments, additionalParameters);
    }

    public static void scanCancel(String scanId) throws
            IOException,
            CxException,
            InterruptedException {

        CxWrapperFactory.build().scanCancel(scanId);
    }
}
