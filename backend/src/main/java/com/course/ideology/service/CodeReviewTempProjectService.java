package com.course.ideology.service;

import com.course.ideology.api.dto.CodeReviewProjectCleanupResponse;
import com.course.ideology.api.dto.CodeReviewProjectDownloadResponse;
import com.course.ideology.api.dto.CodeReviewProjectFileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;

@Service
public class CodeReviewTempProjectService {
    private static final boolean WINDOWS_OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final Pattern HTTPS_GITHUB_PATTERN = Pattern.compile("^https?://github\\.com/([^/\\s]+)/([^/\\s?#]+?)(?:\\.git)?(?:[/?#].*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SSH_GITHUB_PATTERN = Pattern.compile("^git@github\\.com:([^/\\s]+)/([^/\\s?#]+?)(?:\\.git)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,80}$");
    private static final Pattern FILENAME_TOO_LONG_PATTERN = Pattern.compile("(?i)(filename too long|file name too long|path too long)");
    private static final int WINDOWS_PRIMARY_REPO_DIR_THRESHOLD = 90;
    private static final int WINDOWS_REPO_DIR_RISK_THRESHOLD = 160;
    private static final int MAX_PROJECT_STRUCTURE_ENTRIES = 3500;
    private static final int MAX_PREVIEW_BYTES = 220_000;
    private static final int CLONE_ERROR_TAIL_CHARS = 5000;
    private static final String WINDOWS_LONG_PATH_MESSAGE = "Git \u514b\u9686\u5931\u8d25\uff1aWindows \u6587\u4ef6\u8def\u5f84\u8fc7\u957f";
    private static final String META_FILE_NAME = "session-meta.json";
    private static final String REPO_DIR_NAME = "repo";
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "java", "kt", "kts", "py", "js", "ts", "tsx", "jsx", "vue", "go", "rs", "c", "cc", "cpp", "h", "hpp", "cs",
            "swift", "scala", "sh", "bash", "zsh", "md", "txt", "json", "yaml", "yml", "xml", "html", "css", "scss",
            "less", "gradle", "properties", "toml", "ini", "sql"
    );
    private static final Set<String> PRIORITY_FILE_NAMES = Set.of(
            "main.java", "main.py", "main.go", "main.ts", "main.js", "index.ts", "index.js", "app.java", "app.py",
            "app.ts", "app.js", "readme.md"
    );

    private final ObjectMapper objectMapper;
    private final Path tempRootPath;
    private final Path windowsShortCloneRootPath;
    private final Duration tempTtl;

    public CodeReviewTempProjectService(
            ObjectMapper objectMapper,
            @Value("${app.code-review.temp-root:workspace/temp/code-review}") String configuredTempRoot,
            @Value("${app.code-review.windows-short-clone-root:}") String configuredWindowsShortCloneRoot,
            @Value("${app.code-review.temp-ttl-hours:12}") long ttlHours
    ) {
        this.objectMapper = objectMapper;
        this.tempRootPath = resolveTempRoot(configuredTempRoot);
        this.windowsShortCloneRootPath = resolveWindowsShortCloneRoot(configuredWindowsShortCloneRoot, tempRootPath);
        this.tempTtl = Duration.ofHours(Math.max(1, ttlHours));
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(tempRootPath);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create code review temp root: " + tempRootPath, ex);
        }
        cleanupExpiredSessions();
    }

    @Scheduled(fixedDelayString = "${app.code-review.temp-clean-interval-ms:1800000}")
    public void scheduledCleanup() {
        cleanupExpiredSessions();
    }

    public synchronized CodeReviewProjectDownloadResponse downloadRepository(String pageSessionIdRaw, String repoUrlRaw, boolean forceRefresh) {
        String pageSessionId = normalizeSessionId(pageSessionIdRaw);
        RepoIdentity repoIdentity = parseGitHubRepo(repoUrlRaw);
        Path sessionDir = resolveSessionDir(pageSessionId);
        createDirectories(sessionDir);

        SessionMetadata existing = readSessionMeta(sessionDir);
        if (!forceRefresh && existing != null && repoIdentity.repoKey.equals(existing.repoKey)) {
            Path existingRepoDir = resolveRepoDir(sessionDir, existing);
            if (Files.isDirectory(existingRepoDir)) {
                touchSessionDir(sessionDir);
                return buildDownloadResponse(pageSessionId, existing, existingRepoDir, true, "Reused temporary project in current page session");
            }
        }

        Path previousRepoDir = existing == null ? null : resolveRepoDir(sessionDir, existing);
        deleteDirectoryQuietly(sessionDir);
        deleteRepoDirIfExternal(sessionDir, previousRepoDir);
        createDirectories(sessionDir);

        CloneTarget cloneTarget = resolveCloneTarget(pageSessionId, sessionDir);
        cloneRepository(repoIdentity.cloneUrl, cloneTarget.repoDir, sessionDir.resolve("clone.log"));

        SessionMetadata meta = new SessionMetadata();
        meta.projectId = UUID.randomUUID().toString();
        meta.repoUrl = repoIdentity.cloneUrl;
        meta.repoKey = repoIdentity.repoKey;
        meta.repoDirName = REPO_DIR_NAME;
        meta.repoAbsolutePath = cloneTarget.repoDir.toString();
        meta.updatedAtEpochMs = System.currentTimeMillis();
        writeSessionMeta(sessionDir, meta);
        touchSessionDir(sessionDir);

        String message = cloneTarget.usedShortRoot
                ? "Repository downloaded into temporary short workspace (Windows path optimization enabled)"
                : "Repository downloaded into temporary session workspace";
        return buildDownloadResponse(pageSessionId, meta, cloneTarget.repoDir, false, message);
    }

    public synchronized CodeReviewProjectFileResponse readProjectFile(String pageSessionIdRaw, String projectIdRaw, String filePathRaw) {
        String pageSessionId = normalizeSessionId(pageSessionIdRaw);
        String projectId = normalizeProjectId(projectIdRaw);
        String relativePath = normalizeRelativePath(filePathRaw);
        Path sessionDir = resolveSessionDir(pageSessionId);
        SessionMetadata meta = readSessionMeta(sessionDir);
        if (meta == null) {
            throw new IllegalArgumentException("Temporary project does not exist. Download repository first.");
        }
        if (!projectId.equals(meta.projectId)) {
            throw new IllegalArgumentException("projectId does not match current page session");
        }

        Path repoDir = resolveRepoDir(sessionDir, meta);
        Path target = resolveProjectFile(repoDir, relativePath);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new IllegalArgumentException("Target file does not exist or is not a regular file");
        }

        FilePreview preview = readPreview(target, relativePath);
        touchSessionDir(sessionDir);

        CodeReviewProjectFileResponse response = new CodeReviewProjectFileResponse();
        response.setPageSessionId(pageSessionId);
        response.setProjectId(projectId);
        response.setFilePath(relativePath);
        response.setContent(preview.content);
        response.setBinary(preview.binary);
        response.setTruncated(preview.truncated);
        return response;
    }

