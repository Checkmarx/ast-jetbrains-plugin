package com.checkmarx.intellij;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Class for static, common util methods
 */
public final class Utils {

    private static final Logger LOGGER = getLogger(Utils.class);
    private static final SimpleDateFormat input = new SimpleDateFormat(Constants.INPUT_DATE_FORMAT);
    private static final SimpleDateFormat output = new SimpleDateFormat(Constants.OUTPUT_DATE_FORMAT);

    private Utils() {
        // forbid instantiation of the class
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

    public static String dateParser(String unformattedDate) {
        try {
            Date d = input.parse(unformattedDate);
            return output.format(d);
        } catch (ParseException e) {
            LOGGER.error(e);
        }
        return "Date unavailable";
    }

    public static void notify(Project project, String content, NotificationType type) {
        new Notification(Constants.NOTIFICATION_GROUP_ID,
                null,
                null,
                null,
                content,
                type,
                NotificationListener.URL_OPENING_LISTENER)
                .notify(project);
    }

    public static void notifyScan(String title, String message, Project project, Runnable func, NotificationType notificationType, String actionText) {
        Notification notification = new Notification(Constants.NOTIFICATION_GROUP_ID,
                null,
                title,
                null,
                message,
                notificationType,
                null);

        if (func != null) {
            notification.addAction(NotificationAction.createSimple(actionText, func));
        }

        notification.notify(project);
    }

    @Nullable
    public static Repository getRootRepository(Project project) {
        List<Repository> repositories = VcsRepositoryManager.getInstance(project)
                .getRepositories()
                .stream()
                .sorted(Comparator.comparing(r -> r.getRoot()
                        .toNioPath()))
                .collect(Collectors.toUnmodifiableList());
        Repository repository = null;
        if (CollectionUtils.isNotEmpty(repositories)) {
            repository = repositories.get(0);
            for (int i = 1; i < repositories.size(); i++) {
                if (!repositories.get(i).getRoot().toNioPath().startsWith(repository.getRoot().toNioPath())) {
                    repository = null;
                    break;
                }
            }
        }
        return repository;
    }

    /**
     * Generating Code verifier for PKCE
     * Used 32 random bytes (per spec recommendation).
     *
     * @return Generated ~43 characters code verifier string
     */
    public static String generateCodeVerifier() {
        try{
            byte[] codeVerifier = new byte[32];
            new SecureRandom().nextBytes(codeVerifier);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
        }catch (Exception exception){
            LOGGER.error("OAuth: Exception occur while generating code verifier. Root Cause:{}"
                    ,exception.getMessage());
            return null;
        }
    }

    /**
     * Generating code challenge for original code verifier using SHA-256
     *
     * @param codeVerifier - Generated code verifier
     * @return Generated hash of code verifier using SHA256
     */
    public static String generateCodeChallenge(String codeVerifier){
        try {
            MessageDigest digest = MessageDigest.getInstance(Constants.AuthConstants.ALGO_SHA256);
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception exception) {
            LOGGER.error("OAuth: Exception occur while generating code challenge. Root Cause:{}"
                    , exception.getMessage());
            return null;
        }
    }

    /**
     * Load HTML page and send in the response as a success message
     * @param resourcePath - file path which you want to load
     * @return html string
     */
    public static String loadAuthSuccessHtml(String resourcePath) throws IOException {
        InputStream input = Utils.class.getClassLoader().getResourceAsStream(resourcePath);
        if (input == null) {
            //add fallback method
            return "<html><body><h2>⚠ Error: HTML file not found.</h2></body></html>";
        }
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static String loadAuthErrorHtml(String resourcePath) throws IOException {
        InputStream input = Utils.class.getClassLoader().getResourceAsStream(resourcePath);
        if (input == null) {
            //add fallback method
            return "<html><body><h2>⚠ Error: HTML file not found.</h2></body></html>";
        }
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void showAuthNotification(String title, String content, NotificationType type, Project project) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("authGroup")
                .createNotification(title,
                        content,
                        type)
                .notify(project);
    }
}
