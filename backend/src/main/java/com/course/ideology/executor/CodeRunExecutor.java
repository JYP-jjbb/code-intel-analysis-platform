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
    private final String gppCommand;
    private final String javacCommand;
    private final String javaCommand;
    private final String pythonCommand;
    private final long compileTimeoutSeconds;
    private final long runTimeoutSeconds;
    private final int maxOutputChars;

    public CodeRunExecutor(TaskRepository taskRepository,
                           WorkspaceManager workspaceManager,
                           ObjectMapper objectMapper,
                           @Value("${app.code-run.commands.gpp:g++}") String gppCommand,
                           @Value("${app.code-run.commands.javac:javac}") String javacCommand,
                           @Value("${app.code-run.commands.java:java}") String javaCommand,
                           @Value("${app.code-run.commands.python:python}") String pythonCommand,
                           @Value("${app.code-run.compile-timeout-seconds:15}") long compileTimeoutSeconds,
                           @Value("${app.code-run.run-timeout-seconds:5}") long runTimeoutSeconds,
                           @Value("${app.code-run.max-output-chars:120000}") int maxOutputChars) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
        this.gppCommand = gppCommand;
        this.javacCommand = javacCommand;
        this.javaCommand = javaCommand;
        this.pythonCommand = pythonCommand;
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
}
