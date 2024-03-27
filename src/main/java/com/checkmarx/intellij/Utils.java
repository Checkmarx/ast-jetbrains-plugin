package com.checkmarx.intellij;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Class for static, common util methods.
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
}
