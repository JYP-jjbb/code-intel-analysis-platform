package com.course.ideology.adapter;

import com.course.ideology.service.NuteraCheckerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
public class NuteraAdapter {
    public enum Mode {
        MOCK,
        LOCAL,
        DOCKER
    }

    private final Path runtimePath;
    private final Mode mode;
    private final String pythonCommand;
    private final String dockerImage;
    private final long timeoutSeconds;
    private final NuteraCheckerService checkerService;

    public NuteraAdapter(
            @Value("${app.nutera.runtimePath:runtimes/nutera}") String runtimePath,
            @Value("${app.nutera.mode:MOCK}") String mode,
            @Value("${app.nutera.pythonCommand:python3}") String pythonCommand,
            @Value("${app.nutera.dockerImage:nutera}") String dockerImage,
            @Value("${app.nutera.timeoutSeconds:600}") long timeoutSeconds,
            NuteraCheckerService checkerService
    ) {
        this.runtimePath = resolveRuntimePath(runtimePath);
        Mode resolved;
        try {
            resolved = Mode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            resolved = Mode.MOCK;
        }
        this.mode = resolved;
        this.pythonCommand = pythonCommand;
        this.dockerImage = dockerImage;
        this.timeoutSeconds = timeoutSeconds;
        this.checkerService = checkerService;
    }

    public NuteraRunResult run(NuteraRunRequest request, Consumer<String> logSink) {
        logSink.accept("[" + Instant.now() + "] NuTera adapter mode: " + mode);
        logSink.accept("[" + Instant.now() + "] Runtime path: " + runtimePath);

        if (!Files.exists(runtimePath)) {
            return new NuteraRunResult(false, "", "", "", "", "Runtime path not found: " + runtimePath);
        }

        switch (mode) {
            case LOCAL:
                return runLocal(request, logSink);
            case DOCKER:
                return runDocker(request, logSink);
            case MOCK:
            default:
                return readResults(request, logSink, "MOCK mode: reading existing results");
        }
    }

    private NuteraRunResult runLocal(NuteraRunRequest request, Consumer<String> logSink) {
        List<String> javaLogs = new ArrayList<>();
        NuteraCheckerService.JavaRuntime checkerJavaRuntime = checkerService.resolveCheckerJavaRuntime(javaLogs);
        for (String line : javaLogs) {
            logSink.accept(line);
        }
        if (!checkerJavaRuntime.isAvailable()) {
            return new NuteraRunResult(
                    false,
                    "",
                    "",
                    "",
                    "",
                    checkerJavaRuntime.getMessage().isBlank()
                            ? "Checker Java runtime resolution failed."
                            : checkerJavaRuntime.getMessage()
            );
        }
        logSink.accept("[" + Instant.now() + "] Checker Java source: " + checkerJavaRuntime.getSource());
        logSink.accept("[" + Instant.now() + "] Checker Java executable: " + checkerJavaRuntime.getJavaExecutable());
        logSink.accept("[" + Instant.now() + "] Checker Java major version: " + checkerJavaRuntime.getJavaMajor());

        List<String> command = buildLocalCommand(request);
        logSink.accept("[" + Instant.now() + "] Local command: " + String.join(" ", command));
        int exit = execute(command, runtimePath, logSink, checkerJavaRuntime.getChildEnv());
        if (exit != 0) {
            return new NuteraRunResult(false, "", "", "", "", "NuTera process failed with exit code " + exit);
        }
        return readResults(request, logSink, "Local run finished");
    }

    private NuteraRunResult runDocker(NuteraRunRequest request, Consumer<String> logSink) {
        List<String> command = buildDockerCommand(request);
        logSink.accept("[" + Instant.now() + "] Docker command: " + String.join(" ", command));
        int exit = execute(command, runtimePath, logSink, Map.of());
        if (exit != 0) {
            return new NuteraRunResult(false, "", "", "", "", "Docker run failed with exit code " + exit);
        }
        return readResults(request, logSink, "Docker run finished");
    }

    private NuteraRunResult readResults(NuteraRunRequest request, Consumer<String> logSink, String message) {
        try {
            Path resultsDir = runtimePath.resolve("results");
            if (!Files.exists(resultsDir)) {
                return new NuteraRunResult(false, "", "", "", "", "Results directory not found: " + resultsDir);
            }

            Path taskArtifacts = request.getTaskWorkspace().resolve("artifacts");
            Files.createDirectories(taskArtifacts);

            Path finalSummary = resultsDir.resolve("final_summary.txt");
            String summaryText = Files.exists(finalSummary)
                    ? Files.readString(finalSummary, StandardCharsets.UTF_8)
                    : "final_summary.txt not found.";
            copyIfExists(finalSummary, taskArtifacts.resolve("final_summary.txt"));

            Path programLevel = newestFile(resultsDir.resolve("program_level"), ".csv");
            Path attemptLevel = newestFile(resultsDir.resolve("attempt_level"), ".csv");
            Path checkpoint = newestFile(resultsDir.resolve("checkpoints"), ".pkl");

            copyIfExists(programLevel, taskArtifacts.resolve("program_level.csv"));
            copyIfExists(attemptLevel, taskArtifacts.resolve("attempt_level.csv"));
            copyIfExists(checkpoint, taskArtifacts.resolve("checkpoint.pkl"));

            String artifactSummary = buildArtifactSummary(finalSummary, programLevel, attemptLevel, checkpoint);
            logSink.accept("[" + Instant.now() + "] Artifacts copied to " + taskArtifacts);

            String candidateFunctions = "See program_level.csv for synthesized ranking functions.";
            String checkerFeedback = "See attempt_level.csv for checker results.";

            return new NuteraRunResult(true, summaryText, candidateFunctions, checkerFeedback, artifactSummary, message);
        } catch (Exception ex) {
            return new NuteraRunResult(false, "", "", "", "", "Failed to read results: " + ex.getMessage());
        }
    }

