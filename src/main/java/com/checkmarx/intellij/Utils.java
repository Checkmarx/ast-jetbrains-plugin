package com.checkmarx.intellij;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Class for static, common util methods.
 */
public final class Utils {

    private static final Logger LOGGER = getLogger(Utils.class);

    private Utils() {
        // forbid instantiation of the class
    }

    /**
     * Compare two {@link InputStream} by their md5 digest.
     *
     * @param a InputStream a
     * @param b InputStream b
     * @return whether a and b's MD5 matches
     * @throws IOException when reading the streams or calculating MD5 fails
     */
    public static boolean compareChecksum(InputStream a, InputStream b) throws IOException {
        return Objects.equals(DigestUtils.md5Hex(a), DigestUtils.md5Hex(b));
    }

    /**
     * Return an intellij logger for a class.
     *
     * @param cls class to obtain the logger for
     * @return intellij logger
     */
    public static Logger getLogger(Class<?> cls) {
        return Logger.getInstance(Constants.LOGGER_CAT_PREFIX + cls.getSimpleName());
    }

    /**
     * Build a string stating the scan is latest according to the argument
     *
     * @param latest if the scan is latest
     * @return formatted or blank string
     */
    public static String formatLatest(boolean latest) {
        return latest ? String.format(" (%s)", Bundle.message(Resource.LATEST_SCAN)) : "";
    }

    /**
     * Run runnable as a {@link com.intellij.openapi.application.ReadAction} in a new thread,
     * using {@link CompletableFuture#runAsync(Runnable)}
     *
     * @param runnable runnable to wrap
     */
    public static void runAsyncReadAction(Runnable runnable) {
        CompletableFuture.runAsync(() -> ApplicationManager.getApplication().runReadAction(runnable));
    }

    /**
     * Validates the calling thread for being the EDT.
     *
     * @return whether the thread is EDT
     */
    public static boolean validThread() {
        if (!EventQueue.isDispatchThread()) {
            LOGGER.info("Invalid thread");
            return false;
        }
        return true;
    }
}
