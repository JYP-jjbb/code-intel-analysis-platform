package com.course.ideology.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class WorkspaceManager {
    private final Path rootPath;

    public WorkspaceManager(@Value("${app.workspace.root:}") String configuredRoot) {
        if (configuredRoot != null && !configuredRoot.isBlank()) {
            rootPath = Paths.get(configuredRoot).toAbsolutePath().normalize();
        } else {
            rootPath = resolveDefaultRoot();
        }
        try {
            Files.createDirectories(rootPath);
        } catch (Exception ignored) {
        }
    }

    public Path getRootPath() {
        return rootPath;
    }

    public Path createTaskDir(String taskId) {
        try {
            Path taskDir = rootPath.resolve(taskId);
            Files.createDirectories(taskDir);
            return taskDir;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create task workspace", e);
        }
    }

    public Path resolveTaskFile(String taskId, String fileName) {
        return rootPath.resolve(taskId).resolve(fileName);
    }

    private Path resolveDefaultRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path direct = current.resolve("workspace").resolve("tasks");
        if (Files.exists(current.resolve("workspace"))) {
            return direct;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("workspace"))) {
            return parent.resolve("workspace").resolve("tasks");
        }
        return direct;
    }
}

