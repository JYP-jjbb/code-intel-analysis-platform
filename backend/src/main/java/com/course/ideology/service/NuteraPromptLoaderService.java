package com.course.ideology.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class NuteraPromptLoaderService {
    private final Path promptPath;

    public NuteraPromptLoaderService(@Value("${app.nutera.rfPromptPath:runtimes/nutera/config/prompts/rf_generation_l3.txt}") String configuredPath) {
        this.promptPath = resolvePath(configuredPath);
    }

    public String loadPrompt() {
        try {
            if (!Files.exists(promptPath)) {
                throw new IllegalStateException("Prompt file does not exist: " + promptPath);
            }
            return Files.readString(promptPath, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read prompt file: " + ex.getMessage(), ex);
        }
    }

    private Path resolvePath(String configuredPath) {
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
