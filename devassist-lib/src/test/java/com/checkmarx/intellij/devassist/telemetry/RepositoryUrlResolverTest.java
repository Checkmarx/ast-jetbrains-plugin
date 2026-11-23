package com.checkmarx.intellij.devassist.telemetry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("RepositoryUrlResolver unit tests")
class RepositoryUrlResolverTest {

    // ===== sanitizeRemoteUrl =====

    @Test
    @DisplayName("sanitizeRemoteUrl_httpsWithCredentials_removesCredentialsAndGitSuffix")
    void sanitizeRemoteUrl_httpsWithCredentials() {
        assertEquals("https://github.com/org/repo",
                RepositoryUrlResolver.sanitizeRemoteUrl("https://user:token@github.com/org/repo.git"));
    }

    @Test
    @DisplayName("sanitizeRemoteUrl_scpLikeSsh_convertsToHttps")
    void sanitizeRemoteUrl_scpLikeSsh() {
        assertEquals("https://github.com/org/repo", RepositoryUrlResolver.sanitizeRemoteUrl("git@github.com:org/repo.git"));
        assertEquals("https://host.example/group/sub/repo", RepositoryUrlResolver.sanitizeRemoteUrl("host.example:group/sub/repo"));
    }

    @Test
    @DisplayName("sanitizeRemoteUrl_sshScheme_dropsUserAndPort")
    void sanitizeRemoteUrl_sshScheme() {
        assertEquals("https://host/org/repo", RepositoryUrlResolver.sanitizeRemoteUrl("ssh://git@host:2222/org/repo.git"));
    }

    @Test
    @DisplayName("sanitizeRemoteUrl_dropsQueryAndFragment")
    void sanitizeRemoteUrl_dropsQueryAndFragment() {
        assertEquals("https://host/org/repo", RepositoryUrlResolver.sanitizeRemoteUrl("https://host/org/repo/?token=abc#frag"));
    }

    @Test
    @DisplayName("sanitizeRemoteUrl_notARemote_returnsEmpty")
    void sanitizeRemoteUrl_notARemote() {
        assertEquals("", RepositoryUrlResolver.sanitizeRemoteUrl(null));
        assertEquals("", RepositoryUrlResolver.sanitizeRemoteUrl("   "));
        assertEquals("", RepositoryUrlResolver.sanitizeRemoteUrl("C:\\work\\repo"));
        assertEquals("", RepositoryUrlResolver.sanitizeRemoteUrl("file:///work/repo"));
        assertEquals("", RepositoryUrlResolver.sanitizeRemoteUrl("https://host"));
        assertEquals("", RepositoryUrlResolver.sanitizeRemoteUrl("not a url"));
    }

    // ===== parseRemoteUrl =====

    @Test
    @DisplayName("parseRemoteUrl_prefersOriginOverOtherRemotes")
    void parseRemoteUrl_prefersOrigin() {
        String url = RepositoryUrlResolver.parseRemoteUrl(Arrays.asList(
                "[core]", "\turl = https://not-a-remote/x",
                "[remote \"upstream\"]", "\turl = https://github.com/up/repo.git",
                "[remote \"origin\"]", "\turl = https://github.com/org/repo.git",
                "\tfetch = +refs/heads/*:refs/remotes/origin/*"));

        assertEquals("https://github.com/org/repo.git", url);
    }

    @Test
    @DisplayName("parseRemoteUrl_noOrigin_usesFirstRemote")
    void parseRemoteUrl_noOrigin_usesFirstRemote() {
        String url = RepositoryUrlResolver.parseRemoteUrl(Arrays.asList(
                "[remote \"upstream\"]", "\turl = https://github.com/up/repo.git",
                "[remote \"other\"]", "\turl = https://github.com/other/repo.git"));

        assertEquals("https://github.com/up/repo.git", url);
    }

    @Test
    @DisplayName("parseRemoteUrl_noRemotes_returnsEmpty")
    void parseRemoteUrl_noRemotes() {
        assertEquals("", RepositoryUrlResolver.parseRemoteUrl(Arrays.asList("[core]", "\tbare = false")));
    }

    // ===== resolve =====

    @Test
    @DisplayName("resolve_fileInsideRepository_readsOriginFromGitConfig")
    void resolve_fileInsideRepository(@TempDir Path root) throws IOException {
        writeGitConfig(root.resolve(".git"), "git@github.com:org/repo.git");
        Path source = Files.createDirectories(root.resolve("src/main")).resolve("App.java");
        Files.writeString(source, "class App {}");

        assertEquals("https://github.com/org/repo", RepositoryUrlResolver.resolve(source.toString()));
    }

    @Test
    @DisplayName("resolve_gitFilePointingToGitDir_isFollowed")
    void resolve_gitFilePointingToGitDir(@TempDir Path root) throws IOException {
        Path realGitDir = root.resolve("real-git");
        writeGitConfig(realGitDir, "https://github.com/org/worktree-repo.git");
        Path worktree = Files.createDirectories(root.resolve("work"));
        Files.writeString(worktree.resolve(".git"), "gitdir: ../real-git\n");
        Path source = worktree.resolve("a.txt");
        Files.writeString(source, "x");

        assertEquals("https://github.com/org/worktree-repo", RepositoryUrlResolver.resolve(source.toString()));
    }

    @Test
    @DisplayName("resolve_configChanges_isReadAgain")
    void resolve_configChanges_isReadAgain(@TempDir Path root) throws IOException {
        Path gitDir = root.resolve(".git");
        writeGitConfig(gitDir, "https://github.com/org/first.git");
        Path source = root.resolve("a.txt");
        Files.writeString(source, "x");
        assertEquals("https://github.com/org/first", RepositoryUrlResolver.resolve(source.toString()));

        writeGitConfig(gitDir, "https://github.com/org/second.git");
        Files.setLastModifiedTime(gitDir.resolve("config"), java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5000));

        assertEquals("https://github.com/org/second", RepositoryUrlResolver.resolve(source.toString()));
    }

    @Test
    @DisplayName("resolve_noRepositoryOrBadInput_returnsEmpty")
    void resolve_noRepositoryOrBadInput(@TempDir Path root) throws IOException {
        Path source = root.resolve("a.txt");
        Files.writeString(source, "x");

        assertEquals("", RepositoryUrlResolver.resolve(null));
        assertEquals("", RepositoryUrlResolver.resolve(" "));
        assertEquals("", RepositoryUrlResolver.resolve("\0invalid"));
    }

    private static void writeGitConfig(Path gitDir, String originUrl) throws IOException {
        Files.createDirectories(gitDir);
        Files.write(gitDir.resolve("config"), Arrays.asList("[core]", "\tbare = false", "[remote \"origin\"]", "\turl = " + originUrl));
    }
}