    public synchronized CodeReviewProjectCleanupResponse cleanupSession(String pageSessionIdRaw, String projectIdRaw) {
        String pageSessionId = normalizeSessionId(pageSessionIdRaw);
        String projectId = projectIdRaw == null ? "" : projectIdRaw.trim();
        Path sessionDir = resolveSessionDir(pageSessionId);

        CodeReviewProjectCleanupResponse response = new CodeReviewProjectCleanupResponse();
        response.setPageSessionId(pageSessionId);
        response.setProjectId(projectId);
        response.setRemovedPath(sessionDir.toString());

        if (!Files.exists(sessionDir)) {
            response.setSuccess(true);
            response.setMessage("Session directory does not exist");
            return response;
        }

        if (!projectId.isBlank()) {
            SessionMetadata meta = readSessionMeta(sessionDir);
            if (meta != null && !projectId.equals(meta.projectId)) {
                response.setSuccess(false);
                response.setMessage("projectId does not match current session directory");
                return response;
            }
        }
        SessionMetadata meta = readSessionMeta(sessionDir);
        Path repoDir = resolveRepoDir(sessionDir, meta);
        deleteDirectoryQuietly(sessionDir);
        deleteRepoDirIfExternal(sessionDir, repoDir);
        response.setSuccess(true);
        response.setMessage("Temporary session directory removed");
        return response;
    }

