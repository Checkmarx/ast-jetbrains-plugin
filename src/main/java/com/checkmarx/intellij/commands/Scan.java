package com.checkmarx.intellij.commands;

import com.checkmarx.ast.wrapper.CxConfig;
import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.settings.global.CxWrapperFactory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handle scan related operations with the CLI wrapper
 */
public class Scan {

    private static final DateTimeFormatter sourceFormat = DateTimeFormatter.ISO_DATE_TIME;
    private static final DateTimeFormatter prettyFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final String scanFormat = "%s    %s";

    /**
     * Get latest scan id, independent of project.
     *
     * @return scan id
     */
    @NotNull
    public static String getLatestScanId() throws
            CxConfig.InvalidCLIConfigException,
            IOException,
            URISyntaxException,
            CxException,
            InterruptedException {
        return CxWrapperFactory.build().scanList().get(0).getID();
    }

    @NotNull
    public static List<String> getList()
            throws
            IOException,
            URISyntaxException,
            InterruptedException,
            CxConfig.InvalidCLIConfigException,
            CxException {

        return CxWrapperFactory.build()
                               .scanList("limit=10000")
                               .stream()
                               .map(scan -> String.format(scanFormat,
                                                          LocalDateTime.parse(scan.getCreatedAt(), Scan.sourceFormat)
                                                                       .format(prettyFormat),
                                                          scan.getID()))
                               .collect(Collectors.toList());
    }

    @NotNull
    public static List<String> getList(String projectId)
            throws
            IOException,
            URISyntaxException,
            InterruptedException,
            CxConfig.InvalidCLIConfigException,
            CxException {

        return CxWrapperFactory.build()
                               .scanList(String.format("project-id=%s,limit=10000", projectId))
                               .stream()
                               .map(scan -> String.format(scanFormat,
                                                          LocalDateTime.parse(scan.getCreatedAt(), Scan.sourceFormat)
                                                                       .format(prettyFormat),
                                                          scan.getID()))
                               .collect(Collectors.toList());
    }
}
