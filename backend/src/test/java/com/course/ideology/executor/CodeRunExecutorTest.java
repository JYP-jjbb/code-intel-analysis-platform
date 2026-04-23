package com.course.ideology.executor;

import com.course.ideology.api.dto.CodeRunTaskDetailResponse;
import com.course.ideology.api.dto.CodeRunTaskRequest;
import com.course.ideology.storage.WorkspaceManager;
import com.course.ideology.task.InMemoryTaskRepository;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;

class CodeRunExecutorTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private Path tempRoot;

    @AfterEach
    void cleanup() throws Exception {
        if (tempRoot == null || !Files.exists(tempRoot)) {
            return;
        }
        try (var walk = Files.walk(tempRoot)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Test
    void javaRunShouldSucceed() throws Exception {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        tempRoot = Files.createTempDirectory("coderun-test");
        WorkspaceManager workspaceManager = new WorkspaceManager(tempRoot.toString());
        CodeRunExecutor executor = new CodeRunExecutor(
                taskRepository,
                workspaceManager,
                objectMapper,
                "gcc",
                "g++",
                "go",
                "docker",
                "javac",
                "java",
                "python",
                "golang:1.22-alpine",
                true,
                20,
                5,
                120000
        );

        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_RUN);
        Path taskDir = workspaceManager.createTaskDir(taskId);
        record.setSourcePath(taskDir.resolve("source.json").toString());
        record.setResultPath(taskDir.resolve("result.json").toString());
        record.setLogPath(taskDir.resolve("run.log").toString());
        taskRepository.save(record);

        CodeRunTaskRequest request = new CodeRunTaskRequest();
        request.setLanguage("java");
        request.setSourceCode("public class Main {\n  public static void main(String[] args) {\n    java.util.Scanner sc = new java.util.Scanner(System.in);\n    String name = sc.nextLine();\n    System.out.print(\"hello-\" + name);\n  }\n}");
        request.setStdin("nutera");

        executor.execute(record, request);

        CodeRunTaskDetailResponse detail = objectMapper.readValue(Path.of(record.getResultPath()).toFile(), CodeRunTaskDetailResponse.class);
        Assertions.assertEquals("SUCCESS", detail.getTaskStatus());
        Assertions.assertEquals("SUCCESS", detail.getCompileStatus());
        Assertions.assertEquals("SUCCESS", detail.getRunStatus());
        Assertions.assertTrue(String.valueOf(detail.getStdout()).contains("hello-nutera"));
    }

    @Test
    void javaRunShouldRejectNonMainClass() throws Exception {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        tempRoot = Files.createTempDirectory("coderun-test");
        WorkspaceManager workspaceManager = new WorkspaceManager(tempRoot.toString());
        CodeRunExecutor executor = new CodeRunExecutor(
                taskRepository,
                workspaceManager,
                objectMapper,
                "gcc",
                "g++",
                "go",
                "docker",
                "javac",
                "java",
                "python",
                "golang:1.22-alpine",
                true,
                20,
                5,
                120000
        );

        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_RUN);
        Path taskDir = workspaceManager.createTaskDir(taskId);
        record.setSourcePath(taskDir.resolve("source.json").toString());
        record.setResultPath(taskDir.resolve("result.json").toString());
        record.setLogPath(taskDir.resolve("run.log").toString());
        taskRepository.save(record);

        CodeRunTaskRequest request = new CodeRunTaskRequest();
        request.setLanguage("java");
        request.setSourceCode("public class Demo { public static void main(String[] args) { System.out.println(\"x\"); } }");
        request.setStdin("");

        executor.execute(record, request);

        CodeRunTaskDetailResponse detail = objectMapper.readValue(Path.of(record.getResultPath()).toFile(), CodeRunTaskDetailResponse.class);
        Assertions.assertEquals("SUCCESS", detail.getTaskStatus());
        Assertions.assertEquals("ERROR", detail.getCompileStatus());
        Assertions.assertEquals("PENDING", detail.getRunStatus());
        Assertions.assertTrue(String.valueOf(detail.getStderr()).contains("Main.java"));
    }

    @Test
    void cppRunShouldOutputSum() throws Exception {
        Assumptions.assumeTrue(commandExists("g++"), "g++ is required for cpp test");

        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        tempRoot = Files.createTempDirectory("coderun-test");
        WorkspaceManager workspaceManager = new WorkspaceManager(tempRoot.toString());
        CodeRunExecutor executor = new CodeRunExecutor(
                taskRepository,
                workspaceManager,
                objectMapper,
                "gcc",
                "g++",
                "go",
                "docker",
                "javac",
                "java",
                "python",
                "golang:1.22-alpine",
                true,
                20,
                5,
                120000
        );

        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_RUN);
        Path taskDir = workspaceManager.createTaskDir(taskId);
        record.setSourcePath(taskDir.resolve("source.json").toString());
        record.setResultPath(taskDir.resolve("result.json").toString());
        record.setLogPath(taskDir.resolve("run.log").toString());
        taskRepository.save(record);

        CodeRunTaskRequest request = new CodeRunTaskRequest();
        request.setLanguage("cpp");
        request.setSourceCode("#include <iostream>\nusing namespace std;\nint main(){int a,b;cin>>a>>b;cout<<a+b;return 0;}");
        request.setStdin("2 3");

        executor.execute(record, request);

        CodeRunTaskDetailResponse detail = objectMapper.readValue(Path.of(record.getResultPath()).toFile(), CodeRunTaskDetailResponse.class);
        Assertions.assertEquals("SUCCESS", detail.getTaskStatus());
        Assertions.assertEquals("SUCCESS", detail.getCompileStatus());
        Assertions.assertEquals("SUCCESS", detail.getRunStatus());
        Assertions.assertEquals("5", String.valueOf(detail.getStdout()).trim());
    }

    @Test
    void cRunShouldOutputSum() throws Exception {
        Assumptions.assumeTrue(commandExists("gcc"), "gcc is required for c test");

        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        tempRoot = Files.createTempDirectory("coderun-test");
        WorkspaceManager workspaceManager = new WorkspaceManager(tempRoot.toString());
        CodeRunExecutor executor = new CodeRunExecutor(
                taskRepository,
                workspaceManager,
                objectMapper,
                "gcc",
                "g++",
                "go",
                "docker",
                "javac",
                "java",
                "python",
                "golang:1.22-alpine",
                true,
                20,
                5,
                120000
        );

        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_RUN);
        Path taskDir = workspaceManager.createTaskDir(taskId);
        record.setSourcePath(taskDir.resolve("source.json").toString());
        record.setResultPath(taskDir.resolve("result.json").toString());
        record.setLogPath(taskDir.resolve("run.log").toString());
        taskRepository.save(record);

        CodeRunTaskRequest request = new CodeRunTaskRequest();
        request.setLanguage("c");
        request.setSourceCode("#include <stdio.h>\nint main(){int a,b;scanf(\"%d%d\",&a,&b);printf(\"%d\",a+b);return 0;}");
        request.setStdin("2 3");

        executor.execute(record, request);

        CodeRunTaskDetailResponse detail = objectMapper.readValue(Path.of(record.getResultPath()).toFile(), CodeRunTaskDetailResponse.class);
        Assertions.assertEquals("SUCCESS", detail.getTaskStatus());
        Assertions.assertEquals("SUCCESS", detail.getCompileStatus());
        Assertions.assertEquals("SUCCESS", detail.getRunStatus());
        Assertions.assertEquals("5", String.valueOf(detail.getStdout()).trim());
    }

    @Test
    void goRunShouldOutputSumWhenRuntimeAvailable() throws Exception {
        boolean localGoAvailable = commandExists("go");
        boolean dockerGoRuntimeAvailable = dockerDaemonReady() && dockerImageExists("golang:1.22-alpine");
        Assumptions.assumeTrue(localGoAvailable || dockerGoRuntimeAvailable, "go runtime is required for go test");

        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        tempRoot = Files.createTempDirectory("coderun-test");
        WorkspaceManager workspaceManager = new WorkspaceManager(tempRoot.toString());
        CodeRunExecutor executor = new CodeRunExecutor(
                taskRepository,
                workspaceManager,
                objectMapper,
                "gcc",
                "g++",
                "go",
                "docker",
                "javac",
                "java",
                "python",
                "golang:1.22-alpine",
                true,
                30,
                8,
                120000
        );

        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_RUN);
        Path taskDir = workspaceManager.createTaskDir(taskId);
        record.setSourcePath(taskDir.resolve("source.json").toString());
        record.setResultPath(taskDir.resolve("result.json").toString());
        record.setLogPath(taskDir.resolve("run.log").toString());
        taskRepository.save(record);

        CodeRunTaskRequest request = new CodeRunTaskRequest();
        request.setLanguage("go");
        request.setSourceCode("package main\nimport \"fmt\"\nfunc main(){var a,b int; fmt.Scan(&a,&b); fmt.Println(a+b)}");
        request.setStdin("2 3");

        executor.execute(record, request);

        CodeRunTaskDetailResponse detail = objectMapper.readValue(Path.of(record.getResultPath()).toFile(), CodeRunTaskDetailResponse.class);
        Assertions.assertEquals("SUCCESS", detail.getTaskStatus());
        Assertions.assertEquals("SUCCESS", detail.getCompileStatus());
        Assertions.assertEquals("SUCCESS", detail.getRunStatus());
        Assertions.assertEquals("5", String.valueOf(detail.getStdout()).trim());
    }

    @Test
    void goRunShouldReturnClearMessageWhenNoRuntimeAvailable() throws Exception {
        InMemoryTaskRepository taskRepository = new InMemoryTaskRepository();
        tempRoot = Files.createTempDirectory("coderun-test");
        WorkspaceManager workspaceManager = new WorkspaceManager(tempRoot.toString());
        CodeRunExecutor executor = new CodeRunExecutor(
                taskRepository,
                workspaceManager,
                objectMapper,
                "gcc",
                "g++",
                "go-missing-command",
                "docker-missing-command",
                "javac",
                "java",
                "python",
                "golang:1.22-alpine",
                true,
                20,
                5,
                120000
        );

        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_RUN);
        Path taskDir = workspaceManager.createTaskDir(taskId);
        record.setSourcePath(taskDir.resolve("source.json").toString());
        record.setResultPath(taskDir.resolve("result.json").toString());
        record.setLogPath(taskDir.resolve("run.log").toString());
        taskRepository.save(record);

        CodeRunTaskRequest request = new CodeRunTaskRequest();
        request.setLanguage("go");
        request.setSourceCode("package main\nimport \"fmt\"\nfunc main(){var a,b int; fmt.Scan(&a,&b); fmt.Println(a+b)}");
        request.setStdin("2 3");

        executor.execute(record, request);

        CodeRunTaskDetailResponse detail = objectMapper.readValue(Path.of(record.getResultPath()).toFile(), CodeRunTaskDetailResponse.class);
        Assertions.assertEquals("SUCCESS", detail.getTaskStatus());
        Assertions.assertEquals("ERROR", detail.getCompileStatus());
        Assertions.assertEquals("PENDING", detail.getRunStatus());
        Assertions.assertTrue(String.valueOf(detail.getStderr()).contains("Go runtime is unavailable"));
    }

    private boolean commandExists(String command) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        String[] paths = path.split(File.pathSeparator);
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String executable = os.contains("win") && !command.endsWith(".exe") ? command + ".exe" : command;
        for (String raw : paths) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            File dir = new File(raw);
            File file = new File(dir, executable);
            if (file.exists() && file.isFile()) {
                return true;
            }
        }
        return false;
    }

    private boolean dockerDaemonReady() {
        if (!commandExists("docker")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("docker", "version")
                    .redirectErrorStream(true)
                    .start();
            boolean done = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
            return done && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean dockerImageExists(String image) {
        if (!dockerDaemonReady()) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("docker", "image", "inspect", image)
                    .redirectErrorStream(true)
                    .start();
            boolean done = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
            return done && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}
