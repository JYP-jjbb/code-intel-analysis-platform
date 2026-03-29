package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraCaseSourceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class NuteraCaseSourceService {
    private static final Map<String, String> DATASET_TO_CSV = buildDatasetMap();

    private final Path projectRoot;
    private final Path benchmarkingRoot;

    public NuteraCaseSourceService(
            @Value("${app.nutera.runtimePath:runtimes/nutera}") String configuredRuntimePath
    ) {
        this.projectRoot = resolveProjectRoot();
        Path runtimeRoot = resolveRuntimeRoot(configuredRuntimePath, projectRoot);
        this.benchmarkingRoot = runtimeRoot.resolve("benchmarking").normalize();
    }

    public NuteraCaseSourceResponse loadCaseSource(String dataset, Integer entryIndex) {
        String datasetKey = normalizeDataset(dataset);
        if ("NONE".equals(datasetKey)) {
            throw new IllegalArgumentException("Dataset 'None' does not map to a source program.");
        }

        String csvRelativePath = DATASET_TO_CSV.get(datasetKey);
        if (csvRelativePath == null) {
            throw new IllegalArgumentException("Unsupported dataset: " + dataset);
        }

        List<String> logs = new ArrayList<>();
        append(logs, "Selected dataset: " + datasetKey);

        Path csvPath = projectRoot.resolve(csvRelativePath).normalize();
        validatePathInsideBenchmarking(csvPath, "CSV path");
        ensureFileExists(csvPath, "CSV file");
        append(logs, "CSV loaded: " + toDisplayPath(csvPath));

        List<ProgramEntry> entries = loadEntries(csvPath);
        if (entries.isEmpty()) {
            throw new IllegalStateException("No program entries found in CSV: " + toDisplayPath(csvPath));
        }

        int index = entryIndex == null ? 0 : entryIndex;
        if (index < 0 || index >= entries.size()) {
            throw new IllegalArgumentException("Program index out of range: " + index + " (total " + entries.size() + ")");
        }

        ProgramEntry selected = entries.get(index);
        append(logs, "Raw program path from CSV: " + selected.rawProgramPath);
        append(logs, "Program entry selected: " + selected.className + " (entry " + (index + 1) + "/" + entries.size() + ")");

        Path binaryPath = resolveProgramBinaryPath(csvPath, selected.rawProgramPath, selected.className, logs);
        validatePathInsideBenchmarking(binaryPath, "Program binary path");
        ensureFileExists(binaryPath, "Program binary");
        append(logs, "Normalized program binary path: " + toDisplayPath(binaryPath));

        Path sourcePath = resolveSourcePath(binaryPath, selected.className, logs);
        validatePathInsideBenchmarking(sourcePath, "Program source path");
        ensureFileExists(sourcePath, "Program source");
        append(logs, "Resolved Java source path: " + toDisplayPath(sourcePath));

        String sourceCode = readFile(sourcePath);
        if (sourceCode.isBlank()) {
            throw new IllegalStateException("Loaded source code is empty: " + toDisplayPath(sourcePath));
        }
        append(logs, "Source code loaded and returned to editor.");

        NuteraCaseSourceResponse response = new NuteraCaseSourceResponse();
        response.setStatus("SUCCESS");
        response.setMessage("Case source loaded.");
        response.setDataset(datasetKey);
        response.setCsvPath(toDisplayPath(csvPath));
        response.setEntryIndex(index);
        response.setTotalEntries(entries.size());
        response.setProgramClass(selected.className);
        response.setProgramFunction(selected.functionName);
        response.setProgramBinaryPath(toDisplayPath(binaryPath));
        response.setProgramPath(toDisplayPath(sourcePath));
        response.setCode(sourceCode);
        response.setLog(String.join("\n", logs));
        return response;
    }

    private List<ProgramEntry> loadEntries(Path csvPath) {
        try {
            List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            if (lines.size() < 2) {
                return List.of();
            }

            List<String> header = parseCsvLine(lines.get(0));
            int fileIndex = findHeaderIndex(header, "File");
            int classIndex = findHeaderIndex(header, "Class");
            int functionIndex = findHeaderIndex(header, "Function");

            List<ProgramEntry> entries = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                List<String> columns = parseCsvLine(line);
                String fileValue = columnValue(columns, fileIndex);
                if (fileValue.isBlank()) {
                    continue;
                }
                entries.add(new ProgramEntry(
                        fileValue,
                        columnValue(columns, classIndex),
                        columnValue(columns, functionIndex)
                ));
            }
            return entries;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse CSV: " + toDisplayPath(csvPath) + ", reason: " + ex.getMessage(), ex);
        }
    }

    private Path resolveProgramBinaryPath(Path csvPath, String rawProgramPath, String className, List<String> logs) {
        String raw = rawProgramPath == null ? "" : rawProgramPath.trim();
        if (raw.isBlank()) {
            throw new IllegalStateException("Program path is empty in CSV.");
        }

        String normalizedRaw = raw.replace("\\", "/");
        Set<Path> candidates = new LinkedHashSet<>();

        candidates.add(csvPath.getParent().resolve(normalizedRaw).normalize());
        candidates.add(benchmarkingRoot.resolve(normalizedRaw).normalize());

        String stripped = normalizedRaw;
        while (stripped.startsWith("../")) {
            stripped = stripped.substring(3);
        }
        while (stripped.startsWith("./")) {
            stripped = stripped.substring(2);
        }
        if (!stripped.equals(normalizedRaw)) {
            candidates.add(benchmarkingRoot.resolve(stripped).normalize());
        }

        for (Path candidate : candidates) {
            if (!candidate.startsWith(benchmarkingRoot)) {
                continue;
            }
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        Path rawFileName = Path.of(normalizedRaw).getFileName();
        if (rawFileName != null) {
            Path byName = findFirstByFileName(rawFileName.toString());
            if (byName != null) {
                append(logs, "Fallback binary match by filename: " + toDisplayPath(byName));
                return byName;
            }
        }

        if (className != null && !className.isBlank()) {
            Path classJar = findFirstByFileName(className + ".jar");
            if (classJar != null) {
                append(logs, "Fallback binary match by class name: " + toDisplayPath(classJar));
                return classJar;
            }
        }

        String attempted = candidates.stream()
                .map(this::toDisplayPath)
                .collect(Collectors.joining(" | "));
        throw new IllegalStateException("Cannot resolve program binary path from CSV value '" + rawProgramPath + "'. Tried: " + attempted);
    }

    private Path resolveSourcePath(Path binaryPath, String className, List<String> logs) {
        String binaryName = binaryPath.getFileName().toString();
        String lowerName = binaryName.toLowerCase();
        Path directory = binaryPath.getParent();
        if (directory == null) {
            throw new IllegalStateException("Invalid binary path: " + binaryPath);
        }

        if (lowerName.endsWith(".java")) {
            return binaryPath;
        }

        List<Path> directCandidates = new ArrayList<>();
        if (className != null && !className.isBlank()) {
            directCandidates.add(directory.resolve(className + ".java").normalize());
        }
        int dotIndex = binaryName.lastIndexOf('.');
        if (dotIndex > 0) {
            String stem = binaryName.substring(0, dotIndex);
            directCandidates.add(directory.resolve(stem + ".java").normalize());
        }

        for (Path candidate : directCandidates) {
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".java"))
                    .sorted()
                    .collect(Collectors.toList());
            if (!javaFiles.isEmpty()) {
                append(logs, "Fallback source match by first .java in directory: " + toDisplayPath(javaFiles.get(0)));
                return javaFiles.get(0);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to search source in directory: " + toDisplayPath(directory), ex);
        }

        if (className != null && !className.isBlank()) {
            Path classMatch = findFirstByFileName(className + ".java");
            if (classMatch != null) {
                append(logs, "Fallback source match by global class name: " + toDisplayPath(classMatch));
                return classMatch;
            }
        }

        throw new IllegalStateException("Cannot locate .java source for: " + toDisplayPath(binaryPath));
    }

    private Path findFirstByFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(benchmarkingRoot, 10)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase(fileName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read source file: " + toDisplayPath(path), ex);
        }
    }

    private void validatePathInsideBenchmarking(Path path, String label) {
        if (!path.startsWith(benchmarkingRoot)) {
            throw new IllegalArgumentException(label + " is outside benchmarking workspace: " + path);
        }
    }

    private void ensureFileExists(Path path, String label) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalStateException(label + " does not exist: " + toDisplayPath(path));
        }
    }

    private int findHeaderIndex(List<String> header, String target) {
        for (int i = 0; i < header.size(); i++) {
            if (target.equalsIgnoreCase(header.get(i).trim())) {
                return i;
            }
        }
        throw new IllegalStateException("Missing CSV column: " + target);
    }

    private String columnValue(List<String> columns, int index) {
        if (index < 0 || index >= columns.size()) {
            return "";
        }
        return columns.get(index).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        if (line == null) {
            return values;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private void append(List<String> logs, String message) {
        logs.add("[" + Instant.now() + "] " + message);
    }

    private String normalizeDataset(String dataset) {
        if (dataset == null || dataset.trim().isEmpty()) {
            throw new IllegalArgumentException("Dataset is required.");
        }
        String raw = dataset.trim();
        if ("none".equalsIgnoreCase(raw)) {
            return "NONE";
        }
        for (String key : DATASET_TO_CSV.keySet()) {
            if (key.equalsIgnoreCase(raw)) {
                return key;
            }
        }
        return raw;
    }

    private String toDisplayPath(Path path) {
        try {
            return projectRoot.relativize(path).toString().replace("\\", "/");
        } catch (Exception ignored) {
            return path.toString().replace("\\", "/");
        }
    }

    private static Map<String, String> buildDatasetMap() {
        Map<String, String> map = new HashMap<>();
        map.put("ALL", "runtimes/nutera/benchmarking/problem-sets/six_sets.csv");
        map.put("Cft", "runtimes/nutera/benchmarking/problem-sets/termination_crafted_set.csv");
        map.put("C-Lit", "runtimes/nutera/benchmarking/problem-sets/termination_crafted_lit_set.csv");
        map.put("Num", "runtimes/nutera/benchmarking/problem-sets/termination_numeric_set.csv");
        map.put("R-15", "runtimes/nutera/benchmarking/problem-sets/termination_restricted_15_set.csv");
        map.put("NLA", "runtimes/nutera/benchmarking/problem-sets/termination_nla_set.csv");
        map.put("NAdv", "runtimes/nutera/benchmarking/problem-sets/nuTerm_advantage_set.csv");
        return map;
    }

    private Path resolveProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(current.resolve("runtimes"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("runtimes"))) {
            return parent;
        }
        return current;
    }

    private Path resolveRuntimeRoot(String configuredRuntimePath, Path baseRoot) {
        Path configured = Path.of(configuredRuntimePath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Path direct = baseRoot.resolve(configured).normalize();
        if (Files.exists(direct)) {
            return direct;
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path fallback = current.resolve(configured).normalize();
        if (Files.exists(fallback)) {
            return fallback;
        }

        return direct;
    }

    private static class ProgramEntry {
        private final String rawProgramPath;
        private final String className;
        private final String functionName;

        private ProgramEntry(String rawProgramPath, String className, String functionName) {
            this.rawProgramPath = rawProgramPath;
            this.className = className;
            this.functionName = functionName;
        }
    }
}
