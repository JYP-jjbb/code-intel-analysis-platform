package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraVerificationSummaryRequest;
import com.course.ideology.api.dto.NuteraVerificationSummaryResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NuteraVerificationSummaryService {
    private static final Pattern LOOP_PATTERN = Pattern.compile("\\b(for|while|do)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\b(if|else\\s+if|elif|switch|case)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETURN_PATTERN = Pattern.compile("\\breturn\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGNMENT_PATTERN = Pattern.compile("(?<![=!<>])=(?!=)|\\+=|-=|\\*=|/=|%=|\\+\\+|--");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public NuteraVerificationSummaryResponse buildSummary(NuteraVerificationSummaryRequest request) {
        String source = safe(request.getCode());
        List<String> lines = splitLines(source);
        int selectedLine = normalizeLine(request.getSelectedLine(), lines.size());

        List<NuteraVerificationSummaryResponse.GraphNode> nodes = buildNodes(lines, request, selectedLine);
        List<NuteraVerificationSummaryResponse.GraphEdge> edges = buildEdges(nodes);
        List<Integer> focusLines = collectFocusLines(nodes);
        if (focusLines.isEmpty()) {
            focusLines.add(selectedLine);
        }

        NuteraVerificationSummaryResponse.SliceData slice = buildSlice(lines, focusLines, selectedLine);
        NuteraVerificationSummaryResponse.VerificationInsight insight = buildInsight(request, focusLines);

        NuteraVerificationSummaryResponse response = new NuteraVerificationSummaryResponse();
        response.setStatus("SUCCESS");
        response.setMessage("验证可视化摘要已生成。");
        response.setSummaryText(buildSummaryText(request, focusLines, nodes));
        response.setVerificationStatus(resolveVerificationStatus(request));
        response.setCandidateFunction(safe(request.getCandidateFunction()));
        response.setFocusLines(focusLines);

        NuteraVerificationSummaryResponse.GraphData graph = new NuteraVerificationSummaryResponse.GraphData();
        graph.setNodes(nodes);
        graph.setEdges(edges);
        response.setGraph(graph);
        response.setSlice(slice);
        response.setInsight(insight);
        return response;
    }

    private List<NuteraVerificationSummaryResponse.GraphNode> buildNodes(List<String> lines,
                                                                         NuteraVerificationSummaryRequest request,
                                                                         int selectedLine) {
        List<NuteraVerificationSummaryResponse.GraphNode> nodes = new ArrayList<>();
        Set<String> candidateTokens = parseCandidateTokens(request.getCandidateFunction());
        String verificationStatus = resolveVerificationStatus(request);

        int entryLine = findFirstNonBlankLine(lines);
        nodes.add(newNode(
                "entry-" + entryLine,
                "函数入口",
                "function_entry",
                entryLine,
                entryLine,
                "normal",
                "程序起始位置。"
        ));

        int autoIndex = 0;
        for (int i = 0; i < lines.size(); i++) {
            String raw = safe(lines.get(i));
            String trimmed = raw.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            int line = i + 1;

            if (LOOP_PATTERN.matcher(trimmed).find()) {
                nodes.add(newNode(
                        "loop-" + line,
                        clipLabel(trimmed),
                        "loop_guard",
                        line,
                        line,
                        mapFocusStatus(verificationStatus, "loop_guard", line == selectedLine),
                        "循环守卫条件，直接影响终止性证明。"
                ));
                continue;
            }
            if (CONDITION_PATTERN.matcher(trimmed).find()) {
                nodes.add(newNode(
                        "cond-" + line,
                        clipLabel(trimmed),
                        "condition",
                        line,
                        line,
                        line == selectedLine ? "focus" : "normal",
                        "分支判断节点，可能改变循环路径与变量约束。"
                ));
                continue;
            }
            if (RETURN_PATTERN.matcher(trimmed).find()) {
                nodes.add(newNode(
                        "ret-" + line,
                        clipLabel(trimmed),
                        "return",
                        line,
                        line,
                        "support",
                        "返回语句，常作为终止路径证据。"
                ));
                continue;
            }
            if (ASSIGNMENT_PATTERN.matcher(trimmed).find()) {
                boolean candidateRelated = containsCandidateToken(trimmed, candidateTokens);
                String status = candidateRelated
                        ? mapFocusStatus(verificationStatus, "candidate_update", line == selectedLine)
                        : (line == selectedLine ? "focus" : "normal");
                String explanation = candidateRelated
                        ? "该变量更新与候选函数变量相关，需重点关注。"
                        : "变量更新节点，可能影响 ranking function 单调性。";
                nodes.add(newNode(
                        "upd-" + line,
                        clipLabel(trimmed),
                        candidateRelated ? "candidate_update" : "variable_update",
                        line,
                        line,
                        status,
                        explanation
                ));
                continue;
            }

            if (line == selectedLine || autoIndex < 2) {
                nodes.add(newNode(
                        "stmt-" + line,
                        clipLabel(trimmed),
                        "statement",
                        line,
                        line,
                        line == selectedLine ? "focus" : "normal",
                        "普通语句节点，用于补全结构上下文。"
                ));
                autoIndex += 1;
            }
        }

        nodes.sort((a, b) -> {
            int cmp = Integer.compare(a.getLineStart(), b.getLineStart());
            if (cmp != 0) {
                return cmp;
            }
            return a.getId().compareToIgnoreCase(b.getId());
        });

        if (nodes.size() > 22) {
            return new ArrayList<>(nodes.subList(0, 22));
        }
        return nodes;
    }

    private List<NuteraVerificationSummaryResponse.GraphEdge> buildEdges(List<NuteraVerificationSummaryResponse.GraphNode> nodes) {
        List<NuteraVerificationSummaryResponse.GraphEdge> edges = new ArrayList<>();
        for (int i = 0; i < nodes.size() - 1; i++) {
            NuteraVerificationSummaryResponse.GraphNode from = nodes.get(i);
            NuteraVerificationSummaryResponse.GraphNode to = nodes.get(i + 1);
            edges.add(newEdge(
                    from.getId(),
                    to.getId(),
                    "syntax_flow",
                    edgeStatusByNode(from, to)
            ));
        }

        for (int i = 0; i < nodes.size(); i++) {
            NuteraVerificationSummaryResponse.GraphNode node = nodes.get(i);
            if (!"loop_guard".equalsIgnoreCase(node.getType()) && !"condition".equalsIgnoreCase(node.getType())) {
                continue;
            }
            for (int j = i + 1; j < nodes.size(); j++) {
                NuteraVerificationSummaryResponse.GraphNode candidate = nodes.get(j);
                int lineGap = candidate.getLineStart() - node.getLineStart();
                if (lineGap > 8) {
                    break;
                }
                if ("variable_update".equalsIgnoreCase(candidate.getType())
                        || "candidate_update".equalsIgnoreCase(candidate.getType())) {
                    edges.add(newEdge(
                            node.getId(),
                            candidate.getId(),
                            "control_dep",
                            "highlight"
                    ));
                }
            }
        }
        return edges;
    }

    private List<Integer> collectFocusLines(List<NuteraVerificationSummaryResponse.GraphNode> nodes) {
        Set<Integer> lines = new LinkedHashSet<>();
        for (NuteraVerificationSummaryResponse.GraphNode node : nodes) {
            String status = safe(node.getStatus()).toLowerCase(Locale.ROOT);
            if ("focus".equals(status) || "highlight".equals(status) || "high-risk".equals(status) || "support".equals(status)) {
                for (int line = node.getLineStart(); line <= node.getLineEnd(); line++) {
                    lines.add(line);
                }
            }
        }
        return new ArrayList<>(lines);
    }

    private NuteraVerificationSummaryResponse.SliceData buildSlice(List<String> lines,
                                                                   List<Integer> focusLines,
                                                                   int selectedLine) {
        NuteraVerificationSummaryResponse.SliceData slice = new NuteraVerificationSummaryResponse.SliceData();
        List<Integer> selected = new ArrayList<>();
        if (!focusLines.isEmpty()) {
            selected.addAll(focusLines);
        }
        if (selected.isEmpty()) {
            selected.add(selectedLine);
        }
        int min = selected.stream().mapToInt(Integer::intValue).min().orElse(selectedLine);
        int max = selected.stream().mapToInt(Integer::intValue).max().orElse(selectedLine);
        int from = Math.max(1, min - 1);
        int to = Math.min(lines.size(), max + 1);
        List<Integer> sliceLines = new ArrayList<>();
        StringBuilder codeBuilder = new StringBuilder();
        for (int line = from; line <= to; line++) {
            sliceLines.add(line);
            codeBuilder.append(lines.get(line - 1));
            if (line < to) {
                codeBuilder.append("\n");
            }
        }
        slice.setLines(sliceLines);
        slice.setCode(codeBuilder.toString());
        return slice;
    }

    private NuteraVerificationSummaryResponse.VerificationInsight buildInsight(NuteraVerificationSummaryRequest request,
                                                                               List<Integer> focusLines) {
        NuteraVerificationSummaryResponse.VerificationInsight insight = new NuteraVerificationSummaryResponse.VerificationInsight();
        String target = focusLines.isEmpty()
                ? "当前程序主流程"
                : "关键行 " + focusLines.get(0) + (focusLines.size() > 1 ? (" ~ " + focusLines.get(focusLines.size() - 1)) : "");
        insight.setTarget(target);
        insight.setCheckerConclusion(nonBlankOrFallback(request.getCheckerConclusion(), request.getCheckerVerdict(), "UNKNOWN"));
        insight.setProofOutcome(resolveVerificationStatus(request));
        String checkerMessage = safe(request.getCheckerMessage());
        insight.setFailureReason(checkerMessage.isBlank() ? "无" : checkerMessage);
        insight.setHighlightExplanation("橙色/红色节点表示当前验证重点与潜在风险位置，绿色表示支持证明的关键路径。");
        return insight;
    }

    private String buildSummaryText(NuteraVerificationSummaryRequest request,
                                    List<Integer> focusLines,
                                    List<NuteraVerificationSummaryResponse.GraphNode> nodes) {
        String status = resolveVerificationStatus(request);
        long loopCount = nodes.stream().filter(item -> "loop_guard".equalsIgnoreCase(item.getType())).count();
        long updateCount = nodes.stream().filter(item -> item.getType().toLowerCase(Locale.ROOT).contains("update")).count();
        String focusRange;
        if (focusLines.isEmpty()) {
            focusRange = "未检测到显式焦点行，已退化为主流程结构展示。";
        } else {
            focusRange = "焦点行: " + focusLines.get(0) + (focusLines.size() > 1 ? ("-" + focusLines.get(focusLines.size() - 1)) : "");
        }
        return "验证状态: " + status + "\n"
                + "候选函数: " + nonBlankOrFallback(request.getCandidateFunction(), "（空）") + "\n"
                + "结构概览: 循环节点 " + loopCount + " 个，变量更新节点 " + updateCount + " 个。\n"
                + focusRange + "\n"
                + "说明: 图面板已高亮循环守卫、条件分支与变量更新关系，可结合代码切片定位证明关键点。";
    }

    private int findFirstNonBlankLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (!safe(lines.get(i)).trim().isBlank()) {
                return i + 1;
            }
        }
        return 1;
    }

    private String mapFocusStatus(String verificationStatus, String nodeType, boolean selected) {
        if (selected) {
            return "focus";
        }
        String normalized = safe(verificationStatus).toUpperCase(Locale.ROOT);
        if ("PROVED".equals(normalized)) {
            return "candidate_update".equalsIgnoreCase(nodeType) ? "support" : "focus";
        }
        if ("NOT_PROVED".equals(normalized) || "STOP".equals(normalized)) {
            return "candidate_update".equalsIgnoreCase(nodeType) ? "high-risk" : "focus";
        }
        return "focus";
    }

    private String edgeStatusByNode(NuteraVerificationSummaryResponse.GraphNode from,
                                    NuteraVerificationSummaryResponse.GraphNode to) {
        String fromStatus = safe(from.getStatus()).toLowerCase(Locale.ROOT);
        String toStatus = safe(to.getStatus()).toLowerCase(Locale.ROOT);
        if ("high-risk".equals(fromStatus) || "high-risk".equals(toStatus)) {
            return "risk";
        }
        if ("focus".equals(fromStatus) || "focus".equals(toStatus)
                || "support".equals(fromStatus) || "support".equals(toStatus)) {
            return "highlight";
        }
        return "normal";
    }

    private boolean containsCandidateToken(String line, Set<String> candidateTokens) {
        if (candidateTokens.isEmpty()) {
            return false;
        }
        String normalized = safe(line);
        for (String token : candidateTokens) {
            if (normalized.matches("(?i).*\\b" + Pattern.quote(token) + "\\b.*")) {
                return true;
            }
        }
        return false;
    }

    private Set<String> parseCandidateTokens(String candidateFunction) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER_PATTERN.matcher(safe(candidateFunction));
        while (matcher.find()) {
            String token = matcher.group();
            String normalized = token.toLowerCase(Locale.ROOT);
            if (normalized.length() <= 1) {
                continue;
            }
            if (normalized.equals("relu")
                    || normalized.equals("max")
                    || normalized.equals("min")
                    || normalized.equals("and")
                    || normalized.equals("or")
                    || normalized.equals("not")) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private NuteraVerificationSummaryResponse.GraphNode newNode(String id,
                                                                String label,
                                                                String type,
                                                                int lineStart,
                                                                int lineEnd,
                                                                String status,
                                                                String explanation) {
        NuteraVerificationSummaryResponse.GraphNode node = new NuteraVerificationSummaryResponse.GraphNode();
        node.setId(id);
        node.setLabel(label);
        node.setType(type);
        node.setLineStart(Math.max(1, lineStart));
        node.setLineEnd(Math.max(lineStart, lineEnd));
        node.setStatus(status);
        node.setExplanation(explanation);
        return node;
    }

    private NuteraVerificationSummaryResponse.GraphEdge newEdge(String source,
                                                                String target,
                                                                String type,
                                                                String status) {
        NuteraVerificationSummaryResponse.GraphEdge edge = new NuteraVerificationSummaryResponse.GraphEdge();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setType(type);
        edge.setStatus(status);
        return edge;
    }

    private List<String> splitLines(String code) {
        String text = safe(code);
        String[] rows = text.split("\\R", -1);
        List<String> lines = new ArrayList<>();
        for (String row : rows) {
            lines.add(row == null ? "" : row);
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private int normalizeLine(Integer line, int maxLine) {
        int value = line == null ? 1 : line;
        if (maxLine <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(maxLine, value));
    }

    private String resolveVerificationStatus(NuteraVerificationSummaryRequest request) {
        String conclusion = safe(request.getCheckerConclusion()).toUpperCase(Locale.ROOT);
        if ("YES".equals(conclusion) || "PROVED".equals(conclusion)) {
            return "PROVED";
        }
        if ("NO".equals(conclusion) || "NOT_PROVED".equals(conclusion)) {
            return "NOT_PROVED";
        }
        String verdict = safe(request.getCheckerVerdict()).toUpperCase(Locale.ROOT);
        if ("PROVED".equals(verdict)) {
            return "PROVED";
        }
        if ("NOT_PROVED".equals(verdict) || "DISPROVED".equals(verdict)) {
            return "NOT_PROVED";
        }
        String status = safe(request.getCheckerStatus()).toUpperCase(Locale.ROOT);
        if ("STOP".equals(status) || "PAUSED".equals(status)) {
            return "STOP";
        }
        if ("COMPLETED".equals(status)) {
            return "NOT_PROVED";
        }
        return "NOT_PROVED";
    }

    private String clipLabel(String source) {
        String value = safe(source).trim();
        if (value.length() <= 44) {
            return value;
        }
        return value.substring(0, 44) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String nonBlankOrFallback(String first, String second, String fallback) {
        String a = safe(first).trim();
        if (!a.isBlank()) {
            return a;
        }
        String b = safe(second).trim();
        if (!b.isBlank()) {
            return b;
        }
        return fallback;
    }

    private String nonBlankOrFallback(String first, String fallback) {
        String a = safe(first).trim();
        return a.isBlank() ? fallback : a;
    }
}

