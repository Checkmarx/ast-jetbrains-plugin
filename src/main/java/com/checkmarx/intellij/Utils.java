package com.checkmarx.intellij;

import com.checkmarx.ast.wrapper.CxException;
import com.checkmarx.intellij.settings.SettingsListener;
import com.checkmarx.intellij.settings.global.GlobalSettingsState;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.messages.MessageBus;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class for static, common util methods
 */
public final class Utils {

    public static final String EMPTY = "";
    private static final Logger LOGGER = getLogger(Utils.class);
    private static final SimpleDateFormat input = new SimpleDateFormat(Constants.INPUT_DATE_FORMAT);
    private static final SimpleDateFormat output = new SimpleDateFormat(Constants.OUTPUT_DATE_FORMAT);

    private static Project cxProject;
    private static MessageBus messageBus;

    private static Project getCxProject() {
        if (cxProject == null && ApplicationManager.getApplication() != null) {
            cxProject = ProjectManager.getInstance().getDefaultProject();
        }
        return cxProject;
    }

    private static MessageBus getMessageBus() {
        if (messageBus == null && ApplicationManager.getApplication() != null) {
            messageBus = ApplicationManager.getApplication().getMessageBus();
        }
        return messageBus;
    }

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
        NotificationGroupManager.getInstance()
                .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
                .createNotification(content, type)
                .notify(project);
    }

    public static void notifyScan(String title, String message, Project project, Runnable func, NotificationType notificationType, String actionText) {
        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
                .createNotification(title, message, notificationType);

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
        try {
            SecureRandom secureRandom = getSecureRandom();
            byte[] codeVerifier = new byte[32];
            secureRandom.nextBytes(codeVerifier);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
        } catch (Exception exception) {
            LOGGER.warn(String.format("OAuth: Exception occurred while generating code verifier. Root Cause:%s"
                    , exception.getMessage()));
            return null;
        }
    }

    /**
     * Getting {@link SecureRandom} object using specific algorithm, if specified algorithm
     * not available the generating default SecureRandom object
     *
     * @return SecureRandom
     */
    private static SecureRandom getSecureRandom() {
        try {
            return SecureRandom.getInstance("DRBG");
        } catch (Exception exception) {
            LOGGER.warn(String.format("OAuth: Exception occurred while getting SecureRandom with DRBG. Root Cause:%s" +
                            " Now getting default SecureRandom"
                    , exception.getMessage()));
            return new SecureRandom();
        }
    }

    /**
     * Generating code challenge for original code verifier using SHA-256
     *
     * @param codeVerifier - Generated code verifier
     * @return Generated hash of code verifier using SHA256
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance(Constants.AuthConstants.ALGO_SHA256);
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception exception) {
            LOGGER.warn(String.format("OAuth: Exception occurred while generating code challenge. Root Cause:%s"
                    , exception.getMessage()));
            return null;
        }
    }

    /**
     * Open Confirmation dialog box for the user
     *
     * @param message       - message to display in confirmation dialog
     * @param title         - title for confirmation dialog
     * @param yesButtonText - yes button text for (e.g., ok)
     * @param noButtonText  - no button text (e.g., cancel)
     * @return true if user clicked on yes otherwise false
     */
    public static boolean openConfirmation(String message, String title, String yesButtonText, String noButtonText) {
        return Messages.showYesNoDialog(
                message,
                title,
                yesButtonText,
                noButtonText,
                Messages.getQuestionIcon()
        ) == Messages.YES;
    }

    /**
     * Load a file from the provided resource path and return a file content as a string
     *
     * @param resourcePath - file path which you want to load
     * @return string - file content
     */
    public static String getFileContentFromResource(String resourcePath) {
        try {
            InputStream input = Utils.class.getClassLoader().getResourceAsStream(resourcePath);
            if (input != null) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception exception) {
            LOGGER.warn(String.format("Load Resource: Unable to load file from the path:%s. Root Cause:%s",
                    resourcePath, exception.getMessage()));
        }
        return null;
    }

    /**
     * Display simple ballon notification in notification area
     *
     * @param title   - Title for notification
     * @param content - Message to display as notification
     * @param type    - Notification type e.g., WARNING, ERROR, INFO etc.
     * @param project - Current project instance
     */
    public static void showNotification(String title, String content, NotificationType type, Project project,boolean displayDockLink, String dockLink) {
      Notification notification =  NotificationGroupManager.getInstance()
                .getNotificationGroup(Constants.NOTIFICATION_GROUP_ID)
                .createNotification(title,
                        content,
                        type);

      if(displayDockLink){
          notification.addAction(NotificationAction.createSimple("Go To documentation", () -> BrowserUtil.browse(dockLink)));
      }
      notification.notify(project);

    }

    /**
     * Executing action with specified max retry attempts.
     * Before going for the every next retry attempt, it will increase delay time by specified delay milliseconds
     *
     * @param action             - {@link Supplier} object which contains the action to execute
     * @param maxRetries         - maximum number of retry attempts
     * @param initialDelayMillis - dealy in milliseconds for a fist attempt
     * @return action result
     * @throws Exception after all attempts if action result not received
     * @apiNote For every next retry attempt delay will be increase by attempt * initialDelayMillis
     */
    public static <T> T executeWithRetry(Supplier<T> action, int maxRetries, long initialDelayMillis) throws Exception {
        Exception lastException = new CxException(500, "Something went wrong, Please try again.");
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return action.get();
            } catch (Exception exception) {
                lastException = exception;

                if (attempt == maxRetries) break;

                long delay = attempt * initialDelayMillis;
                LOGGER.info(String.format("Retry: Attempt:%d failed:%s. Retrying in:%d ms.", attempt, exception.getMessage(), delay));
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.debug("Retry: Exception occurred while delaying after attempt:{}", attempt);
                }
            }
        }
        throw lastException;
    }

    /**
     * Adding duration in seconds to the current date
     *
     * @param duration - seconds to be added in current date time
     * @return updated LocalDateTime
     */
    public static LocalDateTime convertToLocalDateTime(Long duration, ZoneId zoneId) {
        return Instant.now()
                .plusSeconds(duration)
                .atZone(zoneId)
                .toLocalDateTime();
    }

    /**
     * Notify on user session expired and publish new state
     */
    public static void notifySessionExpired() {
        ApplicationManager.getApplication().invokeLater(() ->
                Utils.showNotification(Bundle.message(Resource.SESSION_EXPIRED_TITLE),
                        Bundle.message(Resource.ERROR_SESSION_EXPIRED),
                        NotificationType.ERROR,
                        getCxProject(),false,"")
        );
        ApplicationManager.getApplication().invokeLater(() ->
                getMessageBus().syncPublisher(SettingsListener.SETTINGS_APPLIED).settingsApplied()
        );
    }

    /**
     * Checking the requested filter is enabled or not by the user
     *
     * @param enabledFilterValues Set<String> which contains enabled filters values
     * @param filterValue         - Label of filter {@link com.checkmarx.intellij.service.StateService}
     * @return true if requester filter is present in enabledFilterValues otherwise false
     */
    public static boolean isFilterEnabled(Set<String> enabledFilterValues, String filterValue) {
        return enabledFilterValues != null && !enabledFilterValues.isEmpty() && enabledFilterValues.contains(filterValue);
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static int length(CharSequence cs) {
        if (cs == null)
            return 0;
        else
            return cs.length();
    }

    public static boolean isBlank(CharSequence cs) {
        int strLen = length(cs);
        if (strLen == 0) {
            return true;
        } else {
            for (int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(cs.charAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Escape HTML special characters
     *
     * @param text String to escape
     * @return Escaped string
     */
    public static String escapeHtml(String text) {
        if (Objects.isNull(text) || text.isBlank()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Check if the user is authenticated or not
     *
     * @return true if a user is authenticated otherwise false
     */
    public static boolean isUserAuthenticated() {
        try {
            return GlobalSettingsState.getInstance().isAuthenticated();
        } catch (Exception e) {
            LOGGER.error("Exception occurred while checking user authentication.", e.getMessage());
            return false;
        }
    }
}