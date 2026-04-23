package com.course.ideology.executor;

import com.course.ideology.api.dto.CodeRunTaskDetailResponse;
import com.course.ideology.api.dto.CodeRunTaskRequest;
import com.course.ideology.storage.WorkspaceManager;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskRepository;
import com.course.ideology.task.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CodeRunExecutor {
    private static final Pattern JAVA_PUBLIC_CLASS_PATTERN = Pattern.compile("\\bpublic\\s+class\\s+([A-Za-z_][A-Za-z0-9_]*)");

    private final TaskRepository taskRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final String gccCommand;
    private final String gppCommand;
    private final String goCommand;
    private final String dockerCommand;
    private final String javacCommand;
    private final String javaCommand;
    private final String pythonCommand;
    private final String goDockerImage;
    private final boolean goPreferDocker;
    private final long compileTimeoutSeconds;
    private final long runTimeoutSeconds;
    private final int maxOutputChars;

    public CodeRunExecutor(TaskRepository taskRepository,
                           WorkspaceManager workspaceManager,
                           ObjectMapper objectMapper,
                           @Value("${app.code-run.commands.gcc:gcc}") String gccCommand,
                           @Value("${app.code-run.commands.gpp:g++}") String gppCommand,
                           @Value("${app.code-run.commands.go:go}") String goCommand,
                           @Value("${app.code-run.commands.docker:docker}") String dockerCommand,
                           @Value("${app.code-run.commands.javac:javac}") String javacCommand,
                           @Value("${app.code-run.commands.java:java}") String javaCommand,
                           @Value("${app.code-run.commands.python:python}") String pythonCommand,
                           @Value("${app.code-run.go.docker-image:golang:1.22-alpine}") String goDockerImage,
                           @Value("${app.code-run.go.prefer-docker:true}") boolean goPreferDocker,
                           @Value("${app.code-run.compile-timeout-seconds:15}") long compileTimeoutSeconds,
                           @Value("${app.code-run.run-timeout-seconds:5}") long runTimeoutSeconds,
                           @Value("${app.code-run.max-output-chars:120000}") int maxOutputChars) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
        this.gccCommand = gccCommand;
        this.gppCommand = gppCommand;
        this.goCommand = goCommand;
        this.dockerCommand = dockerCommand;
        this.javacCommand = javacCommand;
        this.javaCommand = javaCommand;
        this.pythonCommand = pythonCommand;
        this.goDockerImage = goDockerImage;
        this.goPreferDocker = goPreferDocker;
        this.compileTimeoutSeconds = Math.max(5L, compileTimeoutSeconds);
        this.runTimeoutSeconds = Math.max(1L, runTimeoutSeconds);
        this.maxOutputChars = Math.max(2000, maxOutputChars);
    }

    public void execute(TaskRecord record, CodeRunTaskRequest request) {
        Path logPath = workspaceManager.resolveTaskFile(record.getTaskId(), "run.log");
        Path resultPath = workspaceManager.resolveTaskFile(record.getTaskId(), "result.json");
        Instant start = Instant.now();

        CodeRunTaskDetailResponse response = CodeRunTaskDetailResponse.pending(record, request.getLanguage());
        response.setTaskStatus(TaskStatus.RUNNING.name());
        response.setCompileStatus("PENDING");
        response.setRunStatus("PENDING");
        response.setCreatedAt(record.getCreatedAt());
        response.setStartTime(start);
        response.setMessage("Running");

        record.updateStatus(TaskStatus.RUNNING, "Running");
        taskRepository.save(record);
        writeResult(resultPath, response);

        try {
            appendLog(logPath, "Code run task started");
            appendLog(logPath, "Language: " + request.getLanguage());
            appendLog(logPath, "Compile timeout(s): " + compileTimeoutSeconds);
            appendLog(logPath, "Run timeout(s): " + runTimeoutSeconds);

            Path taskDir = workspaceManager.createTaskDir(record.getTaskId());
            Path runDir = taskDir.resolve("runner");
            Files.createDirectories(runDir);
            Path stdinPath = runDir.resolve("input.txt");
            Files.writeString(stdinPath, safe(request.getStdin()), StandardCharsets.UTF_8);

            long startedMs = System.currentTimeMillis();
            switch (String.valueOf(request.getLanguage()).toLowerCase(Locale.ROOT)) {
                case "cpp":
                    runCpp(request.getSourceCode(), runDir, stdinPath, response, logPath);
                    break;
                case "c":
                    runC(request.getSourceCode(), runDir, stdinPath, response, logPath);
                    break;
                case "go":
                    runGo(request.getSourceCode(), runDir, stdinPath, response, logPath);
                    break;
                case "java":
                    runJava(request.getSourceCode(), runDir, stdinPath, response, logPath);
                    break;
                case "python":
                    runPython(request.getSourceCode(), runDir, stdinPath, response, logPath);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported language: " + request.getLanguage());
            }
            response.setTimeMs(Math.max(0L, System.currentTimeMillis() - startedMs));

            if ("SYSTEM_ERROR".equals(response.getRunStatus())) {
                response.setTaskStatus(TaskStatus.FAILED.name());
                record.updateStatus(TaskStatus.FAILED, safe(response.getMessage()));
            } else {
                response.setTaskStatus(TaskStatus.SUCCESS.name());
                record.updateStatus(TaskStatus.SUCCESS, safe(response.getMessage()));
            }
            response.setFinishTime(Instant.now());
            appendLog(logPath, "Code run task finished with taskStatus=" + response.getTaskStatus()
                    + ", compileStatus=" + safe(response.getCompileStatus())
                    + ", runStatus=" + safe(response.getRunStatus()));
            writeResult(resultPath, response);
            taskRepository.save(record);
        } catch (Exception ex) {
            response.setTaskStatus(TaskStatus.FAILED.name());
            if (response.getCompileStatus() == null || response.getCompileStatus().isBlank()) {
                response.setCompileStatus("ERROR");
            }
            response.setRunStatus("SYSTEM_ERROR");
            response.setErrorMessage(safe(ex.getMessage()));
            response.setMessage("系统执行异常");
            response.setFinishTime(Instant.now());
            appendLog(logPath, "Code run task failed: " + safe(ex.getMessage()));
            writeResult(resultPath, response);
            record.updateStatus(TaskStatus.FAILED, "Code run failed: " + safe(ex.getMessage()));
            taskRepository.save(record);
        }
    }

    private void runCpp(String sourceCode,
                        Path runDir,
                        Path stdinPath,
                        CodeRunTaskDetailResponse response,
                        Path logPath) throws Exception {
        Path sourcePath = runDir.resolve("main.cpp");
        Files.writeString(sourcePath, safe(sourceCode), StandardCharsets.UTF_8);

        String binaryName = isWindows() ? "main.exe" : "main";
        Path binaryPath = runDir.resolve(binaryName).toAbsolutePath().normalize();
        ProcessRunResult compileResult = null;
        String compileStartFailure = "";
        List<List<String>> candidates = resolveCppCompilerCandidates();
        for (List<String> candidate : candidates) {
            List<String> compileCommand = new ArrayList<>(candidate);
            compileCommand.add("main.cpp");
            compileCommand.add("-O2");
            compileCommand.add("-std=c++17");
            compileCommand.add("-o");
            compileCommand.add(binaryPath.toString());
            appendLog(logPath, "Compile command: " + String.join(" ", compileCommand));
            try {
                compileResult = runCommand(compileCommand, runDir, null, compileTimeoutSeconds);
                appendLog(logPath, "Compile exitCode=" + compileResult.exitCode + ", timeout=" + compileResult.timedOut);
                appendProcessOutput(logPath, "Compile stdout", compileResult.stdout);
                appendProcessOutput(logPath, "Compile stderr", compileResult.stderr);
                break;
            } catch (IllegalStateException startEx) {
                compileStartFailure = safe(startEx.getMessage());
                appendLog(logPath, "Compile start failed, try next compiler: " + compileStartFailure);
            }
        }

        if (compileResult == null) {
            response.setCompileStatus("ERROR");
            response.setRunStatus("PENDING");
            response.setStderr(trimOutput(compileStartFailure.isBlank()
                    ? "C++ 编译器不可用，请确认 g++/clang++ 已安装并在 PATH 中。"
                    : compileStartFailure));
            response.setMessage("编译失败");
            response.setExitCode(-1);
            return;
        }

        response.setCompileStatus(compileResult.timedOut ? "ERROR" : (compileResult.exitCode == 0 ? "SUCCESS" : "ERROR"));
        if (compileResult.timedOut) {
            response.setRunStatus("TIMEOUT");
            response.setStderr(trimOutput("C++ 编译超时\n" + compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译超时");
            response.setExitCode(-1);
            return;
        }
        if (compileResult.exitCode != 0) {
            response.setRunStatus("PENDING");
            response.setStderr(trimOutput(compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译失败");
            response.setExitCode(compileResult.exitCode);
            return;
        }

        if (!Files.exists(binaryPath)) {
            response.setRunStatus("PENDING");
            response.setStdout(trimOutput(compileResult.stdout));
            response.setStderr(trimOutput(compileResult.stderr + "\n编译器返回成功但未生成预期产物: " + binaryPath.getFileName()));
            response.setMessage("编译失败");
            response.setExitCode(-1);
            appendLog(logPath, "Compile artifact missing: " + binaryPath);
            return;
        }

        List<String> runCommand = List.of(binaryPath.toString());
        appendLog(logPath, "Run command: " + String.join(" ", runCommand));
        ProcessRunResult runResult = runCommand(runCommand, runDir, stdinPath, runTimeoutSeconds);
        appendLog(logPath, "Run exitCode=" + runResult.exitCode + ", timeout=" + runResult.timedOut);
        appendProcessOutput(logPath, "Run stdout", runResult.stdout);
        appendProcessOutput(logPath, "Run stderr", runResult.stderr);
        applyRunResult(runResult, response);
    }

    private void runC(String sourceCode,
                      Path runDir,
                      Path stdinPath,
                      CodeRunTaskDetailResponse response,
                      Path logPath) throws Exception {
        Path sourcePath = runDir.resolve("main.c");
        Files.writeString(sourcePath, safe(sourceCode), StandardCharsets.UTF_8);

        String binaryName = isWindows() ? "main.exe" : "main";
        Path binaryPath = runDir.resolve(binaryName).toAbsolutePath().normalize();
        ProcessRunResult compileResult = null;
        String compileStartFailure = "";
        List<List<String>> candidates = resolveCCompilerCandidates();
        for (List<String> candidate : candidates) {
            List<String> compileCommand = new ArrayList<>(candidate);
            compileCommand.add("main.c");
            compileCommand.add("-O2");
            compileCommand.add("-std=c11");
            compileCommand.add("-o");
            compileCommand.add(binaryPath.toString());
            appendLog(logPath, "Compile command: " + String.join(" ", compileCommand));
            try {
                compileResult = runCommand(compileCommand, runDir, null, compileTimeoutSeconds);
                appendLog(logPath, "Compile exitCode=" + compileResult.exitCode + ", timeout=" + compileResult.timedOut);
                appendProcessOutput(logPath, "Compile stdout", compileResult.stdout);
                appendProcessOutput(logPath, "Compile stderr", compileResult.stderr);
                break;
            } catch (IllegalStateException startEx) {
                compileStartFailure = safe(startEx.getMessage());
                appendLog(logPath, "Compile start failed, try next compiler: " + compileStartFailure);
            }
        }

        if (compileResult == null) {
            response.setCompileStatus("ERROR");
            response.setRunStatus("PENDING");
            response.setStderr(trimOutput(compileStartFailure.isBlank()
                    ? "C compiler is not available. Please install gcc/clang or configure CODE_RUN_GCC_COMMAND."
                    : compileStartFailure));
            response.setMessage("编译失败");
            response.setExitCode(-1);
            return;
        }

        response.setCompileStatus(compileResult.timedOut ? "ERROR" : (compileResult.exitCode == 0 ? "SUCCESS" : "ERROR"));
        if (compileResult.timedOut) {
            response.setRunStatus("TIMEOUT");
            response.setStderr(trimOutput("C compile timeout\n" + compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译超时");
            response.setExitCode(-1);
            return;
        }
        if (compileResult.exitCode != 0) {
            response.setRunStatus("PENDING");
            response.setStderr(trimOutput(compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译失败");
            response.setExitCode(compileResult.exitCode);
            return;
        }

        if (!Files.exists(binaryPath)) {
            response.setRunStatus("PENDING");
            response.setStdout(trimOutput(compileResult.stdout));
            response.setStderr(trimOutput(compileResult.stderr + "\nCompiler finished but artifact is missing: " + binaryPath.getFileName()));
            response.setMessage("编译失败");
            response.setExitCode(-1);
            appendLog(logPath, "Compile artifact missing: " + binaryPath);
            return;
        }

        List<String> runCommand = List.of(binaryPath.toString());
        appendLog(logPath, "Run command: " + String.join(" ", runCommand));
        ProcessRunResult runResult = runCommand(runCommand, runDir, stdinPath, runTimeoutSeconds);
        appendLog(logPath, "Run exitCode=" + runResult.exitCode + ", timeout=" + runResult.timedOut);
        appendProcessOutput(logPath, "Run stdout", runResult.stdout);
        appendProcessOutput(logPath, "Run stderr", runResult.stderr);
        applyRunResult(runResult, response);
    }

    private void runGo(String sourceCode,
                       Path runDir,
                       Path stdinPath,
                       CodeRunTaskDetailResponse response,
                       Path logPath) throws Exception {
        Path sourcePath = runDir.resolve("main.go");
        Files.writeString(sourcePath, safe(sourceCode), StandardCharsets.UTF_8);

        Path dockerBinaryPath = runDir.resolve("main").toAbsolutePath().normalize();
        Path localBinaryPath = runDir.resolve(isWindows() ? "main.exe" : "main").toAbsolutePath().normalize();

        GoCompileContext goContext = compileGo(sourcePath, runDir, localBinaryPath, logPath);
        Path binaryPath = goContext.dockerMode ? dockerBinaryPath : localBinaryPath;
        ProcessRunResult compileResult = goContext.compileResult;
        response.setCompileStatus(compileResult.timedOut ? "ERROR" : (compileResult.exitCode == 0 ? "SUCCESS" : "ERROR"));

        if (compileResult.timedOut) {
            response.setRunStatus("TIMEOUT");
            response.setStderr(trimOutput("Go compile timeout\n" + compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译超时");
            response.setExitCode(-1);
            return;
        }
        if (compileResult.exitCode != 0) {
            response.setRunStatus("PENDING");
            response.setStderr(trimOutput(compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译失败");
            response.setExitCode(compileResult.exitCode);
            return;
        }

        if (!Files.exists(binaryPath)) {
            response.setRunStatus("PENDING");
            response.setStderr(trimOutput("Go compile finished but artifact is missing: " + binaryPath.getFileName()));
            response.setMessage("编译失败");
            response.setExitCode(-1);
            appendLog(logPath, "Compile artifact missing: " + binaryPath);
            return;
        }

        ProcessRunResult runResult;
        if (goContext.dockerMode) {
            List<String> runCommand = buildDockerRunBaseCommand(runDir);
            runCommand.add("sh");
            runCommand.add("-lc");
            runCommand.add("./main < input.txt");
            appendLog(logPath, "Run command: " + String.join(" ", runCommand));
            runResult = runCommand(runCommand, runDir, null, runTimeoutSeconds);
        } else {
            List<String> runCommand = List.of(binaryPath.toString());
            appendLog(logPath, "Run command: " + String.join(" ", runCommand));
            runResult = runCommand(runCommand, runDir, stdinPath, runTimeoutSeconds);
        }
        appendLog(logPath, "Run exitCode=" + runResult.exitCode + ", timeout=" + runResult.timedOut);
        appendProcessOutput(logPath, "Run stdout", runResult.stdout);
        appendProcessOutput(logPath, "Run stderr", runResult.stderr);
        applyRunResult(runResult, response);
    }

    private void runJava(String sourceCode,
                         Path runDir,
                         Path stdinPath,
                         CodeRunTaskDetailResponse response,
                         Path logPath) throws Exception {
        String javaClassError = validateJavaMainClass(sourceCode);
        if (!javaClassError.isBlank()) {
            response.setCompileStatus("ERROR");
            response.setRunStatus("PENDING");
            response.setStderr(javaClassError);
            response.setStdout("");
            response.setMessage("编译失败");
            response.setExitCode(-1);
            appendLog(logPath, "Java source rejected: " + javaClassError);
            return;
        }
        Path sourcePath = runDir.resolve("Main.java");
        Files.writeString(sourcePath, safe(sourceCode), StandardCharsets.UTF_8);

        List<String> compileCommand = new ArrayList<>(splitCommandOrFallback(javacCommand, "javac"));
        compileCommand.add("Main.java");

        appendLog(logPath, "Compile command: " + String.join(" ", compileCommand));
        ProcessRunResult compileResult = runCommand(compileCommand, runDir, null, compileTimeoutSeconds);
        appendLog(logPath, "Compile exitCode=" + compileResult.exitCode + ", timeout=" + compileResult.timedOut);
        appendProcessOutput(logPath, "Compile stdout", compileResult.stdout);
        appendProcessOutput(logPath, "Compile stderr", compileResult.stderr);
        response.setCompileStatus(compileResult.timedOut ? "ERROR" : (compileResult.exitCode == 0 ? "SUCCESS" : "ERROR"));
        if (compileResult.timedOut) {
            response.setRunStatus("TIMEOUT");
            response.setStderr(trimOutput("Java 编译超时\n" + compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译超时");
            response.setExitCode(-1);
            return;
        }
        if (compileResult.exitCode != 0) {
            response.setRunStatus("PENDING");
            response.setStderr(trimOutput(compileResult.stderr));
            response.setStdout(trimOutput(compileResult.stdout));
            response.setMessage("编译失败");
            response.setExitCode(compileResult.exitCode);
            return;
        }

        List<String> runCommand = new ArrayList<>(splitCommandOrFallback(javaCommand, "java"));
        runCommand.add("-cp");
        runCommand.add(".");
        runCommand.add("Main");

        appendLog(logPath, "Run command: " + String.join(" ", runCommand));
        ProcessRunResult runResult = runCommand(runCommand, runDir, stdinPath, runTimeoutSeconds);
        appendLog(logPath, "Run exitCode=" + runResult.exitCode + ", timeout=" + runResult.timedOut);
        appendProcessOutput(logPath, "Run stdout", runResult.stdout);
        appendProcessOutput(logPath, "Run stderr", runResult.stderr);
        applyRunResult(runResult, response);
    }

    private void runPython(String sourceCode,
                           Path runDir,
                           Path stdinPath,
                           CodeRunTaskDetailResponse response,
                           Path logPath) throws Exception {
        Path sourcePath = runDir.resolve("main.py");
        Files.writeString(sourcePath, safe(sourceCode), StandardCharsets.UTF_8);
        response.setCompileStatus("NOT_REQUIRED");

        List<String> runCommand = new ArrayList<>(splitCommandOrFallback(pythonCommand, "python"));
        runCommand.add("main.py");

        appendLog(logPath, "Run command: " + String.join(" ", runCommand));
        ProcessRunResult runResult = runCommand(runCommand, runDir, stdinPath, runTimeoutSeconds);
        appendLog(logPath, "Run exitCode=" + runResult.exitCode + ", timeout=" + runResult.timedOut);
        appendProcessOutput(logPath, "Run stdout", runResult.stdout);
        appendProcessOutput(logPath, "Run stderr", runResult.stderr);
        applyRunResult(runResult, response);
    }

    private GoCompileContext compileGo(Path sourcePath,
                                       Path runDir,
                                       Path binaryPath,
                                       Path logPath) throws Exception {
        boolean localGoAvailable = isCommandAvailable(goCommand, "go");
        String dockerInfraError = "";
        if (goPreferDocker) {
            try {
                List<String> compileCommand = buildDockerRunBaseCommand(runDir);
                compileCommand.add("go");
                compileCommand.add("build");
                compileCommand.add("-o");
                compileCommand.add("./main");
                compileCommand.add(sourcePath.getFileName().toString());
                appendLog(logPath, "Compile command: " + String.join(" ", compileCommand));
                ProcessRunResult compileResult = runCommand(compileCommand, runDir, null, compileTimeoutSeconds);
                appendLog(logPath, "Compile exitCode=" + compileResult.exitCode + ", timeout=" + compileResult.timedOut);
                appendProcessOutput(logPath, "Compile stdout", compileResult.stdout);
                appendProcessOutput(logPath, "Compile stderr", compileResult.stderr);

                if (compileResult.exitCode == 0 || compileResult.timedOut || looksLikeGoSourceCompileError(compileResult.stderr, compileResult.stdout)) {
                    return GoCompileContext.docker(compileResult);
                }
                dockerInfraError = firstNonBlank(compileResult.stderr, compileResult.stdout);
                appendLog(logPath, "Docker go compile infra error detected: " + dockerInfraError);
            } catch (IllegalStateException startEx) {
                dockerInfraError = safe(startEx.getMessage());
                appendLog(logPath, "Docker go compile start failed: " + dockerInfraError);
            }
        }

        if (!localGoAvailable) {
            String message = goPreferDocker
                    ? "Go runtime is unavailable. Docker mode failed and local go compiler was not found.\n"
                    + "Please start Docker Desktop or install Go, then retry.\n"
                    + (dockerInfraError.isBlank() ? "" : ("Docker error: " + dockerInfraError))
                    : "Go compiler is not available. Please install Go or configure CODE_RUN_GO_COMMAND.";
            ProcessRunResult result = new ProcessRunResult();
            result.exitCode = -1;
            result.timedOut = false;
            result.stdout = "";
            result.stderr = trimOutput(message);
            return GoCompileContext.local(result);
        }

        List<String> compileCommand = new ArrayList<>(splitCommandOrFallback(goCommand, "go"));
        compileCommand.add("build");
        compileCommand.add("-o");
        compileCommand.add(binaryPath.toString());
        compileCommand.add(sourcePath.getFileName().toString());
        appendLog(logPath, "Compile command: " + String.join(" ", compileCommand));
        ProcessRunResult compileResult = runCommand(compileCommand, runDir, null, compileTimeoutSeconds);
        appendLog(logPath, "Compile exitCode=" + compileResult.exitCode + ", timeout=" + compileResult.timedOut);
        appendProcessOutput(logPath, "Compile stdout", compileResult.stdout);
        appendProcessOutput(logPath, "Compile stderr", compileResult.stderr);
        return GoCompileContext.local(compileResult);
    }

    private List<String> buildDockerRunBaseCommand(Path runDir) {
        List<String> command = new ArrayList<>(splitCommandOrFallback(dockerCommand, "docker"));
        command.add("run");
        command.add("--rm");
        command.add("-v");
        command.add(toDockerMountSource(runDir) + ":/workspace");
        command.add("-w");
        command.add("/workspace");
        command.add(safe(goDockerImage).isBlank() ? "golang:1.22-alpine" : goDockerImage);
        return command;
    }

    private String toDockerMountSource(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

    private boolean looksLikeGoSourceCompileError(String stderr, String stdout) {
        String merged = (safe(stderr) + "\n" + safe(stdout)).toLowerCase(Locale.ROOT);
        return merged.contains("main.go:") || merged.contains("syntax error")
                || merged.contains("undefined") || merged.contains("imported and not used")
                || merged.contains("expected");
    }

    private String firstNonBlank(String first, String second) {
        String v1 = safe(first).trim();
        if (!v1.isBlank()) {
            return v1;
        }
        return safe(second).trim();
    }

    private String validateJavaMainClass(String sourceCode) {
        String source = safe(sourceCode);
        Matcher matcher = JAVA_PUBLIC_CLASS_PATTERN.matcher(source);
        if (matcher.find()) {
            String className = matcher.group(1);
            if (!"Main".equals(className)) {
                return "Java 学习模式仅支持 Main.java，检测到 public class " + className + "。请将主类名改为 Main。";
            }
        } else if (!source.contains("class Main")) {
            return "Java 学习模式仅支持 Main.java。请提供 class Main 并包含 main 方法。";
        }
        return "";
    }

    private void applyRunResult(ProcessRunResult runResult, CodeRunTaskDetailResponse response) {
        response.setStdout(trimOutput(runResult.stdout));
        response.setStderr(trimOutput(runResult.stderr));
        response.setExitCode(runResult.exitCode);

        if (runResult.timedOut) {
            response.setRunStatus("TIMEOUT");
            response.setMessage("运行超时");
            return;
        }
        if (runResult.exitCode == 0) {
            response.setRunStatus("SUCCESS");
            response.setMessage("运行完成");
            return;
        }
        response.setRunStatus("RUNTIME_ERROR");
        response.setMessage("运行失败");
    }

    private ProcessRunResult runCommand(List<String> command,
                                        Path cwd,
                                        Path stdinFile,
                                        long timeoutSeconds) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        builder.redirectErrorStream(false);
        if (stdinFile != null) {
            builder.redirectInput(stdinFile.toFile());
        }
        Process process;
        try {
            process = builder.start();
        } catch (Exception ex) {
            throw new IllegalStateException("无法启动进程: " + String.join(" ", command) + "。请检查本机运行环境。原因: " + safe(ex.getMessage()), ex);
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        Thread outThread = streamCollector(process.getInputStream(), stdout);
        Thread errThread = streamCollector(process.getErrorStream(), stderr);
        outThread.start();
        errThread.start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        int exitCode;
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
            exitCode = -1;
        } else {
            exitCode = process.exitValue();
        }

        outThread.join(2000);
        errThread.join(2000);

        ProcessRunResult result = new ProcessRunResult();
        result.exitCode = exitCode;
        result.timedOut = !finished;
        result.stdout = stdout.toString();
        result.stderr = stderr.toString();
        return result;
    }

    private Thread streamCollector(InputStream inputStream, StringBuilder collector) {
        return new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    synchronized (collector) {
                        if (collector.length() >= maxOutputChars) {
                            continue;
                        }
                        int allowed = Math.min(read, maxOutputChars - collector.length());
                        collector.append(buffer, 0, allowed);
                    }
                }
            } catch (Exception ignored) {
                // ignore stream read errors
            }
        }, "code-run-stream-" + System.nanoTime());
    }

    private List<String> splitCommand(String rawCommand) {
        String commandText = safe(rawCommand).trim();
        if (commandText.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        char quoteChar = 0;
        for (int i = 0; i < commandText.length(); i += 1) {
            char ch = commandText.charAt(i);
            if (inQuote) {
                if (ch == quoteChar) {
                    inQuote = false;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                inQuote = true;
                quoteChar = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result.isEmpty() ? List.of(commandText) : result;
    }

    private List<String> splitCommandOrFallback(String rawCommand, String fallback) {
        List<String> command = splitCommand(rawCommand);
        if (command.isEmpty()) {
            return List.of(fallback);
        }
        return command;
    }

    private List<List<String>> resolveCppCompilerCandidates() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        String configured = safe(gppCommand).trim();
        if (!configured.isBlank()) {
            unique.add(configured);
        }
        unique.add("g++");
        unique.add("clang++");
        unique.add("c++");

        List<List<String>> candidates = new ArrayList<>();
        for (String raw : unique) {
            List<String> parsed = splitCommand(raw);
            if (!parsed.isEmpty()) {
                candidates.add(parsed);
            }
        }
        return candidates;
    }

    private List<List<String>> resolveCCompilerCandidates() {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        String configured = safe(gccCommand).trim();
        if (!configured.isBlank()) {
            unique.add(configured);
        }
        unique.add("gcc");
        unique.add("clang");
        unique.add("cc");

        List<List<String>> candidates = new ArrayList<>();
        for (String raw : unique) {
            List<String> parsed = splitCommand(raw);
            if (!parsed.isEmpty()) {
                candidates.add(parsed);
            }
        }
        return candidates;
    }

    private boolean isCommandAvailable(String configuredCommand, String fallback) {
        List<String> command = splitCommandOrFallback(configuredCommand, fallback);
        if (command.isEmpty()) {
            return false;
        }
        String executable = command.get(0);
        if (executable.contains("\\") || executable.contains("/") || executable.contains(":")) {
            Path path = Path.of(executable.replace("\"", ""));
            if (Files.exists(path)) {
                return true;
            }
        }
        String envPath = safe(System.getenv("PATH"));
        if (envPath.isBlank()) {
            return false;
        }
        String[] entries = envPath.split(java.io.File.pathSeparator);
        String name = executable;
        if (isWindows() && !name.toLowerCase(Locale.ROOT).endsWith(".exe")) {
            name = name + ".exe";
        }
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry).resolve(name);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private String trimOutput(String value) {
        String text = safe(value);
        if (text.length() <= maxOutputChars) {
            return text;
        }
        return text.substring(0, maxOutputChars) + "\n...[truncated]";
    }

    private void appendProcessOutput(Path logPath, String prefix, String content) {
        String text = safe(content).trim();
        if (text.isBlank()) {
            return;
        }
        appendLog(logPath, prefix + ":\n" + trimOutput(text));
    }

    private void appendLog(Path logPath, String message) {
        try {
            Files.writeString(logPath, "[" + Instant.now() + "] " + safe(message) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private void writeResult(Path resultPath, CodeRunTaskDetailResponse response) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), response);
        } catch (Exception ignored) {
            // ignore
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class ProcessRunResult {
        private int exitCode;
        private boolean timedOut;
        private String stdout;
        private String stderr;
    }

    private static class GoCompileContext {
        private final ProcessRunResult compileResult;
        private final boolean dockerMode;

        private GoCompileContext(ProcessRunResult compileResult, boolean dockerMode) {
            this.compileResult = compileResult;
            this.dockerMode = dockerMode;
        }

        private static GoCompileContext docker(ProcessRunResult compileResult) {
            return new GoCompileContext(compileResult, true);
        }

        private static GoCompileContext local(ProcessRunResult compileResult) {
            return new GoCompileContext(compileResult, false);
        }
    }
}
