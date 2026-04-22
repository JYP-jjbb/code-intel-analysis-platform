package com.course.ideology.executor;

import com.course.ideology.api.dto.CodeReviewTaskRequest;
import com.course.ideology.api.dto.CodeReviewTaskResultResponse;
import com.course.ideology.service.SiliconFlowChatService;
import com.course.ideology.storage.WorkspaceManager;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskRepository;
import com.course.ideology.task.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class CodeReviewExecutor {
    private static final Set<String> REVIEW_EXT = Set.of("java","kt","kts","py","js","jsx","ts","tsx","vue","go","rs","c","cc","cpp","h","hpp","cs","swift","scala","sql","xml","yml","yaml","json","md","toml","properties","gradle");
    private static final Set<String> IGNORE_DIR = Set.of(".git","node_modules","dist","build","target","out","coverage","__pycache__",".idea",".vscode");
    private static final int MAX_FILES = 24;
    private static final int MAX_FILE_CHARS = 3200;
    private static final int MAX_TREE = 2600;

    private final TaskRepository taskRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final SiliconFlowChatService chatService;

    public CodeReviewExecutor(TaskRepository taskRepository, WorkspaceManager workspaceManager, ObjectMapper objectMapper, SiliconFlowChatService chatService) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
        this.chatService = chatService;
    }

    public void execute(TaskRecord record, CodeReviewTaskRequest request) {
        Path logPath = workspaceManager.resolveTaskFile(record.getTaskId(), "run.log");
        Path resultPath = workspaceManager.resolveTaskFile(record.getTaskId(), "result.json");
        List<CodeReviewTaskResultResponse.TimelineLog> timeline = new ArrayList<>();
        Instant startedAt = Instant.now();
        record.updateStatus(TaskStatus.RUNNING, "工程审查进行中");
        taskRepository.save(record);

        try {
            // 真实审查链路入口：先记录任务上下文，避免前端/报告中心只能看到空日志。
            appendLog(logPath, "TaskId: " + record.getTaskId());
            appendLog(logPath, "Repo: " + safe(request.getRepoUrl()));
            appendLog(logPath, "LocalFolder: " + safe(request.getLocalFolder()));
            appendLog(logPath, "Model: " + safe(request.getModel()));

            stage(logPath, timeline, "SCAN", "RUNNING", "读取项目树与文件统计", 12);
            Path root = resolveRoot(request.getLocalFolder());
            if (root == null) {
                CodeReviewTaskResultResponse fallback = buildFallback(record, request, "未找到可审查目录，请先下载仓库", timeline, startedAt);
                write(resultPath, fallback);
                record.updateStatus(TaskStatus.SUCCESS, "已输出兜底审查结果");
                taskRepository.save(record);
                return;
            }
            Scan scan = scan(root);
            stage(logPath, timeline, "SCAN", "DONE", "扫描完成：总文件 " + scan.totalFiles + "，可分析 " + scan.snippets.size(), 32);

            Parsed parsed = null;
            String fallbackReason = "";
            if (scan.snippets.isEmpty()) {
                fallbackReason = "未找到可分析代码文件";
            } else if (!chatService.hasApiCredentialForModel(request.getModel(), "")) {
                fallbackReason = chatService.buildMissingCredentialMessage(request.getModel());
            } else {
                stage(logPath, timeline, "LLM", "RUNNING", "调用模型执行工程审查", 58);
                try {
                    SiliconFlowChatService.ChatResult chatResult = chatService.chatCompletion(
                            "",
                            request.getModel(),
                            buildSystemPrompt(),
                            buildUserPrompt(request, root, scan),
                            "code-review.project-audit"
                    );
                    parsed = parse(chatResult.getContent(), scan);
                    stage(logPath, timeline, "LLM", "DONE", "模型返回结构化结果，问题数 " + parsed.issues.size(), 82);
                } catch (Exception ex) {
                    fallbackReason = "模型调用失败：" + safe(ex.getMessage());
                    stage(logPath, timeline, "LLM", "FAILED", fallbackReason, 78);
                }
            }

            boolean real = parsed != null;
            if (!real && fallbackReason.isBlank()) fallbackReason = "模型结果解析失败";
            // fallback 触发条件：LLM 未调用成功、鉴权不可用、结果不合规 JSON 或无法解析。
            if (!real) stage(logPath, timeline, "FALLBACK", "RUNNING", "触发兜底：" + fallbackReason, 86);

            List<CodeReviewTaskResultResponse.ReviewIssue> issues = real ? parsed.issues : fallbackIssues(scan, fallbackReason);
            String risk = real ? normRisk(parsed.riskLevel) : inferRisk(issues);
            List<String> fix = real && !parsed.fix.isEmpty() ? parsed.fix : suggest(issues);
            String summary = real ? parsed.summary : "当前为兜底结果，原因：" + fallbackReason + "。建议修复环境后重新执行真实审查。";

            CodeReviewTaskResultResponse out = assemble(record, request, scan, issues, fix, summary, risk, real, fallbackReason, timeline, startedAt);
            stage(logPath, timeline, "REPORT", "DONE", "报告生成完成，来源=" + out.getResultSource(), 96);
            out.setTimelineLogs(new ArrayList<>(timeline));
            write(resultPath, out);

            stage(logPath, timeline, "DONE", "SUCCESS", "审查完成", 100);
            out.setTimelineLogs(new ArrayList<>(timeline));
            write(resultPath, out);
            record.updateStatus(TaskStatus.SUCCESS, real ? "工程审查完成（真实模型）" : "工程审查完成（兜底）");
            taskRepository.save(record);
        } catch (Exception ex) {
            stage(logPath, timeline, "DONE", "FAILED", "审查失败：" + safe(ex.getMessage()), 100);
            CodeReviewTaskResultResponse fail = buildFallback(record, request, "执行异常：" + safe(ex.getMessage()), timeline, startedAt);
            write(resultPath, fail);
            record.updateStatus(TaskStatus.FAILED, "工程审查失败");
            taskRepository.save(record);
        }
    }

    private CodeReviewTaskResultResponse assemble(TaskRecord record, CodeReviewTaskRequest request, Scan scan, List<CodeReviewTaskResultResponse.ReviewIssue> issues, List<String> fix, String summary, String risk, boolean real, String fallbackReason, List<CodeReviewTaskResultResponse.TimelineLog> timeline, Instant startedAt) {
        CodeReviewTaskResultResponse r = new CodeReviewTaskResultResponse();
        r.setProjectStructure(scan.tree);
        r.setIssueList(textIssues(issues));
        r.setFixSuggestions(String.join("\n", fix));
        r.setSummary(summary);
        r.setRiskLevel(risk);
        r.setFallbackResult(!real);
        r.setFallbackReason(real ? "" : fallbackReason);
        r.setResultSource(real ? "REAL_LLM" : "FALLBACK");
        r.setReviewIssue(issues);
        r.setDirectoryStats(dirStats(issues));
        List<CodeReviewTaskResultResponse.FileRiskStat> fr = fileStats(issues);
        r.setFileRiskStats(fr);
        r.setCoverageStats(coverage(scan));
        r.setLanguageStats(languageStats(scan.langCounter, scan.totalFiles));
        r.setHealthScore(health(issues, scan));
        r.setRepairBenefitStats(benefit(fr));
        r.setTimelineLogs(new ArrayList<>(timeline));

        CodeReviewTaskResultResponse.ReviewTask task = new CodeReviewTaskResultResponse.ReviewTask();
        task.setTaskId(record.getTaskId());
        task.setRepoUrl(safe(request.getRepoUrl()));
        task.setProjectPath(safe(request.getLocalFolder()));
        task.setModel(safe(request.getModel()));
        task.setStatus(real ? "SUCCESS" : "FALLBACK");
        task.setStartedAt(startedAt.toString());
        task.setFinishedAt(Instant.now().toString());
        task.setReviewInstruction("中文输出结构化工程代码审查 JSON");
        r.setReviewTask(task);

        CodeReviewTaskResultResponse.ReviewSummary s = new CodeReviewTaskResultResponse.ReviewSummary();
        s.setRiskLevel(risk);
        s.setIssueCount(issues.size());
        s.setConclusion(summary);
        s.setSource(real ? "REAL_LLM" : "FALLBACK");
        s.setFallbackResult(!real);
        s.setFallbackReason(real ? "" : fallbackReason);
        r.setReviewSummary(s);
        return r;
    }

    private CodeReviewTaskResultResponse buildFallback(TaskRecord record, CodeReviewTaskRequest request, String reason, List<CodeReviewTaskResultResponse.TimelineLog> timeline, Instant startedAt) {
        Scan empty = new Scan();
        empty.tree = "";
        empty.totalFiles = 0;
        empty.langCounter = new LinkedHashMap<>();
        empty.snippets = List.of();
        List<CodeReviewTaskResultResponse.ReviewIssue> issues = List.of(issue("审查未完整执行", "当前任务未能完成真实代码审查。", "MEDIUM", "架构设计问题", "", "", 0, 0, "请确认仓库目录和模型配置后重试。", false, reason, 0.56));
        return assemble(record, request, empty, issues, List.of("修复执行环境后重新执行真实审查"), "当前为兜底结果。", "MEDIUM", false, reason, timeline, startedAt);
    }

    private Path resolveRoot(String localFolder) {
        if (localFolder == null || localFolder.isBlank()) return null;
        try {
            Path p = Path.of(localFolder).toAbsolutePath().normalize();
            return Files.isDirectory(p) ? p : null;
        } catch (Exception ignored) { return null; }
    }

    private Scan scan(Path root) throws Exception {
        Scan s = new Scan();
        s.langCounter = new LinkedHashMap<>();
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        List<ScFile> candidates = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\','/');
            if (skipPath(rel)) continue;
            s.totalFiles++;
            String ext = ext(rel);
            String lang = lang(ext);
            s.langCounter.merge(lang, 1, Integer::sum);
            if (!REVIEW_EXT.contains(ext)) continue;
            long size = Files.size(f);
            if (size > 180000) continue;
            String content;
            try {
                content = Files.readString(f, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                // 某些仓库存在非 UTF-8 文件，跳过单文件而不是中断整次审查。
                continue;
            }
            if (content.isBlank()) continue;
            ScFile c = new ScFile();
            c.path = rel; c.dir = parent(rel); c.content = trim(content, MAX_FILE_CHARS); c.score = score(rel); c.size = size;
            candidates.add(c);
        }
        candidates.sort(Comparator.comparingInt((ScFile v)->v.score).reversed().thenComparingLong(v->v.size));
        s.snippets = candidates.stream().limit(MAX_FILES).collect(Collectors.toList());
        List<String> tree = files.stream().map(p->root.relativize(p).toString().replace('\\','/')).filter(v->!skipPath(v)).sorted().limit(MAX_TREE).collect(Collectors.toList());
        if (files.size() > MAX_TREE) tree.add("... (truncated)");
        s.tree = String.join("\n", tree);
        return s;
    }

    private String buildSystemPrompt() {
        return "你是工程代码审查专家。只输出 JSON，全部中文。字段必须包含 riskLevel、summary、issues、fixSuggestions。issues 每项包含 title、description、riskLevel、issueType、directoryPath、filePath、lineStart、lineEnd、suggestion、confidence。riskLevel 仅 HIGH/MEDIUM/LOW/INFO。issueType 仅：安全性问题、健壮性问题、可维护性问题、性能问题、代码规范问题、测试缺失问题、架构设计问题、依赖管理问题。";
    }

    private String buildUserPrompt(CodeReviewTaskRequest req, Path root, Scan scan) {
        StringBuilder sb = new StringBuilder();
        sb.append("【任务说明】请基于以下工程信息输出结构化审查 JSON。\n");
        sb.append("model=").append(safe(req.getModel())).append("\n");
        sb.append("repoUrl=").append(safe(req.getRepoUrl())).append("\n");
        sb.append("projectRoot=").append(root).append("\n");
        sb.append("totalFiles=").append(scan.totalFiles).append(" analyzedFiles=").append(scan.snippets.size()).append("\n\n");
        sb.append("【项目树摘要】\n").append(scan.tree).append("\n\n【关键文件】\n");
        int budget = 90000;
        for (ScFile f : scan.snippets) {
            String block = "### " + f.path + "\n" + f.content + "\n\n";
            if (budget - block.length() < 0) break;
            sb.append(block);
            budget -= block.length();
        }
        sb.append("请仅返回 JSON。\n");
        return sb.toString();
    }

    private Parsed parse(String raw, Scan scan) throws Exception {
        String txt = raw == null ? "" : raw.trim();
        if (txt.startsWith("```")) txt = txt.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
        int a = txt.indexOf('{'); int b = txt.lastIndexOf('}');
        if (a >= 0 && b > a) txt = txt.substring(a, b + 1);
        JsonNode root = objectMapper.readTree(txt);
        Parsed p = new Parsed();
        p.riskLevel = normRisk(text(root, "riskLevel", "风险等级"));
        p.summary = zh(text(root, "summary", "综合结论", "结论"));
        JsonNode arr = arr(root, "issues", "问题列表", "reviewIssue");
        p.issues = new ArrayList<>();
        if (arr != null) for (JsonNode n : arr) {
            String fp = text(n, "filePath", "所属文件", "file").replace('\\','/');
            CodeReviewTaskResultResponse.ReviewIssue i = issue(
                    zh(text(n, "title", "问题标题", "name")),
                    zh(text(n, "description", "中文描述", "desc")),
                    normRisk(text(n, "riskLevel", "风险等级", "risk")),
                    normType(text(n, "issueType", "问题类型", "type")),
                    fp,
                    text(n, "directoryPath", "所属目录", "directory", "dir").isBlank() ? parent(fp) : text(n, "directoryPath", "所属目录", "directory", "dir").replace('\\','/'),
                    intVal(n, "lineStart", "line", "行号开始"),
                    intVal(n, "lineEnd", "行号结束"),
                    zh(text(n, "suggestion", "修复建议", "fix")),
                    true,
                    "REAL_LLM",
                    dblVal(n, "confidence", "置信度")
            );
            if (!i.getTitle().isBlank() || !i.getDescription().isBlank()) p.issues.add(i);
        }
        if (p.issues.isEmpty()) throw new IllegalStateException("issues 为空");
        JsonNode fs = arr(root, "fixSuggestions", "修复建议");
        p.fix = new ArrayList<>();
        if (fs != null) for (JsonNode n : fs) { String t = zh(n.asText("")); if (!t.isBlank()) p.fix.add(t); }
        return p;
    }

    private List<CodeReviewTaskResultResponse.ReviewIssue> fallbackIssues(Scan scan, String reason) {
        List<CodeReviewTaskResultResponse.ReviewIssue> out = new ArrayList<>();
        boolean hasTest = scan.snippets.stream().anyMatch(f -> f.path.toLowerCase(Locale.ROOT).contains("test"));
        if (!hasTest) out.add(issue("测试覆盖不足", "工程中未发现明显测试文件。", "MEDIUM", "测试缺失问题", "", "/", 0, 0, "补齐核心模块单元测试与异常路径测试。", false, reason, 0.62));
        for (ScFile f : scan.snippets) {
            String lower = f.content.toLowerCase(Locale.ROOT);
            if (lower.contains("todo") || lower.contains("fixme")) out.add(issue("存在待办标记", "发现 TODO/FIXME，可能有遗留技术债。", "LOW", "可维护性问题", f.path, f.dir, line(f.content,"todo"), line(f.content,"todo"), "清理长期未关闭的 TODO/FIXME。", false, reason, 0.57));
            if (lower.contains("password") || lower.contains("secret")) out.add(issue("疑似敏感信息硬编码", "代码出现 password/secret 关键字。", "HIGH", "安全性问题", f.path, f.dir, line(f.content,"password"), line(f.content,"password"), "改用密钥管理或环境变量。", false, reason, 0.67));
            if (out.size() >= 14) break;
        }
        if (out.isEmpty()) out.add(issue("建议补充工程级质量门禁", "未识别高置信问题，建议补充静态检查与测试门禁。", "LOW", "架构设计问题", "", "/", 0, 0, "在 CI 增加 lint、测试和安全扫描。", false, reason, 0.55));
        return out;
    }

    private List<String> suggest(List<CodeReviewTaskResultResponse.ReviewIssue> issues) {
        LinkedHashSet<String> s = new LinkedHashSet<>();
        for (var i : issues) if (!safe(i.getSuggestion()).isBlank()) s.add(zh(i.getSuggestion()));
        if (s.isEmpty()) s.add("优先修复高风险问题，并在 CI 加入质量门禁。");
        return new ArrayList<>(s);
    }

    private List<CodeReviewTaskResultResponse.DirectoryStat> dirStats(List<CodeReviewTaskResultResponse.ReviewIssue> issues) {
        Map<String, CodeReviewTaskResultResponse.DirectoryStat> m = new LinkedHashMap<>();
        for (var i : issues) {
            String d = safe(i.getDirectoryPath()).isBlank() ? "/" : i.getDirectoryPath();
            var s = m.computeIfAbsent(d, k -> { var x = new CodeReviewTaskResultResponse.DirectoryStat(); x.setDirectoryPath(k); return x; });
            s.setIssueCount(s.getIssueCount()+1);
            switch (normRisk(i.getRiskLevel())) { case "HIGH": s.setHighCount(s.getHighCount()+1); break; case "MEDIUM": s.setMediumCount(s.getMediumCount()+1); break; case "LOW": s.setLowCount(s.getLowCount()+1); break; default: s.setInfoCount(s.getInfoCount()+1); }
        }
        return m.values().stream().sorted(Comparator.comparingInt(CodeReviewTaskResultResponse.DirectoryStat::getIssueCount).reversed()).collect(Collectors.toList());
    }

    private List<CodeReviewTaskResultResponse.FileRiskStat> fileStats(List<CodeReviewTaskResultResponse.ReviewIssue> issues) {
        Map<String, CodeReviewTaskResultResponse.FileRiskStat> m = new LinkedHashMap<>();
        for (var i : issues) {
            if (safe(i.getFilePath()).isBlank()) continue;
            var s = m.computeIfAbsent(i.getFilePath(), k -> { var x = new CodeReviewTaskResultResponse.FileRiskStat(); x.setFilePath(k); x.setDirectoryPath(parent(k)); x.setMaxRiskLevel("INFO"); x.setIssueTypes(new ArrayList<>()); return x; });
            s.setIssueCount(s.getIssueCount()+1);
            if (!s.getIssueTypes().contains(i.getIssueType())) s.getIssueTypes().add(i.getIssueType());
            if (riskW(i.getRiskLevel()) > riskW(s.getMaxRiskLevel())) s.setMaxRiskLevel(normRisk(i.getRiskLevel()));
            s.setRiskScore(round2(s.getRiskScore()+riskW(i.getRiskLevel())));
        }
        return m.values().stream().sorted(Comparator.comparingDouble(CodeReviewTaskResultResponse.FileRiskStat::getRiskScore).reversed()).limit(20).collect(Collectors.toList());
    }

    private CodeReviewTaskResultResponse.CoverageStats coverage(Scan s) {
        var c = new CodeReviewTaskResultResponse.CoverageStats();
        c.setTotalFiles(s.totalFiles); c.setAnalyzedFiles(s.snippets.size()); c.setSkippedFiles(Math.max(0,s.totalFiles-s.snippets.size()));
        c.setCoverageRate(s.totalFiles<=0?0:round2(s.snippets.size()*100.0/s.totalFiles));
        return c;
    }

    private List<CodeReviewTaskResultResponse.LanguageStat> languageStats(Map<String,Integer> counter, int total) {
        List<CodeReviewTaskResultResponse.LanguageStat> out = new ArrayList<>();
        for (var e : counter.entrySet()) { var s = new CodeReviewTaskResultResponse.LanguageStat(); s.setLanguage(e.getKey()); s.setFileCount(e.getValue()); s.setPercentage(total<=0?0:round2(e.getValue()*100.0/total)); out.add(s);} 
        out.sort(Comparator.comparingInt(CodeReviewTaskResultResponse.LanguageStat::getFileCount).reversed());
        return out;
    }

    private CodeReviewTaskResultResponse.HealthScore health(List<CodeReviewTaskResultResponse.ReviewIssue> issues, Scan scan) {
        int sec = countType(issues,"安全性问题"), robust = countType(issues,"健壮性问题"), maintain = countType(issues,"可维护性问题"), test = countType(issues,"测试缺失问题"), arch = countType(issues,"架构设计问题") + countType(issues,"依赖管理问题");
        double s1 = bound(100-sec*9-countRisk(issues,"HIGH")*4);
        double s2 = bound(100-robust*8-countRisk(issues,"MEDIUM")*1.5);
        double s3 = bound(100-maintain*7-issues.size()*1.2);
        double s4 = bound(100-test*10-(scan.snippets.isEmpty()?20:0));
        double s5 = bound(100-arch*8);
        var h = new CodeReviewTaskResultResponse.HealthScore();
        h.setSecurity(round2(s1)); h.setRobustness(round2(s2)); h.setMaintainability(round2(s3)); h.setTestingCompleteness(round2(s4)); h.setStructureClarity(round2(s5)); h.setOverall(round2((s1+s2+s3+s4+s5)/5.0));
        return h;
    }

    private List<CodeReviewTaskResultResponse.RepairBenefitStat> benefit(List<CodeReviewTaskResultResponse.FileRiskStat> files) {
        // 报告聚合逻辑：将文件风险分映射为“风险收益比”，供报告中心的修复收益图表直接消费。
        List<CodeReviewTaskResultResponse.RepairBenefitStat> out = new ArrayList<>();
        for (var f : files.stream().limit(10).collect(Collectors.toList())) {
            var b = new CodeReviewTaskResultResponse.RepairBenefitStat();
            b.setFilePath(f.getFilePath());
            b.setIssueType(f.getIssueTypes().isEmpty()?"可维护性问题":f.getIssueTypes().get(0));
            b.setRiskScore(f.getRiskScore());
            double cost = round2(Math.max(1.0, Math.min(10.0, f.getIssueCount()*1.2 + ("HIGH".equals(f.getMaxRiskLevel())?2:1))));
            b.setRepairCost(cost);
            double score = round2(f.getRiskScore()/cost);
            b.setBenefitScore(score);
            b.setBenefitLevel(score>=2.4?"HIGH":score>=1.4?"MEDIUM":"LOW");
            b.setRecommendation("优先修复该文件，风险收益比更高。");
            out.add(b);
        }
        out.sort(Comparator.comparingDouble(CodeReviewTaskResultResponse.RepairBenefitStat::getBenefitScore).reversed());
        return out;
    }

    private String textIssues(List<CodeReviewTaskResultResponse.ReviewIssue> issues) {
        List<String> l = new ArrayList<>(); int i=1;
        for (var it:issues) l.add(i++ + ". [" + normRisk(it.getRiskLevel()) + "] " + it.getTitle() + " - " + it.getDescription());
        return String.join("\n", l);
    }

    private CodeReviewTaskResultResponse.ReviewIssue issue(String title,String desc,String risk,String type,String file,String dir,int ls,int le,String sug,boolean real,String src,double conf){
        var i = new CodeReviewTaskResultResponse.ReviewIssue();
        i.setTitle(zh(title)); i.setDescription(zh(desc)); i.setRiskLevel(normRisk(risk)); i.setIssueType(normType(type)); i.setFilePath(safe(file)); i.setDirectoryPath(safe(dir).isBlank()?parent(file):safe(dir)); i.setLineStart(Math.max(ls,0)); i.setLineEnd(Math.max(le, i.getLineStart())); i.setSuggestion(zh(sug)); i.setFromRealLlm(real); i.setSourceNote(src); i.setConfidence(round2(conf<=0?0.6:conf));
        return i;
    }

    private void stage(Path logPath, List<CodeReviewTaskResultResponse.TimelineLog> tl, String stage, String status, String msg, int progress) {
        String ts = Instant.now().toString();
        appendLog(logPath, "[" + ts + "] [" + stage + "] [" + status + "] [progress=" + progress + "%] " + msg);
        var t = new CodeReviewTaskResultResponse.TimelineLog();
        t.setStage(stage); t.setStatus(status); t.setMessage(msg); t.setProgress(Math.max(0,Math.min(100,progress))); t.setTimestamp(ts); tl.add(t);
    }

    private void appendLog(Path logPath, String m) {
        try { Files.writeString(logPath, m + System.lineSeparator(), StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); } catch (Exception ignored) {}
    }
    private void write(Path p, CodeReviewTaskResultResponse r) { try { objectMapper.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), r);} catch (Exception ignored) {} }

    private boolean skipPath(String rel){ String[] seg=rel.replace('\\','/').split("/"); for(String s:seg){ if(IGNORE_DIR.contains(s.toLowerCase(Locale.ROOT))) return true; } return false; }
    private String ext(String p){ int i=p.lastIndexOf('.'); return i<0?"":p.substring(i+1).toLowerCase(Locale.ROOT); }
    private String parent(String p){ if(p==null||p.isBlank()) return "/"; int i=p.lastIndexOf('/'); return i<=0?"/":p.substring(0,i); }
    private String lang(String ext){ switch(ext){ case "java":return "Java"; case "py":return "Python"; case "js": case "jsx":return "JavaScript"; case "ts": case "tsx":return "TypeScript"; case "yml": case "yaml":return "YAML"; case "json":return "JSON"; case "md":return "Markdown"; case "xml":return "XML"; default:return ext.isBlank()?"Other":ext.toUpperCase(Locale.ROOT);} }
    private int score(String path){ String l=path.toLowerCase(Locale.ROOT); int s=0; if(l.contains("/src/")||l.startsWith("src/")) s+=6; if(l.contains("controller")) s+=6; if(l.contains("service")) s+=5; if(l.contains("security")) s+=7; if(l.contains("config")) s+=3; if(l.contains("test")) s-=2; return s; }
    private int line(String content,String keyword){ if(content==null||keyword==null) return 0; String[] lines=content.split("\\R",-1); for(int i=0;i<lines.length;i++){ if(lines[i].toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) return i+1; } return 0; }
    private String normRisk(String r){ String v=safe(r).trim().toUpperCase(Locale.ROOT); if(v.contains("高")||v.equals("CRITICAL")||v.equals("P1")) return "HIGH"; if(v.contains("中")||v.equals("P2")) return "MEDIUM"; if(v.contains("低")||v.equals("P3")) return "LOW"; return Set.of("HIGH","MEDIUM","LOW","INFO").contains(v)?v:"INFO"; }
    private String inferRisk(List<CodeReviewTaskResultResponse.ReviewIssue> issues){ if(issues.stream().anyMatch(i->"HIGH".equals(normRisk(i.getRiskLevel())))) return "HIGH"; if(issues.stream().anyMatch(i->"MEDIUM".equals(normRisk(i.getRiskLevel())))) return "MEDIUM"; if(issues.stream().anyMatch(i->"LOW".equals(normRisk(i.getRiskLevel())))) return "LOW"; return "INFO"; }
    private double riskW(String r){ switch(normRisk(r)){ case "HIGH":return 5; case "MEDIUM":return 3; case "LOW":return 1.2; default:return 0.6; } }
    private String normType(String t){ String v=safe(t).toLowerCase(Locale.ROOT); if(v.contains("安全")||v.contains("security")) return "安全性问题"; if(v.contains("健壮")||v.contains("null")||v.contains("异常")) return "健壮性问题"; if(v.contains("性能")||v.contains("performance")) return "性能问题"; if(v.contains("规范")||v.contains("style")||v.contains("lint")) return "代码规范问题"; if(v.contains("测试")||v.contains("test")) return "测试缺失问题"; if(v.contains("架构")||v.contains("design")) return "架构设计问题"; if(v.contains("依赖")||v.contains("dependency")) return "依赖管理问题"; return "可维护性问题"; }
    private int countType(List<CodeReviewTaskResultResponse.ReviewIssue> issues, String t){ return (int)issues.stream().filter(i->t.equals(i.getIssueType())).count(); }
    private int countRisk(List<CodeReviewTaskResultResponse.ReviewIssue> issues, String r){ return (int)issues.stream().filter(i->r.equals(normRisk(i.getRiskLevel()))).count(); }
    private double bound(double v){ return Math.max(20, Math.min(100,v)); }
    private double round2(double v){ return Math.round(v*100.0)/100.0; }
    private String text(JsonNode n, String...keys){ if(n==null) return ""; for(String k:keys){ JsonNode x=n.get(k); if(x!=null){ String t=x.asText("").trim(); if(!t.isBlank()) return t; }} return ""; }
    private JsonNode arr(JsonNode n, String...keys){ if(n==null) return null; for(String k:keys){ JsonNode x=n.get(k); if(x!=null&&x.isArray()) return x; } return null; }
    private int intVal(JsonNode n, String...keys){ for(String k:keys){ JsonNode x=n.get(k); if(x==null) continue; try{return x.asInt();}catch(Exception ignored){} try{return Integer.parseInt(x.asText().trim());}catch(Exception ignored){} } return 0; }
    private double dblVal(JsonNode n, String...keys){ for(String k:keys){ JsonNode x=n.get(k); if(x==null) continue; try{return x.asDouble();}catch(Exception ignored){} try{return Double.parseDouble(x.asText().trim());}catch(Exception ignored){} } return 0.72; }
    private String trim(String s, int n){ if(s==null) return ""; return s.length()<=n?s:s.substring(0,n)+"\n// [truncated]"; }
    private String zh(String s){ String t=safe(s).trim(); if(t.isBlank()) return ""; if(t.endsWith("。")||t.endsWith("！")||t.endsWith("？")) return t; if(t.endsWith(".")||t.endsWith("!")||t.endsWith("?")) return t.substring(0,t.length()-1)+"。"; return t+"。"; }
    private String safe(String s){ return s==null?"":s; }

    private static class Scan { int totalFiles; String tree=""; Map<String,Integer> langCounter=new LinkedHashMap<>(); List<ScFile> snippets=List.of(); }
    private static class ScFile { String path; String dir; String content; int score; long size; }
    private static class Parsed { String riskLevel; String summary; List<String> fix = new ArrayList<>(); List<CodeReviewTaskResultResponse.ReviewIssue> issues = new ArrayList<>(); }
}
