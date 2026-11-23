package com.checkmarx.intellij.devassist.telemetry;

import com.checkmarx.intellij.common.utils.Utils;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the repository URL sent with scan and remediation events.
 * <p>
 * It reads the git config file directly, so no git process is started for each scan. The result is cached
 * until the config file changes. The URL never contains credentials, query or fragment, and SSH remotes
 * are converted to https.
 */
public final class RepositoryUrlResolver {

    private static final Logger LOGGER = Utils.getLogger(RepositoryUrlResolver.class);
    private static final Pattern SCP_LIKE = Pattern.compile("^(?:[^@/\\s]+@)?([^:/\\s]+):(?!//)(.+)$");
    private static final Pattern HAS_SCHEME = Pattern.compile("^[a-z][a-z0-9+.-]*://.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern WINDOWS_PATH = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern REMOTE_SECTION = Pattern.compile("^\\[remote\\s+\"([^\"]+)\"]\\s*$");
    private static final Pattern SECTION = Pattern.compile("^\\[.*]\\s*$");
    private static final Pattern URL_LINE = Pattern.compile("^url\\s*=\\s*(.+)$");
    private static final List<String> SUPPORTED_SCHEMES = List.of("http", "https", "ssh", "git");
    private static final String ORIGIN = "origin";
    private static final String GIT_DIR = ".git";

    private static final Map<Path, CachedRemote> CACHE = new ConcurrentHashMap<>();

    private RepositoryUrlResolver() {
    }

    /**
     * Sanitized remote URL of the repository that contains the file, or an empty string when there is none.
     * Never throws.
     */
    public static String resolve(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            Path configFile = findConfigFile(Paths.get(filePath));
            return configFile == null ? "" : readCached(configFile);
        } catch (Exception e) {
            LOGGER.debug("Telemetry: could not resolve repository URL", e);
            return "";
        }
    }

    /**
     * Removes credentials, query and fragment from a git remote URL, and converts SSH remotes to https.
     * Returns an empty string for anything that is not a recognisable remote.
     * <pre>
     * https://user:token@github.com/org/repo.git -> https://github.com/org/repo
     * git@github.com:org/repo.git                -> https://github.com/org/repo
     * ssh://git@host:2222/org/repo.git           -> https://host/org/repo
     * </pre>
     */
    public static String sanitizeRemoteUrl(String remoteUrl) {
        String raw = remoteUrl == null ? "" : remoteUrl.trim();
        if (raw.isEmpty() || WINDOWS_PATH.matcher(raw).matches()) {
            return "";
        }
        String host;
        String repoPath;
        Matcher scp = SCP_LIKE.matcher(raw);
        if (scp.matches() && !HAS_SCHEME.matcher(raw).matches()) {
            host = scp.group(1);
            repoPath = scp.group(2);
        } else {
            try {
                URI uri = new URI(raw);
                if (uri.getScheme() == null || !SUPPORTED_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))) {
                    return "";
                }
                host = uri.getHost();
                repoPath = uri.getPath();
            } catch (Exception e) {
                return "";
            }
        }
        if (host == null || repoPath == null) {
            return "";
        }
        repoPath = repoPath.replaceAll("^/+", "").replaceAll("/+$", "").replaceAll("(?i)\\.git$", "");
        return host.isEmpty() || repoPath.isEmpty() ? "" : "https://" + host + "/" + repoPath;
    }

    /**
     * Picks the remote named origin, or the first remote, from the content of a git config file.
     */
    static String parseRemoteUrl(List<String> configLines) {
        String currentRemote = null;
        String firstUrl = null;
        for (String rawLine : configLines) {
            String line = rawLine.trim();
            Matcher remote = REMOTE_SECTION.matcher(line);
            if (remote.matches()) {
                currentRemote = remote.group(1);
                continue;
            }
            if (SECTION.matcher(line).matches()) {
                currentRemote = null;
                continue;
            }
            Matcher url = URL_LINE.matcher(line);
            if (currentRemote != null && url.matches()) {
                if (ORIGIN.equals(currentRemote)) {
                    return url.group(1).trim();
                }
                if (firstUrl == null) {
                    firstUrl = url.group(1).trim();
                }
            }
        }
        return firstUrl == null ? "" : firstUrl;
    }

    private static String readCached(Path configFile) throws IOException {
        long modified = Files.getLastModifiedTime(configFile).toMillis();
        CachedRemote cached = CACHE.get(configFile);
        if (cached != null && cached.lastModified == modified) {
            return cached.url;
        }
        String url = sanitizeRemoteUrl(parseRemoteUrl(Files.readAllLines(configFile, StandardCharsets.UTF_8)));
        CACHE.put(configFile, new CachedRemote(modified, url));
        return url;
    }

    /**
     * Walks up from the file to the repository root and returns its git config file.
     * A .git file (worktree or submodule) points to the real git directory.
     */
    private static Path findConfigFile(Path start) throws IOException {
        Path dir = Files.isDirectory(start) ? start : start.getParent();
        while (dir != null) {
            Path git = dir.resolve(GIT_DIR);
            if (Files.isDirectory(git)) {
                return existingConfig(git);
            }
            if (Files.isRegularFile(git)) {
                Path gitDir = readGitDirPointer(git, dir);
                return gitDir == null ? null : existingConfig(gitDir);
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static Path existingConfig(Path gitDir) throws IOException {
        Path commonDir = gitDir;
        Path commonDirFile = gitDir.resolve("commondir");
        if (Files.isRegularFile(commonDirFile)) {
            commonDir = gitDir.resolve(Files.readString(commonDirFile).trim()).normalize();
        }
        Path config = commonDir.resolve("config");
        return Files.isRegularFile(config) ? config : null;
    }

    private static Path readGitDirPointer(Path gitFile, Path repoDir) throws IOException {
        for (String line : Files.readAllLines(gitFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("gitdir:")) {
                return repoDir.resolve(line.substring("gitdir:".length()).trim()).normalize();
            }
        }
        return null;
    }

    private static final class CachedRemote {
        private final long lastModified;
        private final String url;

        private CachedRemote(long lastModified, String url) {
            this.lastModified = lastModified;
            this.url = url;
        }
    }
}
