package com.checkmarx.intellij.common.commands;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.common.utils.Constants;
import com.checkmarx.intellij.common.settings.global.CxWrapperFactory;
import lombok.NonNull;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Handle project related operations with the wrapper
 */
public class Project {

    public static List<com.checkmarx.ast.project.Project> getList()
            throws
            IOException,
            InterruptedException,
            CxException {

        return CxWrapperFactory.build().projectList("limit=10000");
    }

    public static List<String> getBranches(@NonNull UUID projectId, boolean isSCMProject)
            throws
            IOException,
            InterruptedException,
            CxException {

        List<String> branches = CxWrapperFactory.build().projectBranches(projectId, "");
        if(isSCMProject) {
            branches.add(0, Constants.USE_LOCAL_BRANCH);
        }

        return branches;
    }
}
