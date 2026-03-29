package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraCaseSourceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class NuteraCheckerService {
    private static final String CHECKER_TRACE_VERSION = "checker-runtime-v2";
    private static final long PROBE_TIMEOUT_SECONDS = 8L;
    private static final int MAX_RAW_OUTPUT = 12000;
    private static final int MAX_SUPPORTED_CLASS_MAJOR = 55; // Java 11

    private final Path projectRoot;
    private final Path runtimeRoot;
    private final String configuredCheckerJava11Home;
    private final String configuredCheckerJavaHome;
    private final String configuredPythonCommand;
    private final long timeoutSeconds;

    public NuteraCheckerService(
            @Value("${app.nutera.runtimePath:runtimes/nutera}") String configuredRuntimePath,
            @Value("${app.nutera.checker.java11-home:C:/Program Files/Eclipse Adoptium/jdk-11.0.28.6-hotspot}") String configuredCheckerJava11Home,
            @Value("${checker.java.home:}") String configuredCheckerJavaHome,
            @Value("${app.nutera.pythonCommand:python3}") String configuredPythonCommand,
            @Value("${app.nutera.timeoutSeconds:600}") long timeoutSeconds
    ) {
        this.projectRoot = resolveProjectRoot();
        this.runtimeRoot = resolveRuntimeRoot(configuredRuntimePath, projectRoot);
        this.configuredCheckerJava11Home = configuredCheckerJava11Home == null ? "" : configuredCheckerJava11Home.trim();
        this.configuredCheckerJavaHome = configuredCheckerJavaHome == null ? "" : configuredCheckerJavaHome.trim();
        this.configuredPythonCommand = configuredPythonCommand == null ? "" : configuredPythonCommand.trim();
        this.timeoutSeconds = Math.max(10L, timeoutSeconds);
    }

    public CheckerResult runChecker(String rankExpression, NuteraCaseSourceResponse caseSource, List<String> logs) {
        CheckerResult internalV2 = runCheckerV2Internal(rankExpression, caseSource, logs);
        return CheckerErrorMapper.toStableResult(internalV2);
    }

    public JavaRuntime resolveCheckerJavaRuntime(List<String> logs) {
        return resolveJavaRuntime(logs);
    }

    private CheckerResult runCheckerV2Internal(String rankExpression, NuteraCaseSourceResponse caseSource, List<String> logs) {
        if (caseSource == null) {
            return CheckerResult.skipped("Checker skipped: case source metadata is unavailable.");
        }

        append(logs, "Checker stage started.");
        append(logs, "CHECKER_TRACE_V2_ENTRY [" + CHECKER_TRACE_VERSION + "]");
        JavaRuntime javaRuntime = resolveJavaRuntime(logs);
        if (!javaRuntime.available) {
            append(logs, "CHECKER_TRACE_V2_RESOLVE_JAVA_FAIL [" + CHECKER_TRACE_VERSION + "]: " + javaRuntime.message);
            if (!trim(javaRuntime.detail).isBlank()) {
                append(logs, "Checker Java runtime detail: " + javaRuntime.detail);
            }
            return CheckerResult.error(javaRuntime.message, javaRuntime.detail);
        }

        append(logs, "CHECKER_TRACE_V2_RESOLVE_JAVA [" + CHECKER_TRACE_VERSION + "]: source=" + javaRuntime.source);
        append(logs, "Configured checker java home (app.nutera.checker.java11-home): "
                + (trim(javaRuntime.configuredJava11Home).isBlank() ? "(empty)" : javaRuntime.configuredJava11Home));
        append(logs, "Configured checker java home (checker.java.home): "
                + (trim(javaRuntime.configuredLegacyJavaHome).isBlank() ? "(empty)" : javaRuntime.configuredLegacyJavaHome));
        append(logs, "JAVA_HOME: " + normalizePath(javaRuntime.javaHome));
        append(logs, "Checker Java executable: " + normalizePath(javaRuntime.javaExecutable));
        append(logs, "Resolved checker java version: "
                + (trim(javaRuntime.javaVersionText).isBlank() ? "(empty)" : clip(javaRuntime.javaVersionText)));
        append(logs, "Detected checker JVM major version: " + javaRuntime.javaMajor);

        Path checkerScript = resolveCheckerScript();
        if (checkerScript == null) {
            return CheckerResult.error(versioned("Checker startup failed: checker script not found."), "");
        }

        Path programJar = resolveProgramJarPath(caseSource);
        if (programJar == null || !Files.exists(programJar)) {
            return CheckerResult.error(versioned("Checker startup failed: program jar path does not exist."),
                    "programBinaryPath=" + trim(caseSource.getProgramBinaryPath()));
        }
        String className = trim(caseSource.getProgramClass());
        String methodName = trim(caseSource.getProgramFunction());

        JarBytecodeInfo bytecodeInfo = inspectJarBytecode(programJar, className, logs);
        if (bytecodeInfo.incompatible()) {
            append(logs, "CHECKER_TRACE_V2_BYTECODE_INCOMPATIBLE [" + CHECKER_TRACE_VERSION + "]: "
                    + "major=" + bytecodeInfo.detectedMajor + ", jar=" + normalizePath(programJar));
            return CheckerResult.error(buildBytecodeCompatibilityMessage(bytecodeInfo.detectedMajor), bytecodeInfo.detail);
        }

        Path javacheckerJar = resolveJavacheckerJar();
        if (javacheckerJar == null) {
            return CheckerResult.error(versioned("Checker startup failed: javachecker-uber.jar not found."),
                    "Checked JAVACHECKER_JAR and runtimes/nutera/deps|libs locations.");
        }

        PythonResolution python = resolvePythonCommand(logs);
        if (!python.available()) {
            return CheckerResult.error(versioned("Checker startup failed: Python interpreter is unavailable."), python.detail);
        }

        List<String> command = new ArrayList<>(python.command);
        command.add(checkerScript.toString());
        command.add("--jar");
        command.add(programJar.toString());
        command.add("--rank");
        command.add(rankExpression == null ? "" : rankExpression);
        command.add("--auto-assume-from-main");
        if (!className.isBlank()) {
            command.add("--class");
            command.add(className);
        }
        if (!methodName.isBlank()) {
            command.add("--method");
            command.add(methodName);
        }

        append(logs, "Checker file: " + normalizePath(checkerScript));
        append(logs, "Checker command: " + formatCommand(command));
        append(logs, "Checker cwd: " + normalizePath(runtimeRoot));
        append(logs, "Program jar: " + normalizePath(programJar));
        append(logs, "Javachecker jar: " + normalizePath(javacheckerJar));
        append(logs, "CHECKER_TRACE_V2_LAUNCH [" + CHECKER_TRACE_VERSION + "]: " + formatCommand(command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(runtimeRoot.toFile());
        pb.redirectErrorStream(false);
        Map<String, String> env = pb.environment();
        env.put("PYTHONIOENCODING", "UTF-8");
        env.put("JAVACHECKER_JAR", javacheckerJar.toString());
        env.putAll(javaRuntime.childEnv);

        Process process;
        try {
            process = pb.start();
        } catch (IOException ex) {
            String message = ex.getMessage() == null ? "Unknown process start error" : ex.getMessage();
            append(logs, "Checker start error: " + message);
            String friendly = isMissingCommandError(message)
                    ? versioned("Checker startup failed: command or dependency not found.")
                    : versioned("Checker startup failed: " + message);
            return CheckerResult.error(friendly, message);
        }

        StreamCollector stdoutCollector = new StreamCollector(process.getInputStream());
        StreamCollector stderrCollector = new StreamCollector(process.getErrorStream());
        new Thread(stdoutCollector, "nutera-checker-stdout").start();
        new Thread(stderrCollector, "nutera-checker-stderr").start();

        boolean finished;
        try {
            finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return CheckerResult.error(versioned("Checker execution interrupted."), ex.getMessage());
        }

        if (!finished) {
            process.destroyForcibly();
            stdoutCollector.await(2);
            stderrCollector.await(2);
            String stdout = trimOutput(stdoutCollector.getText());
            String stderr = trimOutput(stderrCollector.getText());
            append(logs, "Checker timeout after " + timeoutSeconds + " seconds.");
            append(logs, "Checker stdout:\n" + clip(stdout));
            append(logs, "Checker stderr:\n" + clip(stderr));
            return CheckerResult.error(versioned("Checker execution timed out (" + timeoutSeconds + "s)."),
                    buildRawOutput(command, -1, stdout, stderr, javaRuntime));
        }

        stdoutCollector.await(2);
        stderrCollector.await(2);
        int exitCode = process.exitValue();
        String stdout = trimOutput(stdoutCollector.getText());
        String stderr = trimOutput(stderrCollector.getText());
        append(logs, "Checker exit code: " + exitCode);
        append(logs, "Checker stdout:\n" + clip(stdout));
        append(logs, "Checker stderr:\n" + clip(stderr));
        String rawOutput = buildRawOutput(command, exitCode, stdout, stderr, javaRuntime);
        ParsedCheckerResult parsed = parseCheckerResult(stdout, stderr);
        Integer unsupportedMajor = detectUnsupportedClassMajor(stdout, stderr);
        if (unsupportedMajor != null) {
            append(logs, "CHECKER_TRACE_V2_BYTECODE_RUNTIME_INCOMPATIBLE [" + CHECKER_TRACE_VERSION + "]: major=" + unsupportedMajor);
            return CheckerResult.error(buildBytecodeCompatibilityMessage(unsupportedMajor), rawOutput);
        }
        if (parsed.hasResult) {
            if ("YES".equals(parsed.conclusion)) {
                return CheckerResult.completed(
                        "Checker completed: termination was proven.",
                        rawOutput,
                        "YES",
                        parsed.counterexampleJson
                );
            }
            if ("NO".equals(parsed.conclusion)) {
                return CheckerResult.completed(
                        "Checker completed: candidate function could not prove termination.",
                        rawOutput,
                        "NO",
                        parsed.counterexampleJson
                );
            }
        }
        if (exitCode == 9009) {
            return CheckerResult.error(versioned("Checker startup failed: command or dependency not found (exit code 9009)."), rawOutput);
        }
        if (exitCode == 0) {
            return CheckerResult.error(
                    versioned("Checker finished but RESULT block was not found. Expected RESULT -> YES/NO."),
                    rawOutput
            );
        }
        return CheckerResult.error(versioned("Checker returned an error (exit code " + exitCode + ")."), rawOutput);
    }

    private JavaRuntime resolveJavaRuntime(List<String> logs) {
        String configuredJava11Home = sanitizePathToken(configuredCheckerJava11Home);
        String configuredLegacyHome = sanitizePathToken(configuredCheckerJavaHome);
        String envJava11Home = sanitizePathToken(System.getenv("NUTERA_JAVA11_HOME"));
        String nuteraJavaHome = sanitizePathToken(System.getenv("NUTERA_JAVA_HOME"));
        String javaHome = sanitizePathToken(System.getenv("JAVA_HOME"));
        append(logs, "Configured checker java home (app.nutera.checker.java11-home): "
                + (configuredJava11Home.isBlank() ? "(empty)" : configuredJava11Home));
        append(logs, "Configured checker java home (checker.java.home): "
                + (configuredLegacyHome.isBlank() ? "(empty)" : configuredLegacyHome));
        append(logs, "Env NUTERA_JAVA11_HOME: " + (envJava11Home.isBlank() ? "(empty)" : envJava11Home));
        append(logs, "Env NUTERA_JAVA_HOME: " + (nuteraJavaHome.isBlank() ? "(empty)" : nuteraJavaHome));
        append(logs, "Env JAVA_HOME: " + (javaHome.isBlank() ? "(empty)" : javaHome));

        List<String> failures = new ArrayList<>();
        String projectConfiguredHome = firstNonBlank(configuredJava11Home, configuredLegacyHome);

        JavaRuntime resolved = resolveConfiguredJava(
                trim(configuredJava11Home).isBlank() ? "checker.java.home" : "app.nutera.checker.java11-home",
                projectConfiguredHome,
                failures
        );
        if (resolved.available) {
            return validateCheckerJavaVersion(resolved, logs, configuredJava11Home, configuredLegacyHome);
        }

        resolved = resolveConfiguredJava("NUTERA_JAVA11_HOME", envJava11Home, failures);
        if (resolved.available) {
            return validateCheckerJavaVersion(resolved, logs, configuredJava11Home, configuredLegacyHome);
        }

        resolved = resolveConfiguredJava("NUTERA_JAVA_HOME", nuteraJavaHome, failures);
        if (resolved.available) {
            return validateCheckerJavaVersion(resolved, logs, configuredJava11Home, configuredLegacyHome);
        }

        resolved = resolveConfiguredJava("JAVA_HOME", javaHome, failures);
        if (resolved.available) {
            return validateCheckerJavaVersion(resolved, logs, configuredJava11Home, configuredLegacyHome);
        }

        String pathJava = sanitizePathToken(resolveExecutableOnPath("java"));
        append(logs, "PATH java probe: " + (pathJava.isBlank() ? "(not found)" : pathJava));
        resolved = resolveJavaFromPath(pathJava, failures);
        if (resolved.available) {
            return validateCheckerJavaVersion(resolved, logs, configuredJava11Home, configuredLegacyHome);
        }

        String detail = failures.isEmpty()
                ? "Missing checker java configuration and java was not found on PATH."
                : String.join("\n", failures);
        return JavaRuntime.error(
                versioned("Java runtime is not configured for checker. Resolution order: "
                        + "project java11 config > NUTERA_JAVA11_HOME > NUTERA_JAVA_HOME > JAVA_HOME > PATH(java)."),
                detail
        );
    }

    private JavaRuntime validateCheckerJavaVersion(JavaRuntime runtime,
                                                   List<String> logs,
                                                   String configuredJava11Home,
                                                   String configuredLegacyHome) {
        ProbeResult javaVersionProbe = runProbe(
                List.of(runtime.javaExecutable.toString(), "-version"),
                runtimeRoot,
                PROBE_TIMEOUT_SECONDS,
                runtime.childEnv
        );
        String javaVersionText = trim(javaVersionProbe.output);
        Integer major = parseJavaMajor(javaVersionText);
        if (major == null) {
            return JavaRuntime.error(
                    versioned("Failed to detect Java version for checker runtime."),
                    "source=" + runtime.source + "\n"
                            + "javaExecutable=" + normalizePath(runtime.javaExecutable) + "\n"
                            + "java -version output:\n" + javaVersionText
            );
        }
        if (!Objects.equals(major, 11)) {
            return JavaRuntime.error(
                    versioned("Checker requires Java 11, but resolved Java is " + major + ": " + normalizePath(runtime.javaExecutable)),
                    "source=" + runtime.source + "\n"
                            + "configuredJava11Home=" + configuredJava11Home + "\n"
                            + "configuredLegacyJavaHome=" + configuredLegacyHome + "\n"
                            + "javaHome=" + normalizePath(runtime.javaHome) + "\n"
                            + "java -version output:\n" + javaVersionText
            );
        }
        append(logs, "Checker Java runtime validated for Java 11. source=" + runtime.source);
        return JavaRuntime.ok(
                runtime.javaHome,
                runtime.javaExecutable,
                runtime.childEnv,
                runtime.source,
                major,
                javaVersionText,
                configuredJava11Home,
                configuredLegacyHome
        );
    }

    private JarBytecodeInfo inspectJarBytecode(Path jarPath, String preferredClassName, List<String> logs) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return JarBytecodeInfo.unknown("Jar not found: " + normalizePath(jarPath));
        }
        String preferredEntry = trim(preferredClassName).isBlank()
                ? ""
                : trim(preferredClassName).replace('.', '/') + ".class";
        int maxMajor = 0;
        int preferredMajor = 0;
        int classCount = 0;
        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            for (ZipEntry entry : java.util.Collections.list(zip.entries())) {
                if (entry == null || entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                classCount++;
                int major;
                try (InputStream in = zip.getInputStream(entry)) {
                    major = readClassMajor(in);
                }
                if (major <= 0) {
                    continue;
                }
                if (major > maxMajor) {
                    maxMajor = major;
                }
                if (!preferredEntry.isBlank() && preferredEntry.equals(entry.getName())) {
                    preferredMajor = major;
                }
            }
        } catch (Exception ex) {
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            String detail = "Failed to inspect jar bytecode version.\njar=" + normalizePath(jarPath) + "\nreason=" + reason;
            append(logs, "Jar bytecode preflight failed: " + reason);
            return JarBytecodeInfo.unknown(detail);
        }
        int detectedMajor = preferredMajor > 0 ? preferredMajor : maxMajor;
        String detail = "jar=" + normalizePath(jarPath)
                + "\npreferredClassEntry=" + (preferredEntry.isBlank() ? "(none)" : preferredEntry)
                + "\nclassCount=" + classCount
                + "\nmaxClassMajor=" + maxMajor
                + "\ndetectedClassMajor=" + detectedMajor;
        append(logs, "Jar bytecode preflight: detectedMajor=" + detectedMajor
                + ", maxMajor=" + maxMajor
                + ", classCount=" + classCount
                + ", preferredClassEntry=" + (preferredEntry.isBlank() ? "(none)" : preferredEntry));
        return new JarBytecodeInfo(detectedMajor, maxMajor, classCount, preferredEntry, detail);
    }

    private int readClassMajor(InputStream in) throws IOException {
        byte[] header = in.readNBytes(8);
        if (header.length < 8) {
            return 0;
        }
        if ((header[0] & 0xFF) != 0xCA
                || (header[1] & 0xFF) != 0xFE
                || (header[2] & 0xFF) != 0xBA
                || (header[3] & 0xFF) != 0xBE) {
            return 0;
        }
        return ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
    }

    private Integer detectUnsupportedClassMajor(String stdout, String stderr) {
        String combined = (stdout == null ? "" : stdout) + "\n" + (stderr == null ? "" : stderr);
        Matcher m = Pattern.compile("Unsupported class file major version\\s+([0-9]+)", Pattern.CASE_INSENSITIVE).matcher(combined);
        if (m.find()) {
            return parseInt(m.group(1));
        }
        return null;
    }

    private String buildBytecodeCompatibilityMessage(int classMajor) {
        String javaVersion = mapClassMajorToJavaVersion(classMajor);
        return versioned("Checker bytecode compatibility error: target program uses class major version "
                + classMajor + " (Java " + javaVersion + "), but current checker supports up to major "
                + MAX_SUPPORTED_CLASS_MAJOR + " (Java 11). Rebuild target jars with --release 11 or upgrade javachecker/ASM.");
    }

    private String mapClassMajorToJavaVersion(int major) {
        if (major == 45) return "1.1";
        if (major == 46) return "1.2";
        if (major == 47) return "1.3";
        if (major == 48) return "1.4";
        if (major >= 49) return String.valueOf(major - 44);
        return "unknown";
    }

    private JavaRuntime resolveConfiguredJava(String sourceName, String value, List<String> failures) {
        if (value == null || value.isBlank()) {
            failures.add(sourceName + " is empty.");
            return JavaRuntime.error("", "");
        }

        Path configuredPath;
        try {
            configuredPath = Path.of(value).toAbsolutePath().normalize();
        } catch (Exception ex) {
            failures.add(sourceName + " is invalid: " + value);
            return JavaRuntime.error("", "");
        }

        if (Files.isRegularFile(configuredPath)) {
            if (!looksLikeJavaExecutable(configuredPath)) {
                failures.add(sourceName + " points to a file but not java executable: " + configuredPath);
                return JavaRuntime.error("", "");
            }
            Path inferredHome = inferJavaHomeFromExecutable(configuredPath);
            Path javaExe = resolveJavaExecutableFromHome(inferredHome);
            if (javaExe == null) {
                failures.add(sourceName + " points to java executable, but inferred JAVA_HOME is invalid: " + inferredHome);
                return JavaRuntime.error("", "");
            }
            return buildJavaRuntime(inferredHome, javaExe, sourceName);
        }

        if (!Files.isDirectory(configuredPath)) {
            failures.add(sourceName + " path does not exist: " + configuredPath);
            return JavaRuntime.error("", "");
        }

        Path homeCandidate = configuredPath;
        String fileName = trim(configuredPath.getFileName() == null ? "" : configuredPath.getFileName().toString());
        if ("bin".equalsIgnoreCase(fileName)) {
            Path parent = configuredPath.getParent();
            if (parent != null) {
                homeCandidate = parent.toAbsolutePath().normalize();
            }
        }

        Path javaExe = resolveJavaExecutableFromHome(homeCandidate);
        if (javaExe == null) {
            Path fromBinDir = resolveJavaExecutableDirectly(configuredPath);
            if (fromBinDir != null) {
                Path inferredHome = inferJavaHomeFromExecutable(fromBinDir);
                Path inferredExe = resolveJavaExecutableFromHome(inferredHome);
                if (inferredExe != null) {
                    return buildJavaRuntime(inferredHome, inferredExe, sourceName);
                }
            }
            failures.add(sourceName + " does not contain usable java under bin: " + configuredPath);
            return JavaRuntime.error("", "");
        }

        return buildJavaRuntime(homeCandidate, javaExe, sourceName);
    }

    private JavaRuntime resolveJavaFromPath(String javaExecutablePath, List<String> failures) {
        if (javaExecutablePath == null || javaExecutablePath.isBlank()) {
            failures.add("PATH does not provide a java executable.");
            return JavaRuntime.error("", "");
        }
        Path javaExe;
        try {
            javaExe = Path.of(javaExecutablePath).toAbsolutePath().normalize();
        } catch (Exception ex) {
            failures.add("PATH java executable path is invalid: " + javaExecutablePath);
            return JavaRuntime.error("", "");
        }
        if (!Files.isRegularFile(javaExe)) {
            failures.add("PATH java executable not found: " + javaExe);
            return JavaRuntime.error("", "");
        }

        Path home = inferJavaHomeFromExecutable(javaExe);
        Path validatedExe = resolveJavaExecutableFromHome(home);
        if (validatedExe == null) {
            failures.add("PATH java executable was found, but inferred JAVA_HOME is unusable: " + home);
            return JavaRuntime.error("", "");
        }
        return buildJavaRuntime(home, validatedExe, "PATH(java)");
    }

    private JavaRuntime buildJavaRuntime(Path javaHome, Path javaExecutable, String source) {
        if (javaHome == null || javaExecutable == null) {
            return JavaRuntime.error("", "");
        }
        Path normalizedHome = javaHome.toAbsolutePath().normalize();
        Path normalizedExe = javaExecutable.toAbsolutePath().normalize();
        String childPath = buildPathWithJavaFirst(normalizedExe.getParent(), System.getenv("PATH"));
        Map<String, String> childEnv = new LinkedHashMap<>();
        childEnv.put("NUTERA_JAVA11_HOME", normalizedHome.toString());
        childEnv.put("NUTERA_JAVA_HOME", normalizedHome.toString());
        childEnv.put("JAVA_HOME", normalizedHome.toString());
        childEnv.put("JRE_HOME", normalizedHome.toString());
        childEnv.put("PATH", childPath);
        return JavaRuntime.ok(normalizedHome, normalizedExe, childEnv, source, 0, "", configuredCheckerJava11Home, configuredCheckerJavaHome);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String text = trim(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private Path resolveJavaExecutableFromHome(Path javaHome) {
        if (javaHome == null || !Files.isDirectory(javaHome)) {
            return null;
        }
        Path javaExe = javaHome.resolve("bin").resolve(isWindows() ? "java.exe" : "java").normalize();
        if (Files.isRegularFile(javaExe)) {
            return javaExe;
        }
        return null;
    }

    private Path resolveJavaExecutableDirectly(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return null;
        }
        Path javaExe = directory.resolve(isWindows() ? "java.exe" : "java").normalize();
        if (Files.isRegularFile(javaExe)) {
            return javaExe;
        }
        return null;
    }

    private Path inferJavaHomeFromExecutable(Path javaExecutable) {
        Path exe = javaExecutable == null ? null : javaExecutable.toAbsolutePath().normalize();
        if (exe == null) {
            return null;
        }
        Path parent = exe.getParent();
        if (parent == null) {
            return exe;
        }
        String parentName = trim(parent.getFileName() == null ? "" : parent.getFileName().toString());
        if ("bin".equalsIgnoreCase(parentName) && parent.getParent() != null) {
            return parent.getParent().toAbsolutePath().normalize();
        }
        return parent.toAbsolutePath().normalize();
    }

    private boolean looksLikeJavaExecutable(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String fileName = trim(path.getFileName() == null ? "" : path.getFileName().toString()).toLowerCase(Locale.ROOT);
        if (isWindows()) {
            return "java.exe".equals(fileName);
        }
        return "java".equals(fileName);
    }

    private String sanitizePathToken(String value) {
        String text = trim(value);
        if (text.length() >= 2) {
            if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
                return text.substring(1, text.length() - 1).trim();
            }
        }
        return text;
    }

    private String buildPathWithJavaFirst(Path javaBin, String originalPath) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(javaBin.toString());
        if (originalPath != null && !originalPath.isBlank()) {
            String[] parts = originalPath.split(isWindows() ? ";" : ":");
            for (String part : parts) {
                String p = trim(part);
                if (!p.isBlank() && !shouldDropPathEntry(p)) {
                    ordered.add(p);
                }
            }
        }
        return String.join(isWindows() ? ";" : ":", ordered);
    }

    private boolean shouldDropPathEntry(String entry) {
        String lower = entry.toLowerCase(Locale.ROOT).replace("/", "\\");
        return lower.contains("oracle\\java\\javapath")
                || lower.contains("\\javapath")
                || lower.contains("jre1.8")
                || lower.contains("jdk1.8")
                || lower.contains("\\java\\8")
                || lower.contains("\\java\\jre8");
    }

    private Integer parseJavaMajor(String output) {
        if (output == null || output.isBlank()) return null;
        Matcher quoted = Pattern.compile("version\\s+\\\"([^\\\"]+)\\\"").matcher(output);
        if (quoted.find()) return parseJavaToken(quoted.group(1));
        Matcher plain = Pattern.compile("openjdk\\s+([0-9]+)", Pattern.CASE_INSENSITIVE).matcher(output);
        if (plain.find()) return parseInt(plain.group(1));
        return null;
    }

    private Integer parseJavaToken(String token) {
        if (token.startsWith("1.")) {
            String[] parts = token.split("\\.");
            return parts.length > 1 ? parseInt(parts[1]) : null;
        }
        Matcher m = Pattern.compile("^([0-9]+)").matcher(token);
        return m.find() ? parseInt(m.group(1)) : null;
    }

    private Integer parseInt(String value) {
        try { return Integer.parseInt(value); } catch (Exception ex) { return null; }
    }

    private String buildRawOutput(List<String> command, int exitCode, String stdout, String stderr, JavaRuntime javaRuntime) {
        StringJoiner j = new StringJoiner("\n");
        j.add("trace: CHECKER_TRACE_V2_RAW [" + CHECKER_TRACE_VERSION + "]");
        j.add("command: " + formatCommand(command));
        j.add("cwd: " + normalizePath(runtimeRoot));
        j.add("javaSource: " + trim(javaRuntime.source));
        j.add("javaHome: " + normalizePath(javaRuntime.javaHome));
        j.add("javaExecutable: " + normalizePath(javaRuntime.javaExecutable));
        j.add("exitCode: " + exitCode);
        j.add("stdout:");
        j.add(stdout.isBlank() ? "(empty)" : stdout);
        j.add("stderr:");
        j.add(stderr.isBlank() ? "(empty)" : stderr);
        return j.toString();
    }

    private String versioned(String message) {
        return "[" + CHECKER_TRACE_VERSION + "] " + (message == null ? "" : message);
    }

    private Path resolveCheckerScript() {
        Path p1 = runtimeRoot.resolve("checker").resolve("rf_check.py").normalize();
        if (Files.isRegularFile(p1)) return p1;
        Path p2 = runtimeRoot.resolve("benchmarking").resolve("rf_check.py").normalize();
        if (Files.isRegularFile(p2)) return p2;
        return null;
    }

    private Path resolveProgramJarPath(NuteraCaseSourceResponse caseSource) {
        String displayPath = trim(caseSource.getProgramBinaryPath());
        if (displayPath.isBlank()) return null;
        Path projectResolved = projectRoot.resolve(displayPath).normalize();
        if (Files.exists(projectResolved)) return projectResolved;
        try {
            Path absolute = Path.of(displayPath);
            if (absolute.isAbsolute() && Files.exists(absolute)) return absolute.normalize();
        } catch (Exception ignored) {
        }
        return projectResolved;
    }

    private Path resolveJavacheckerJar() {
        String envJar = trim(System.getenv("JAVACHECKER_JAR"));
        if (!envJar.isBlank()) {
            try {
                Path envPath = Path.of(envJar).normalize();
                if (Files.isRegularFile(envPath)) return envPath;
            } catch (Exception ignored) {
            }
        }
        Path depsJar = runtimeRoot.resolve("deps").resolve("javachecker").resolve("build").resolve("libs").resolve("javachecker-uber.jar").normalize();
        if (Files.isRegularFile(depsJar)) return depsJar;
        Path libsJar = runtimeRoot.resolve("libs").resolve("javachecker-uber.jar").normalize();
        if (Files.isRegularFile(libsJar)) return libsJar;
        return null;
    }

    private PythonResolution resolvePythonCommand(List<String> logs) {
        List<List<String>> candidates = new ArrayList<>();
        if (!configuredPythonCommand.isBlank()) {
            List<String> configured = parseCommandTokens(configuredPythonCommand);
            if (!configured.isEmpty()) candidates.add(configured);
        }
        if (isWindows()) {
            candidates.add(List.of("py", "-3"));
            candidates.add(List.of("python"));
            candidates.add(List.of("python3"));
        } else {
            candidates.add(List.of("python3"));
            candidates.add(List.of("python"));
        }
        List<List<String>> unique = deduplicate(candidates);
        StringBuilder detail = new StringBuilder();
        for (List<String> base : unique) {
            List<String> probe = new ArrayList<>(base);
            probe.add("--version");
            ProbeResult result = runProbe(probe, runtimeRoot, PROBE_TIMEOUT_SECONDS, null);
            detail.append("[").append(formatCommand(probe)).append("] ").append(result.message).append(System.lineSeparator());
            if (result.success) {
                List<String> resolved = absolutizeCommand(base);
                append(logs, "Python command (resolved): " + formatCommand(resolved));
                return new PythonResolution(resolved, detail.toString().trim());
            }
        }
        return new PythonResolution(List.of(), detail.toString().trim());
    }

    private ProbeResult runProbe(List<String> command, Path cwd, long timeoutSec, Map<String, String> envOverrides) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        if (envOverrides != null && !envOverrides.isEmpty()) pb.environment().putAll(envOverrides);
        try {
            Process process = pb.start();
            StreamCollector collector = new StreamCollector(process.getInputStream());
            new Thread(collector, "nutera-probe").start();
            boolean finished = process.waitFor(timeoutSec, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                collector.await(1);
                return new ProbeResult(false, "timeout", collector.getText());
            }
            collector.await(1);
            int exit = process.exitValue();
            return exit == 0
                    ? new ProbeResult(true, "ok", collector.getText())
                    : new ProbeResult(false, "exit code " + exit, collector.getText());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new ProbeResult(false, message, "");
        }
    }

    private ParsedCheckerResult parseCheckerResult(String stdout, String stderr) {
        String out = trim(stdout);
        String err = trim(stderr);

        String conclusion = parseConclusion(out);
        String source = out;

        // Primary source is stdout RESULT block; fallback to stderr only for compatibility.
        if (conclusion.isBlank()) {
            conclusion = parseConclusion(err);
            source = err;
        }

        String counterexample = "NO".equals(conclusion) ? extractCounterexampleJson(source) : "";
        return new ParsedCheckerResult(!conclusion.isBlank(), conclusion, counterexample);
    }

    private String parseConclusion(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = trim(lines[i]);
            if (!"RESULT".equals(line)) continue;
            for (int j = i + 1; j < lines.length && j <= i + 12; j++) {
                String verdict = trim(lines[j]).toUpperCase(Locale.ROOT);
                if ("YES".equals(verdict) || "NO".equals(verdict)) {
                    return verdict;
                }
            }
        }
        return "";
    }

    private String extractCounterexampleJson(String text) {
        if (text == null || text.isBlank()) return "";
        int marker = text.indexOf("Counterexample:");
        if (marker < 0) return "";
        int start = text.indexOf('{', marker);
        if (start < 0) return "";

        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '{') depth++;
            if (ch == '}') depth--;
            if (depth == 0) {
                return text.substring(start, i + 1).trim();
            }
        }
        return "";
    }

    private boolean isMissingCommandError(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return text.contains("createprocess error=2")
                || text.contains("cannot run program")
                || text.contains("not found")
                || text.contains("file not found")
                || text.contains("cannot find the file");
    }

    private List<String> absolutizeCommand(List<String> command) {
        if (command == null || command.isEmpty()) return List.of();
        List<String> resolved = new ArrayList<>(command);
        String absolute = resolveExecutableOnPath(command.get(0));
        if (absolute != null && !absolute.isBlank()) resolved.set(0, absolute);
        return resolved;
    }

    private String resolveExecutableOnPath(String executable) {
        String cmd = trim(executable);
        if (cmd.isBlank()) return null;
        try {
            Path direct = Path.of(cmd);
            if ((direct.isAbsolute() || cmd.contains("/") || cmd.contains("\\")) && Files.exists(direct)) {
                return direct.toAbsolutePath().normalize().toString();
            }
        } catch (Exception ignored) {
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) return null;
        boolean windows = isWindows();
        List<String> suffixes = new ArrayList<>();
        if (windows) {
            String pathext = System.getenv("PATHEXT");
            if (pathext != null && !pathext.isBlank()) {
                for (String ext : pathext.split(";")) {
                    String v = trim(ext).toLowerCase(Locale.ROOT);
                    if (!v.isBlank()) suffixes.add(v);
                }
            }
            if (suffixes.isEmpty()) {
                suffixes.add(".exe");
                suffixes.add(".cmd");
                suffixes.add(".bat");
            }
        } else {
            suffixes.add("");
        }
        String lowerCmd = cmd.toLowerCase(Locale.ROOT);
        String[] dirs = pathEnv.split(windows ? ";" : ":");
        for (String dir : dirs) {
            String baseDir = trim(dir);
            if (baseDir.isBlank()) continue;
            Path directory = Path.of(baseDir);
            if (!Files.isDirectory(directory)) continue;
            if (windows && (lowerCmd.endsWith(".exe") || lowerCmd.endsWith(".cmd") || lowerCmd.endsWith(".bat"))) {
                Path candidate = directory.resolve(cmd);
                if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize().toString();
            } else {
                for (String suffix : suffixes) {
                    Path candidate = directory.resolve(cmd + suffix);
                    if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath().normalize().toString();
                }
            }
        }
        return null;
    }

    private String formatCommand(List<String> command) {
        if (command == null || command.isEmpty()) return "(empty)";
        StringJoiner joiner = new StringJoiner(" ");
        for (String part : command) {
            if (part == null) continue;
            String token = part.contains(" ") ? "\"" + part + "\"" : part;
            joiner.add(token);
        }
        return joiner.toString();
    }

    private String normalizePath(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    private String trimOutput(String value) {
        String text = trim(value);
        if (text.length() > MAX_RAW_OUTPUT) return text.substring(0, MAX_RAW_OUTPUT) + "\n...(truncated)";
        return text;
    }

    private String clip(String text) {
        String value = trim(text);
        if (value.isBlank()) return "(empty)";
        if (value.length() <= 1200) return value;
        return value.substring(0, 1200) + "\n...(truncated)";
    }

    private List<String> parseCommandTokens(String commandLine) {
        List<String> tokens = new ArrayList<>();
        if (commandLine == null || commandLine.isBlank()) return tokens;
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (Character.isWhitespace(ch) && !inSingle && !inDouble) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private List<List<String>> deduplicate(List<List<String>> input) {
        List<List<String>> unique = new ArrayList<>();
        for (List<String> item : input) {
            if (item == null || item.isEmpty()) continue;
            boolean exists = false;
            for (List<String> old : unique) {
                if (old.equals(item)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) unique.add(item);
        }
        return unique;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private void append(List<String> logs, String message) {
        if (logs == null) {
            return;
        }
        logs.add("[" + Instant.now() + "] " + message);
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private Path resolveProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(current.resolve("runtimes"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("runtimes"))) return parent;
        return current;
    }

    private Path resolveRuntimeRoot(String configuredRuntimePath, Path baseRoot) {
        Path configured = Path.of(configuredRuntimePath);
        if (configured.isAbsolute()) return configured.normalize();
        Path direct = baseRoot.resolve(configured).normalize();
        if (Files.exists(direct)) return direct;
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path fallback = current.resolve(configured).normalize();
        if (Files.exists(fallback)) return fallback;
        return direct;
    }

    public static final class CheckerResult {
        private final String status;
        private final String message;
        private final String rawOutput;
        private final String verdict;
        private final String conclusion;
        private final String counterexample;
        private final String debugMessage;
        private final String traceTag;

        private CheckerResult(String status, String message, String rawOutput, String verdict, String conclusion, String counterexample,
                              String debugMessage, String traceTag) {
            this.status = status;
            this.message = message == null ? "" : message;
            this.rawOutput = rawOutput == null ? "" : rawOutput;
            this.verdict = verdict == null ? "" : verdict;
            this.conclusion = conclusion == null ? "" : conclusion;
            this.counterexample = counterexample == null ? "" : counterexample;
            this.debugMessage = debugMessage == null ? "" : debugMessage;
            this.traceTag = traceTag == null ? "" : traceTag;
        }

        public static CheckerResult completed(String message, String rawOutput, String conclusion, String counterexample) {
            String verdict = "";
            if ("YES".equalsIgnoreCase(trimSafe(conclusion))) verdict = "PROVED";
            if ("NO".equalsIgnoreCase(trimSafe(conclusion))) verdict = "NOT_PROVED";
            return new CheckerResult("COMPLETED", message, rawOutput, verdict, conclusion, counterexample, "", "");
        }

        public static CheckerResult skipped(String message) {
            return new CheckerResult("SKIPPED", message, "", "", "", "", "", "");
        }

        public static CheckerResult error(String message, String rawOutput) {
            return new CheckerResult("CHECKER_ERROR", message, rawOutput, "", "", "", "", CHECKER_TRACE_VERSION);
        }

        public CheckerResult withExternalMessage(String userMessage, String debugMessage, String traceTag) {
            return new CheckerResult(
                    this.status,
                    userMessage,
                    this.rawOutput,
                    this.verdict,
                    this.conclusion,
                    this.counterexample,
                    debugMessage,
                    traceTag
            );
        }

        public String getStatus() { return status; }
        public String getMessage() { return message; }
        public String getRawOutput() { return rawOutput; }
        public String getVerdict() { return verdict; }
        public String getConclusion() { return conclusion; }
        public String getCounterexample() { return counterexample; }
        public String getDebugMessage() { return debugMessage; }
        public String getTraceTag() { return traceTag; }

        private static String trimSafe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private static final class CheckerErrorMapper {
        private static final Pattern LEADING_TRACE_TAG = Pattern.compile("^\\s*(?:\\[checker-runtime-v\\d+\\]\\s*)+", Pattern.CASE_INSENSITIVE);

        private CheckerErrorMapper() {
        }

        private static CheckerResult toStableResult(CheckerResult internal) {
            if (internal == null) {
                return CheckerResult.error("Checker failed unexpectedly.", "");
            }
            String rawMessage = internal.getMessage() == null ? "" : internal.getMessage();
            String userMessage = sanitizeUserMessage(rawMessage);
            if (!"CHECKER_ERROR".equalsIgnoreCase(internal.getStatus())) {
                return internal.withExternalMessage(userMessage, internal.getDebugMessage(), internal.getTraceTag());
            }
            if (userMessage.isBlank()) {
                userMessage = "Checker execution failed.";
            }
            String debugMessage = rawMessage;
            String traceTag = trimSafe(internal.getTraceTag()).isBlank() ? CHECKER_TRACE_VERSION : internal.getTraceTag();
            return internal.withExternalMessage(userMessage, debugMessage, traceTag);
        }

        private static String sanitizeUserMessage(String message) {
            if (message == null || message.isBlank()) {
                return "";
            }
            return LEADING_TRACE_TAG.matcher(message).replaceFirst("").trim();
        }

        private static String trimSafe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private static final class ParsedCheckerResult {
        private final boolean hasResult;
        private final String conclusion;
        private final String counterexampleJson;

        private ParsedCheckerResult(boolean hasResult, String conclusion, String counterexampleJson) {
            this.hasResult = hasResult;
            this.conclusion = conclusion == null ? "" : conclusion;
            this.counterexampleJson = counterexampleJson == null ? "" : counterexampleJson;
        }
    }

    public static final class JavaRuntime {
        private final boolean available;
        private final String message;
        private final String detail;
        private final String source;
        private final Path javaHome;
        private final Path javaExecutable;
        private final Map<String, String> childEnv;
        private final int javaMajor;
        private final String javaVersionText;
        private final String configuredJava11Home;
        private final String configuredLegacyJavaHome;

        private JavaRuntime(boolean available, String message, String detail, String source, Path javaHome, Path javaExecutable,
                            Map<String, String> childEnv, int javaMajor, String javaVersionText,
                            String configuredJava11Home, String configuredLegacyJavaHome) {
            this.available = available;
            this.message = message;
            this.detail = detail;
            this.source = source == null ? "" : source;
            this.javaHome = javaHome;
            this.javaExecutable = javaExecutable;
            this.childEnv = childEnv == null ? Map.of() : Map.copyOf(childEnv);
            this.javaMajor = javaMajor;
            this.javaVersionText = javaVersionText == null ? "" : javaVersionText;
            this.configuredJava11Home = configuredJava11Home == null ? "" : configuredJava11Home;
            this.configuredLegacyJavaHome = configuredLegacyJavaHome == null ? "" : configuredLegacyJavaHome;
        }

        private static JavaRuntime ok(Path javaHome, Path javaExecutable, Map<String, String> childEnv, String source,
                                      int javaMajor, String javaVersionText,
                                      String configuredJava11Home, String configuredLegacyJavaHome) {
            return new JavaRuntime(true, "", "", source, javaHome, javaExecutable, childEnv, javaMajor, javaVersionText,
                    configuredJava11Home, configuredLegacyJavaHome);
        }

        private static JavaRuntime error(String message, String detail) {
            return new JavaRuntime(false, message, detail, "", null, null, Map.of(), 0, "", "", "");
        }

        public boolean isAvailable() {
            return available;
        }

        public String getMessage() {
            return message;
        }

        public String getDetail() {
            return detail;
        }

        public String getSource() {
            return source;
        }

        public Path getJavaHome() {
            return javaHome;
        }

        public Path getJavaExecutable() {
            return javaExecutable;
        }

        public Map<String, String> getChildEnv() {
            return childEnv;
        }

        public int getJavaMajor() {
            return javaMajor;
        }

        public String getJavaVersionText() {
            return javaVersionText;
        }
    }

    private static final class JarBytecodeInfo {
        private final int detectedMajor;
        private final int maxMajor;
        private final int classCount;
        private final String preferredClassEntry;
        private final String detail;

        private JarBytecodeInfo(int detectedMajor, int maxMajor, int classCount, String preferredClassEntry, String detail) {
            this.detectedMajor = detectedMajor;
            this.maxMajor = maxMajor;
            this.classCount = classCount;
            this.preferredClassEntry = preferredClassEntry == null ? "" : preferredClassEntry;
            this.detail = detail == null ? "" : detail;
        }

        private static JarBytecodeInfo unknown(String detail) {
            return new JarBytecodeInfo(0, 0, 0, "", detail);
        }

        private boolean incompatible() {
            return detectedMajor > MAX_SUPPORTED_CLASS_MAJOR;
        }
    }

    private static final class StreamCollector implements Runnable {
        private final InputStream inputStream;
        private final StringBuilder output = new StringBuilder();
        private final CountDownLatch done = new CountDownLatch(1);

        private StreamCollector(InputStream inputStream) {
            this.inputStream = Objects.requireNonNull(inputStream);
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            } catch (Exception ex) {
                output.append("[stream-read-error] ").append(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            } finally {
                done.countDown();
            }
        }

        private void await(long seconds) {
            try {
                done.await(seconds, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        private String getText() { return output.toString(); }
    }

    private static final class ProbeResult {
        private final boolean success;
        private final String message;
        private final String output;

        private ProbeResult(boolean success, String message, String output) {
            this.success = success;
            this.message = message == null ? "" : message;
            this.output = output == null ? "" : output;
        }
    }

    private static final class PythonResolution {
        private final List<String> command;
        private final String detail;

        private PythonResolution(List<String> command, String detail) {
            this.command = command == null ? List.of() : new ArrayList<>(command);
            this.detail = detail == null ? "" : detail;
        }

        private boolean available() { return command != null && !command.isEmpty(); }
    }
}