    public synchronized void cleanupExpiredSessions() {
        if (!Files.isDirectory(tempRootPath)) {
            return;
        }
        long now = System.currentTimeMillis();
        long ttlMillis = tempTtl.toMillis();
        try (Stream<Path> stream = Files.list(tempRootPath)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                long lastModified = readLastModifiedMillis(dir);
                if (now - lastModified > ttlMillis) {
                    SessionMetadata meta = readSessionMeta(dir);
                    Path repoDir = resolveRepoDir(dir, meta);
                    deleteDirectoryQuietly(dir);
                    deleteRepoDirIfExternal(dir, repoDir);
                }
            });
        } catch (IOException ignored) {
        }
    }

    private Path resolveTempRoot(String configuredTempRoot) {
        String candidate = configuredTempRoot == null || configuredTempRoot.isBlank() ? "workspace/temp/code-review" : configuredTempRoot.trim();
        Path configured = Paths.get(candidate);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize().resolve(configured).normalize();
    }

    private Path resolveWindowsShortCloneRoot(String configuredWindowsShortCloneRoot, Path defaultTempRoot) {
        if (!WINDOWS_OS) {
            return defaultTempRoot;
        }
        String configured = configuredWindowsShortCloneRoot == null ? "" : configuredWindowsShortCloneRoot.trim();
        if (!configured.isBlank()) {
            return Paths.get(configured).toAbsolutePath().normalize();
        }
        Path absoluteTempRoot = defaultTempRoot.toAbsolutePath().normalize();
        Path root = absoluteTempRoot.getRoot();
        if (root != null) {
            return root.resolve("cia").resolve("repo").toAbsolutePath().normalize();
        }
        return absoluteTempRoot.resolve("cia").resolve("repo").toAbsolutePath().normalize();
    }

    private String normalizeSessionId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!SESSION_ID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid pageSessionId");
        }
        return value;
    }

    private String normalizeProjectId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("projectId is required");
        }
        return value;
    }

    private String normalizeRelativePath(String raw) {
        String value = raw == null ? "" : raw.trim().replace('\\', '/');
        value = value.replaceAll("^/+", "").replaceAll("/+", "/");
        if (value.isBlank() || value.contains("..")) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return value;
    }

    private RepoIdentity parseGitHubRepo(String rawRepoUrl) {
        String input = rawRepoUrl == null ? "" : rawRepoUrl.trim();
        if (input.isBlank()) {
            throw new IllegalArgumentException("repoUrl is required");
        }

        Matcher httpsMatcher = HTTPS_GITHUB_PATTERN.matcher(input);
        if (httpsMatcher.matches()) {
            String owner = httpsMatcher.group(1);
            String repo = trimGitSuffix(httpsMatcher.group(2));
            return RepoIdentity.of(owner, repo, "https://github.com/" + owner + "/" + repo + ".git");
        }

        Matcher sshMatcher = SSH_GITHUB_PATTERN.matcher(input);
        if (sshMatcher.matches()) {
            String owner = sshMatcher.group(1);
            String repo = trimGitSuffix(sshMatcher.group(2));
            return RepoIdentity.of(owner, repo, "https://github.com/" + owner + "/" + repo + ".git");
        }

        throw new IllegalArgumentException("Only GitHub repository URL is supported");
    }

    private String trimGitSuffix(String value) {
        String text = value == null ? "" : value.trim();
        if (text.toLowerCase(Locale.ROOT).endsWith(".git")) {
            return text.substring(0, text.length() - 4);
        }
        return text;
    }

    private Path resolveSessionDir(String pageSessionId) {
        return tempRootPath.resolve(pageSessionId).normalize();
    }

    private CloneTarget resolveCloneTarget(String pageSessionId, Path sessionDir) {
        Path defaultRepoDir = sessionDir.resolve(REPO_DIR_NAME).toAbsolutePath().normalize();
        if (!WINDOWS_OS) {
            return new CloneTarget(defaultRepoDir, false);
        }

        if (absolutePathLength(defaultRepoDir) <= WINDOWS_PRIMARY_REPO_DIR_THRESHOLD) {
            if (absolutePathLength(defaultRepoDir) > WINDOWS_REPO_DIR_RISK_THRESHOLD) {
                throw buildPrecheckPathTooLongException(defaultRepoDir);
            }
            return new CloneTarget(defaultRepoDir, false);
        }

        Path shortRepoDir = windowsShortCloneRootPath.resolve(toShortSessionId(pageSessionId)).toAbsolutePath().normalize();
        if (absolutePathLength(shortRepoDir) > WINDOWS_REPO_DIR_RISK_THRESHOLD) {
            throw buildPrecheckPathTooLongException(shortRepoDir);
        }
        return new CloneTarget(shortRepoDir, true);
    }

    private Path resolveRepoDir(Path sessionDir, SessionMetadata meta) {
        if (meta != null) {
            String absolutePath = meta.repoAbsolutePath == null ? "" : meta.repoAbsolutePath.trim();
            if (!absolutePath.isBlank()) {
                return Paths.get(absolutePath).toAbsolutePath().normalize();
            }
        }
        String repoDirName = meta == null || meta.repoDirName == null || meta.repoDirName.isBlank()
                ? REPO_DIR_NAME
                : meta.repoDirName;
        return sessionDir.resolve(repoDirName).toAbsolutePath().normalize();
    }

    private int absolutePathLength(Path path) {
        return path.toAbsolutePath().normalize().toString().length();
    }

    private String toShortSessionId(String pageSessionId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(pageSessionId.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(pageSessionId.hashCode());
        }
    }

    private void createDirectories(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create directory: " + dir, ex);
        }
    }

    private void cloneRepository(String repoUrl, Path repoDir, Path cloneLog) {
        createDirectories(repoDir.getParent());
        createDirectories(cloneLog.getParent());
        try {
            Files.deleteIfExists(cloneLog);
        } catch (IOException ignored) {
        }
        deleteDirectoryQuietly(repoDir);

        List<String> cloneCommand = buildCloneCommand(repoUrl, repoDir);
        ProcessBuilder builder = new ProcessBuilder(cloneCommand);
        builder.redirectErrorStream(true);
        builder.redirectOutput(cloneLog.toFile());

        try {
            Process process = builder.start();
            boolean finished = process.waitFor(4, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new RepoCloneException(
                        "GIT_CLONE_TIMEOUT",
                        "Git clone failed: timeout",
                        "git clone did not finish within 4 minutes.",
                        List.of("Network may be slow, or repository size is large.", "Retry later after confirming repository accessibility.")
                );
            }
            if (process.exitValue() != 0) {
                String cloneOutput = readTailText(cloneLog, CLONE_ERROR_TAIL_CHARS);
                if (isFilenameTooLongError(cloneOutput)) {
                    throw buildFilenameTooLongException(repoDir, cloneOutput);
                }
                throw new RepoCloneException(
                        "GIT_CLONE_FAILED",
                        "Git clone failed",
                        cloneOutput,
                        List.of("Check repository URL and permissions.", "On Windows, avoid committing node_modules, dist, or build folders.")
                );
            }
        } catch (IOException ex) {
            throw new RepoCloneException(
                    "GIT_EXECUTION_FAILED",
                    "Git clone failed: cannot execute git command",
                    ex.getMessage(),
                    List.of("Ensure Git is installed and available in PATH.")
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RepoCloneException(
                    "GIT_CLONE_INTERRUPTED",
                    "Git clone failed: interrupted",
                    ex.getMessage(),
                    List.of("Retry this operation.")
            );
        }
    }

    private CodeReviewProjectDownloadResponse buildDownloadResponse(
            String pageSessionId,
            SessionMetadata meta,
            Path repoDir,
            boolean reused,
            String message
    ) {
        ProjectSnapshot snapshot = scanProject(repoDir);
        CodeReviewProjectDownloadResponse response = new CodeReviewProjectDownloadResponse();
        response.setPageSessionId(pageSessionId);
        response.setProjectId(meta.projectId);
        response.setRepoUrl(meta.repoUrl);
        response.setLocalPath(repoDir.toString());
        response.setProjectStructure(snapshot.projectStructure);
        response.setFocusFilePath(snapshot.focusFilePath);
        response.setFocusFileContent(snapshot.focusFileContent);
        response.setReused(reused);
        response.setMessage(message);
        return response;
    }

    private ProjectSnapshot scanProject(Path repoDir) {
        if (!Files.isDirectory(repoDir)) {
            throw new IllegalStateException("Repository directory does not exist: " + repoDir);
        }

        List<String> filePaths = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoDir)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                if (isGitInternalPath(repoDir, path)) {
                    return;
                }
                String relative = toRelativePath(repoDir, path);
                if (!relative.isBlank()) {
                    filePaths.add(relative);
                }
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to scan repository structure", ex);
        }

        filePaths.sort(Comparator.naturalOrder());
        List<String> limited = filePaths;
        boolean truncated = false;
        if (filePaths.size() > MAX_PROJECT_STRUCTURE_ENTRIES) {
            limited = new ArrayList<>(filePaths.subList(0, MAX_PROJECT_STRUCTURE_ENTRIES));
            truncated = true;
        }
        String projectStructure = String.join("\n", limited);
        if (truncated) {
            projectStructure = projectStructure + "\n... (truncated)";
        }

        String focusFilePath = resolveFocusFile(limited);
        String focusContent = "";
        if (!focusFilePath.isBlank()) {
            focusContent = readPreview(resolveProjectFile(repoDir, focusFilePath), focusFilePath).content;
        }

        ProjectSnapshot snapshot = new ProjectSnapshot();
        snapshot.projectStructure = projectStructure;
        snapshot.focusFilePath = focusFilePath;
        snapshot.focusFileContent = focusContent;
        return snapshot;
    }

    private String resolveFocusFile(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return "";
        }

        for (String path : filePaths) {
            String fileName = extractFileName(path).toLowerCase(Locale.ROOT);
            if (PRIORITY_FILE_NAMES.contains(fileName)) {
                return path;
            }
            if (fileName.startsWith("main.") || fileName.startsWith("app.") || fileName.startsWith("index.")) {
                return path;
            }
        }

        for (String path : filePaths) {
            if (looksLikeTextPath(path)) {
                return path;
            }
        }
        return filePaths.get(0);
    }

    private String extractFileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }

    private boolean looksLikeTextPath(String path) {
        String fileName = extractFileName(path).toLowerCase(Locale.ROOT);
        if ("dockerfile".equals(fileName) || fileName.startsWith("readme")) {
            return true;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dot + 1);
        return TEXT_EXTENSIONS.contains(ext);
    }

    private boolean isGitInternalPath(Path repoDir, Path path) {
        String relative = toRelativePath(repoDir, path);
        return relative.equals(".git") || relative.startsWith(".git/");
    }

    private String toRelativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private Path resolveProjectFile(Path repoDir, String relativePath) {
        Path normalizedRepo = repoDir.toAbsolutePath().normalize();
        Path resolved = normalizedRepo.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedRepo)) {
            throw new IllegalArgumentException("File path escapes repository root");
        }
        return resolved;
    }

    private FilePreview readPreview(Path filePath, String relativePath) {
        try {
            long size = Files.size(filePath);
            int readLength = (int) Math.min(size, MAX_PREVIEW_BYTES);
            byte[] bytes = readPrefixBytes(filePath, readLength);
            boolean binary = looksBinary(bytes) && !looksLikeTextPath(relativePath);
            boolean truncated = size > readLength;

            FilePreview preview = new FilePreview();
            preview.binary = binary;
            preview.truncated = truncated;
            if (binary) {
                preview.content = "// Binary file is not previewable in text mode: " + relativePath;
                return preview;
            }

            String content = new String(bytes, StandardCharsets.UTF_8);
            if (truncated) {
                content += "\n\n// [File preview truncated. Original file is larger than " + MAX_PREVIEW_BYTES + " bytes]";
            }
            preview.content = content;
            return preview;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file preview: " + relativePath, ex);
        }
    }

    private byte[] readPrefixBytes(Path filePath, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        try (InputStream inputStream = Files.newInputStream(filePath, StandardOpenOption.READ)) {
            while (offset < length) {
                int read = inputStream.read(buffer, offset, length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        if (offset == length) {
            return buffer;
        }
        byte[] resized = new byte[offset];
        System.arraycopy(buffer, 0, resized, 0, offset);
        return resized;
    }

    private boolean looksBinary(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        int suspicious = 0;
        for (byte value : bytes) {
            int current = value & 0xFF;
            if (current == 0) {
                return true;
            }
            if ((current < 0x09 || (current > 0x0D && current < 0x20)) && current != 0x1B) {
                suspicious += 1;
            }
        }
        return suspicious > bytes.length / 12;
    }

    private SessionMetadata readSessionMeta(Path sessionDir) {
        Path metaPath = sessionDir.resolve(META_FILE_NAME);
        if (!Files.exists(metaPath)) {
            return null;
        }
        try {
            return objectMapper.readValue(metaPath.toFile(), SessionMetadata.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private void writeSessionMeta(Path sessionDir, SessionMetadata metadata) {
        metadata.updatedAtEpochMs = System.currentTimeMillis();
        Path metaPath = sessionDir.resolve(META_FILE_NAME);
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metaPath.toFile(), metadata);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write session metadata", ex);
        }
    }

    private void touchSessionDir(Path sessionDir) {
        createDirectories(sessionDir);
        FileTime now = FileTime.from(Instant.now());
        try {
            Files.setLastModifiedTime(sessionDir, now);
        } catch (IOException ignored) {
        }
        SessionMetadata metadata = readSessionMeta(sessionDir);
        if (metadata != null) {
            writeSessionMeta(sessionDir, metadata);
        }
    }

    private long readLastModifiedMillis(Path dir) {
        try {
            SessionMetadata meta = readSessionMeta(dir);
            if (meta != null && meta.updatedAtEpochMs > 0) {
                return meta.updatedAtEpochMs;
            }
            return Files.getLastModifiedTime(dir).toMillis();
        } catch (IOException ex) {
            return System.currentTimeMillis();
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult postVisitDirectory(Path current, IOException exc) throws IOException {
                    Files.deleteIfExists(current);
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }

    private String readTailText(Path filePath, int maxChars) {
        if (!Files.exists(filePath)) {
            return "";
        }
        try {
            String text = Files.readString(filePath, StandardCharsets.UTF_8);
            if (text.length() <= maxChars) {
                return text;
            }
            return text.substring(text.length() - maxChars);
        } catch (IOException ex) {
            return "";
        }
    }

    private List<String> buildCloneCommand(String repoUrl, Path repoDir) {
        List<String> command = new ArrayList<>();
        command.add("git");
        if (WINDOWS_OS) {
            command.add("-c");
            command.add("core.longpaths=true");
        }
        command.add("clone");
        command.add("--depth");
        command.add("1");
        command.add("--single-branch");
        command.add("--no-tags");
        command.add(repoUrl);
        command.add(repoDir.toString());
        return command;
    }

    private boolean isFilenameTooLongError(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return FILENAME_TOO_LONG_PATTERN.matcher(text).find();
    }

    private RepoCloneException buildPrecheckPathTooLongException(Path repoDir) {
        String detail = "Target clone directory is still too long on Windows: " + repoDir
                + " (length=" + absolutePathLength(repoDir) + ")";
        return new RepoCloneException(
                "WINDOWS_PATH_TOO_LONG",
                WINDOWS_LONG_PATH_MESSAGE,
                detail,
                List.of(
                        "Local clone root is still too deep and may trigger checkout Filename too long.",
                        "Shorten local root or configure app.code-review.windows-short-clone-root (for example: E:\\cia\\repo)."
                )
        );
    }

    private RepoCloneException buildFilenameTooLongException(Path repoDir, String cloneOutput) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        hints.add("Repository checkout failed with Filename too long.");
        hints.add("Possible causes: node_modules/dist/build committed to repo, or local clone root is too deep.");
        if (cloneOutput != null && cloneOutput.toLowerCase(Locale.ROOT).contains("node_modules")) {
            hints.add("Detected node_modules paths in stderr. Remove them from repository and ignore via .gitignore.");
        }
        hints.add("If business allows, consider --no-checkout plus sparse-checkout to exclude node_modules/dist/build.");
        String detail = "cloneTarget=" + repoDir + "\n" + (cloneOutput == null ? "" : cloneOutput);
        return new RepoCloneException(
                "WINDOWS_PATH_TOO_LONG",
                WINDOWS_LONG_PATH_MESSAGE,
                detail,
                new ArrayList<>(hints)
        );
    }

    private void deleteRepoDirIfExternal(Path sessionDir, Path repoDir) {
        if (repoDir == null) {
            return;
        }
        Path normalizedSessionDir = sessionDir.toAbsolutePath().normalize();
        Path normalizedRepoDir = repoDir.toAbsolutePath().normalize();
        if (normalizedRepoDir.startsWith(normalizedSessionDir)) {
            return;
        }
        deleteDirectoryQuietly(normalizedRepoDir);
    }

    private static class RepoIdentity {
        private String repoKey;
        private String cloneUrl;

        private static RepoIdentity of(String owner, String repo, String cloneUrl) {
            RepoIdentity identity = new RepoIdentity();
            identity.repoKey = owner.toLowerCase(Locale.ROOT) + "/" + repo.toLowerCase(Locale.ROOT);
            identity.cloneUrl = cloneUrl;
            return identity;
        }
    }

    private static class CloneTarget {
        private final Path repoDir;
        private final boolean usedShortRoot;

        private CloneTarget(Path repoDir, boolean usedShortRoot) {
            this.repoDir = repoDir;
            this.usedShortRoot = usedShortRoot;
        }
    }

    private static class SessionMetadata {
        public String projectId;
        public String repoUrl;
        public String repoKey;
        public String repoDirName;
        public String repoAbsolutePath;
        public long updatedAtEpochMs;
    }

    private static class ProjectSnapshot {
        public String projectStructure;
        public String focusFilePath;
        public String focusFileContent;
    }

    private static class FilePreview {
        public String content;
        public boolean truncated;
        public boolean binary;
    }

    public static class RepoCloneException extends RuntimeException {
        private final String errorCode;
        private final String detail;
        private final List<String> hints;

        public RepoCloneException(String errorCode, String message, String detail, List<String> hints) {
            super(message);
            this.errorCode = errorCode == null ? "GIT_CLONE_FAILED" : errorCode;
            this.detail = detail == null ? "" : detail;
            this.hints = hints == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(hints));
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getDetail() {
            return detail;
        }

        public List<String> getHints() {
            return hints;
        }
    }
}



