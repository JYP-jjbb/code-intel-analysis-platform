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
    private static final Pattern DECL_OR_ASSIGN_PATTERN = Pattern.compile("^(?:[A-Za-z_][A-Za-z0-9_<>\\[\\]]*\\s+)*([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)$");
    private static final Pattern COMPOUND_ASSIGN_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*([+\\-*/%])=\\s*(.+)$");
    private static final Pattern INC_DEC_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*(\\+\\+|--)$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> IDENTIFIER_STOPWORDS = Set.of(
            "for", "while", "do", "if", "else", "switch", "case", "return",
            "int", "long", "short", "float", "double", "char", "bool", "boolean",
            "void", "class", "public", "private", "protected", "static", "new",
            "true", "false", "null", "and", "or", "not", "max", "min", "relu"
    );

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
        Set<String> loopGuardTokens = new LinkedHashSet<>();
        Set<String> positiveGuardTokens = new LinkedHashSet<>();
        String verificationStatus = resolveVerificationStatus(request);
        int lastLoopLine = -1;

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
                loopGuardTokens.addAll(parseStructureTokens(trimmed));
                positiveGuardTokens.addAll(parsePositiveGuardTokens(trimmed));
                lastLoopLine = line;
                nodes.add(newNode(
                        "loop-" + line,
                        clipLabel(trimmed),
                        "loop_guard",
                        line,
                        line,
                        resolveLoopStatus(trimmed),
                        "循环守卫条件，直接影响终止性证明。"
                ));
                continue;
            }
            if (CONDITION_PATTERN.matcher(trimmed).find()) {
                boolean conditionRelated = containsCandidateToken(trimmed, candidateTokens)
                        || containsCandidateToken(trimmed, loopGuardTokens);
                nodes.add(newNode(
                        "cond-" + line,
                        clipLabel(trimmed),
                        "condition",
                        line,
                        line,
                        resolveConditionStatus(trimmed, verificationStatus, conditionRelated),
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
                        "normal",
                        "返回语句作为背景结构节点，不直接代表证明支持。"
                ));
                continue;
            }
            if (ASSIGNMENT_PATTERN.matcher(trimmed).find()) {
                UpdateSemantic semantic = resolveUpdateSemantic(
                        trimmed,
                        line,
                        lastLoopLine,
                        verificationStatus,
                        candidateTokens,
                        loopGuardTokens,
                        positiveGuardTokens
                );
                nodes.add(newNode(
                        "upd-" + line,
                        clipLabel(trimmed),
                        semantic.type,
                        line,
                        line,
                        semantic.status,
                        semantic.explanation
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
                        "normal",
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
                            edgeStatusByNode(node, candidate)
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
            if ("focus".equals(status)
                    || "highlight".equals(status)
                    || "high-risk".equals(status)
                    || "support".equals(status)
                    || "unsupport".equals(status)
                    || "input".equals(status)) {
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
        insight.setHighlightExplanation("灰蓝表示普通结构，浅蓝表示输入初始化/前置供给节点，绿色表示直接支撑终止性证明链条，红色表示当前不支持证明的关键位置。");
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
            focusRange = "焦点行 " + focusLines.get(0) + (focusLines.size() > 1 ? ("-" + focusLines.get(focusLines.size() - 1)) : "");
        }
        return "验证状态: " + status + "\n"
                + "候选函数: " + nonBlankOrFallback(request.getCandidateFunction(), "（空）") + "\n"
                + "结构概览: 循环节点 " + loopCount + " 个，变量更新节点 " + updateCount + " 个。\n"
                + focusRange + "\n"
                + "说明: 图面板会高亮循环守卫、条件分支与变量更新关系，可结合代码切片定位证明关键点。";
    }

    private int findFirstNonBlankLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (!safe(lines.get(i)).trim().isBlank()) {
                return i + 1;
            }
        }
        return 1;
    }

    private String resolveLoopStatus(String loopLine) {
        String text = safe(loopLine).toLowerCase(Locale.ROOT);
        if (text.contains("while") || text.contains("for")) {
            return "support";
        }
        return "normal";
    }

    private String resolveConditionStatus(String conditionLine, String verificationStatus, boolean conditionRelated) {
        if (!conditionRelated) {
            return "normal";
        }
        String text = safe(conditionLine).toLowerCase(Locale.ROOT);
        if (text.contains("> 0") || text.contains(">= 1")) {
            return "support";
        }
        String normalized = safe(verificationStatus).toUpperCase(Locale.ROOT);
        if ("NOT_PROVED".equals(normalized) || "STOP".equals(normalized)) {
            return "input";
        }
        return "normal";
    }

    private UpdateSemantic resolveUpdateSemantic(String line,
                                                 int lineNumber,
                                                 int lastLoopLine,
                                                 String verificationStatus,
                                                 Set<String> candidateTokens,
                                                 Set<String> loopGuardTokens,
                                                 Set<String> positiveGuardTokens) {
        AssignmentInfo assignment = parseAssignmentInfo(line);
        boolean inLoopNeighborhood = lastLoopLine > 0 && lineNumber > lastLoopLine && (lineNumber - lastLoopLine) <= 10;
        boolean candidateRelated = containsCandidateToken(line, candidateTokens);
        boolean guardRelated = containsCandidateToken(line, loopGuardTokens);
        boolean inputInit = looksLikeInputInitialization(assignment, lineNumber, lastLoopLine);
        boolean terminationSupport = looksLikeTerminationSupportUpdate(assignment, inLoopNeighborhood, loopGuardTokens, positiveGuardTokens);

        if (inputInit) {
            return new UpdateSemantic(
                    "variable_update",
                    "input",
                    "输入初始化/前置数据供给节点，为循环与候选函数提供初始值。"
            );
        }
        if (terminationSupport) {
            return new UpdateSemantic(
                    "candidate_update",
                    "support",
                    "该更新语句直接推动循环变量下降，构成终止性证明关键链条。"
            );
        }

        String normalizedVerification = safe(verificationStatus).toUpperCase(Locale.ROOT);
        boolean modifiesGuardVariable = assignment != null && containsToken(loopGuardTokens, assignment.left);
        if (inLoopNeighborhood && (modifiesGuardVariable || (guardRelated && candidateRelated))) {
            if ("NOT_PROVED".equals(normalizedVerification) || "STOP".equals(normalizedVerification)) {
                return new UpdateSemantic(
                        candidateRelated ? "candidate_update" : "variable_update",
                        "unsupport",
                        "该更新当前不支持终止性证明，可能导致证明无法闭合。"
                );
            }
            return new UpdateSemantic(
                    candidateRelated ? "candidate_update" : "variable_update",
                    "input",
                    "该更新与守卫/候选函数相关，但暂不构成明确下降证据。"
            );
        }

        if (inLoopNeighborhood && guardRelated) {
            return new UpdateSemantic(
                    candidateRelated ? "candidate_update" : "variable_update",
                    "input",
                    "循环内部的关联更新节点，作为证明链条的辅助上下文。"
            );
        }
        return new UpdateSemantic(
                candidateRelated ? "candidate_update" : "variable_update",
                "normal",
                "普通变量更新节点，用于补全程序结构上下文。"
        );
    }

    private AssignmentInfo parseAssignmentInfo(String line) {
        String text = safe(line).trim();
        if (text.endsWith(";")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        Matcher incDec = INC_DEC_PATTERN.matcher(text);
        if (incDec.matches()) {
            return new AssignmentInfo(incDec.group(1), incDec.group(2), "1");
        }
        Matcher compound = COMPOUND_ASSIGN_PATTERN.matcher(text);
        if (compound.matches()) {
            return new AssignmentInfo(compound.group(1), compound.group(2) + "=", compound.group(3));
        }
        Matcher assign = DECL_OR_ASSIGN_PATTERN.matcher(text);
        if (assign.matches()) {
            return new AssignmentInfo(assign.group(1), "=", assign.group(2));
        }
        return null;
    }

    private boolean looksLikeInputInitialization(AssignmentInfo assignment, int lineNumber, int lastLoopLine) {
        if (assignment == null) {
            return false;
        }
        if (lastLoopLine > 0 && lineNumber >= lastLoopLine) {
            return false;
        }
        String rhs = safe(assignment.right).toLowerCase(Locale.ROOT);
        return rhs.contains("args[")
                || rhs.contains("argv[")
                || rhs.contains("scanner")
                || rhs.contains("stdin")
                || rhs.contains("getline")
                || rhs.contains("readline")
                || rhs.contains("input(");
    }

    private boolean looksLikeTerminationSupportUpdate(AssignmentInfo assignment,
                                                      boolean inLoopNeighborhood,
                                                      Set<String> loopGuardTokens,
                                                      Set<String> positiveGuardTokens) {
        if (assignment == null || !inLoopNeighborhood || loopGuardTokens.isEmpty()) {
            return false;
        }
        String lhs = safe(assignment.left);
        if (!containsToken(loopGuardTokens, lhs)) {
            return false;
        }
        String op = safe(assignment.operator);
        String rhs = safe(assignment.right);
        String rhsLower = rhs.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if ("--".equals(op)) {
            return true;
        }
        if ("-=".equals(op)) {
            return isPositiveRhs(rhsLower, positiveGuardTokens);
        }
        if (!"=".equals(op)) {
            return false;
        }
        if (rhsLower.matches("^" + Pattern.quote(lhs.toLowerCase(Locale.ROOT)) + "-.+")) {
            String delta = rhsLower.substring(lhs.length() + 1);
            return isPositiveRhs(delta, positiveGuardTokens);
        }
        return false;
    }

    private boolean isPositiveRhs(String rhs, Set<String> positiveGuardTokens) {
        String value = safe(rhs).trim();
        if (value.isBlank()) {
            return false;
        }
        if (value.matches("^\\d+(?:\\.\\d+)?$")) {
            return Double.parseDouble(value) > 0;
        }
        if (positiveGuardTokens.contains(value)) {
            return true;
        }
        for (String token : positiveGuardTokens) {
            if (value.matches("^" + Pattern.quote(token.toLowerCase(Locale.ROOT)) + "$")) {
                return true;
            }
        }
        return false;
    }

    private String edgeStatusByNode(NuteraVerificationSummaryResponse.GraphNode from,
                                    NuteraVerificationSummaryResponse.GraphNode to) {
        String fromStatus = safe(from.getStatus()).toLowerCase(Locale.ROOT);
        String toStatus = safe(to.getStatus()).toLowerCase(Locale.ROOT);
        if ("high-risk".equals(fromStatus)
                || "high-risk".equals(toStatus)
                || "unsupport".equals(fromStatus)
                || "unsupport".equals(toStatus)) {
            return "risk";
        }
        if ("support".equals(fromStatus) || "support".equals(toStatus)) {
            return "support";
        }
        if ("focus".equals(fromStatus)
                || "focus".equals(toStatus)
                || "input".equals(fromStatus)
                || "input".equals(toStatus)) {
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
            if (IDENTIFIER_STOPWORDS.contains(normalized)) {
                continue;
            }
            tokens.add(normalized);
        }
        return tokens;
    }

    private Set<String> parseStructureTokens(String expression) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER_PATTERN.matcher(safe(expression));
        while (matcher.find()) {
            String token = matcher.group();
            String normalized = token.toLowerCase(Locale.ROOT);
            if (IDENTIFIER_STOPWORDS.contains(normalized)) {
                continue;
            }
            tokens.add(normalized);
        }
        return tokens;
    }

    private Set<String> parsePositiveGuardTokens(String expression) {
        Set<String> tokens = new LinkedHashSet<>();
        String text = safe(expression);
        Matcher positiveMatcher = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:>|>=)\\s*(?:0|1)").matcher(text);
        while (positiveMatcher.find()) {
            tokens.add(positiveMatcher.group(1).toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private boolean containsToken(Set<String> tokens, String token) {
        if (tokens == null || tokens.isEmpty()) {
            return false;
        }
        String normalized = safe(token).toLowerCase(Locale.ROOT);
        return !normalized.isBlank() && tokens.contains(normalized);
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

    private static final class UpdateSemantic {
        private final String type;
        private final String status;
        private final String explanation;

        private UpdateSemantic(String type, String status, String explanation) {
            this.type = type;
            this.status = status;
            this.explanation = explanation;
        }
    }

    private static final class AssignmentInfo {
        private final String left;
        private final String operator;
        private final String right;

        private AssignmentInfo(String left, String operator, String right) {
            this.left = left == null ? "" : left.trim();
            this.operator = operator == null ? "" : operator.trim();
            this.right = right == null ? "" : right.trim();
        }
    }
}