    private void copyIfExists(Path source, Path target) throws Exception {
        if (source != null && Files.exists(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String buildArtifactSummary(Path finalSummary, Path programLevel, Path attemptLevel, Path checkpoint) {
        List<String> lines = new ArrayList<>();
        lines.add("final_summary.txt: " + nameOrMissing(finalSummary));
        lines.add("program_level.csv: " + nameOrMissing(programLevel));
        lines.add("attempt_level.csv: " + nameOrMissing(attemptLevel));
        lines.add("checkpoint.pkl: " + nameOrMissing(checkpoint));
        return String.join("\n", lines);
    }

    private String nameOrMissing(Path path) {
        if (path == null) return "missing";
        return path.getFileName().toString();
    }

    private Path newestFile(Path dir, String suffix) throws Exception {
        if (dir == null || !Files.exists(dir)) return null;
        try (var stream = Files.list(dir)) {
            Optional<Path> newest = stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .max(Comparator.comparingLong(path -> path.toFile().lastModified()));
            return newest.orElse(null);
        }
    }

    private List<String> buildLocalCommand(NuteraRunRequest request) {
        String runMode = request.getRunMode();
        List<String> command = new ArrayList<>();
        command.add(pythonCommand);

        if ("manual-rank".equals(runMode)) {
            command.add("checker/manual_rank_check.py");
            appendParameters(command, request.getParameters());
            if (request.getRankExpression() != null && !request.getRankExpression().isBlank()) {
                command.add("--rank");
                command.add(request.getRankExpression());
            }
            return command;
        }

        if ("run-stage-checker".equals(runMode)) {
            command.add("main.py");
            command.add("run-stage");
            command.add("checker");
            String params = request.getParameters();
            if (params != null && !params.isBlank()) {
                command.add("--params");
                command.add(params);
            }
            return command;
        }

        command.add("main.py");
        command.add("run");
        String benchmark = resolveBenchmark(request.getBenchmark());
        command.add("--problem-set-csv");
        command.add(benchmark);
        command.add("--config");
        command.add("config/default.yaml");
        appendParameters(command, request.getParameters());
        return command;
    }

    private String resolveBenchmark(String benchmark) {
        if (benchmark == null || benchmark.isBlank()) {
            return "benchmarking/problem-sets/nuTerm_advantage_set.csv";
        }
        if (benchmark.endsWith(".csv")) {
            return benchmark;
        }
        return "benchmarking/problem-sets/" + benchmark + ".csv";
    }

    private void appendParameters(List<String> command, String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return;
        }
        String[] parts = parameters.trim().split("\\s+");
        for (String part : parts) {
            if (!part.isBlank()) {
                command.add(part);
            }
        }
    }

    private List<String> buildDockerCommand(NuteraRunRequest request) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.add("run");
        command.add("--rm");
        String openRouter = System.getenv("OPENROUTER_API_KEY");
        String openAi = System.getenv("OPENAI_API_KEY");
        if (openRouter != null) {
            command.add("-e");
            command.add("OPENROUTER_API_KEY");
        }
        if (openAi != null) {
            command.add("-e");
            command.add("OPENAI_API_KEY");
        }
        command.add("-v");
        command.add(runtimePath + ":/home");
        command.add("-w");
        command.add("/home");
        command.add(dockerImage);
        command.add("bash");
        command.add("-lc");
        command.add(buildDockerScript(request));
        return command;
    }

    private String buildDockerScript(NuteraRunRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("export JAVACHECKER_JAR=\"/home/deps/javachecker/build/libs/javachecker-uber.jar\"; ");
        sb.append("export LD_LIBRARY_PATH=\"/home/libs/linux-x64:${LD_LIBRARY_PATH}\"; ");
        sb.append("export JAVA_TOOL_OPTIONS=\"-Djava.library.path=/home/libs/linux-x64\"; ");
        List<String> local = buildLocalCommand(request);
        local.set(0, "python3");
        sb.append(String.join(" ", local));
        return sb.toString();
    }

    private int execute(List<String> command, Path workDir, Consumer<String> logSink, Map<String, String> envOverrides) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            if (envOverrides != null && !envOverrides.isEmpty()) {
                pb.environment().putAll(envOverrides);
            }
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logSink.accept(line);
                }
            }
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (Exception ex) {
            logSink.accept("[" + Instant.now() + "] Failed to run process: " + ex.getMessage());
            return -1;
        }
    }

    private Path resolveRuntimePath(String configuredPath) {
        Path candidate = Path.of(configuredPath);
        if (!candidate.isAbsolute()) {
            Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            Path direct = current.resolve(candidate).normalize();
            if (Files.exists(direct)) {
                return direct;
            }
            Path parent = current.getParent();
            if (parent != null) {
                Path fallback = parent.resolve(candidate).normalize();
                if (Files.exists(fallback)) {
                    return fallback;
                }
            }
            return direct;
        }
        return candidate.toAbsolutePath().normalize();
    }
}
