package com.checkmarx.intellij;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Utils for interacting with the CLI through the java wrapper.
 */
public final class Execution {

    private Execution() {
        // forbid instantiation of the class
    }

    /**
     * Get path to a CLI binary in the environment's temp folder.
     * If the binary does not exist in the temp folder, or it is outdated by md5, copies it before returning the path.
     *
     * @return path to binary
     * @throws IOException when reading the file fails
     */
    @Nonnull
    public static String getTempBinary() throws IOException, URISyntaxException {

        URL executablePath = com.checkmarx.ast.wrapper.Execution.detectBinary().toURL();

        String cxBinaryFileName = FilenameUtils.getName(executablePath.getPath());

        File tempExecutable = new File(Constants.OS_TEMP_DIR, cxBinaryFileName);

        if (!tempExecutable.exists() || !Utils.compareChecksum(executablePath.openStream(),
                                                               new FileInputStream(tempExecutable))) {
            FileUtils.copyURLToFile(executablePath, tempExecutable);
        }

        if (!tempExecutable.canExecute() && !tempExecutable.setExecutable(true)) {
            throw new IOException(Bundle.message(Resource.CLI_EXECUTABLE, tempExecutable.getAbsolutePath()));
        }

        return tempExecutable.getAbsolutePath();
    }
}
