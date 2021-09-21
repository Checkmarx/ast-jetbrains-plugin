package com.checkmarx.intellij;

import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

/**
 * Utils for interacting with the CLI through the java wrapper.
 */
public final class CLI {

    private static String cxBinaryFileName;

    private CLI() {
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
    public static String getTempBinary() throws IOException {

        if (cxBinaryFileName == null) {
            cxBinaryFileName = detectBinary();
        }

        URL executablePath = CLI.class.getClassLoader()
                                      .getResource(cxBinaryFileName);

        if (executablePath == null) {
            throw new IOException(Bundle.message(Resource.CLI_MISSING));
        }

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

    /**
     * Detect binary name by the current architecture.
     *
     * @return binary name
     * @throws IOException when architecture is unsupported
     */
    @Nonnull
    private static String detectBinary() throws IOException {
        final String arch = Constants.OS_NAME;
        if (arch.contains(Constants.OS_LINUX)) {
            return Constants.FILE_NAME_LINUX;
        } else if (arch.contains(Constants.OS_WINDOWS)) {
            return Constants.FILE_NAME_WINDOWS;
        } else {
            for (String macStr : Constants.OS_MAC) {
                if (arch.contains(macStr)) {
                    return Constants.FILE_NAME_MAC;
                }
            }
        }
        throw new IOException(Bundle.message(Resource.CLI_ARCH, arch));
    }
}
