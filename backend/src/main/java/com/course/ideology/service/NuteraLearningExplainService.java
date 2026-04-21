package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraLearningExplainRequest;
import com.course.ideology.api.dto.NuteraLearningExplainResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class NuteraLearningExplainService {
    private final SiliconFlowChatService chatService;
    private final ObjectMapper objectMapper;

    public NuteraLearningExplainService(SiliconFlowChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    public NuteraLearningExplainResponse explainCode(NuteraLearningExplainRequest request) {
        List<String> logs = new ArrayList<>();
        append(logs, "学习讲解请求已接收。");
        append(logs, "模型: " + safe(request.getModel()) + ", 语言: " + safe(request.getLanguage()));

        if (!chatService.hasApiCredentialForModel(request.getModel(), "")) {
            throw new ApiKeyNotConfiguredException(chatService.buildMissingCredentialMessage(request.getModel()));
        }

        String code = safe(request.getCode());
        List<String> sourceLines = splitLines(code);
        int selectedLine = normalizeSelectedLine(request.getSelectedLine(), sourceLines.size());
        append(logs, "代码总行数: " + sourceLines.size() + ", 当前选中行: " + selectedLine);

        try {
            SiliconFlowChatService.ChatResult chatResult = chatService.chatCompletion(
                    "",
                    request.getModel(),
                    buildSystemPrompt(),
                    buildUserPrompt(request, selectedLine)
            );

            JsonNode root = parseJson(chatResult.getContent());
            List<NuteraLearningExplainResponse.LineExplanation> parsedLineExplanations =
                    parseLineExplanations(root, sourceLines);
            List<NuteraLearningExplainResponse.CodeBlockExplanation> parsedBlocks =
                    parseCodeBlocks(root, sourceLines.size());

            if (parsedLineExplanations.isEmpty()) {
                append(logs, "模型返回 line_explanations 为空，启用本地兜底行讲解。");
                parsedLineExplanations = buildFallbackLineExplanations(sourceLines);
            }
            if (parsedBlocks.isEmpty()) {
                append(logs, "模型返回 code_blocks 为空，启用本地兜底代码分块。");
                parsedBlocks = buildFallbackBlocks(sourceLines);
            }

            NuteraLearningExplainResponse.CodeBlockExplanation selectedBlock =
                    parseSelectedBlock(root, parsedBlocks, sourceLines.size(), selectedLine);
            if (selectedBlock == null) {
                selectedBlock = selectBlockByLine(parsedBlocks, selectedLine);
            }

            NuteraLearningExplainResponse response = new NuteraLearningExplainResponse();
            response.setStatus("SUCCESS");
            response.setMessage("代码讲解生成成功。");
            response.setLineExplanations(parsedLineExplanations);
            response.setCodeBlocks(parsedBlocks);
            response.setSelectedBlock(selectedBlock);
            response.setRawResponse(safe(chatResult.getRawResponse()));
            append(logs, "讲解生成完成：line_explanations=" + parsedLineExplanations.size() + ", code_blocks=" + parsedBlocks.size());
            response.setLog(String.join("\n", logs));
            return response;
        } catch (Exception ex) {
            append(logs, "模型讲解失败，启用本地兜底: " + safe(ex.getMessage()));
            List<NuteraLearningExplainResponse.LineExplanation> fallbackLines = buildFallbackLineExplanations(sourceLines);
            List<NuteraLearningExplainResponse.CodeBlockExplanation> fallbackBlocks = buildFallbackBlocks(sourceLines);

            NuteraLearningExplainResponse response = new NuteraLearningExplainResponse();
            response.setStatus("FALLBACK");
            response.setMessage("模型讲解失败，已使用本地兜底讲解。");
            response.setLineExplanations(fallbackLines);
            response.setCodeBlocks(fallbackBlocks);
            response.setSelectedBlock(selectBlockByLine(fallbackBlocks, selectedLine));
            response.setRawResponse("");
            response.setLog(String.join("\n", logs));
            return response;
        }
    }

    private String buildSystemPrompt() {
        return "你是代码教学讲解助手。请仅输出 JSON，不要输出任何多余文字。"
                + "必须返回字段 line_explanations、code_blocks、selected_block。"
                + "line_explanations 为数组，每项包含 line_number、line_text、line_explanation、syntax_point、common_mistake。"
                + "code_blocks 为数组，每项包含 start_line、end_line、block_title、block_type、block_explanation、key_points、common_mistakes。"
                + "selected_block 为对象，结构与 code_blocks 单项一致。"
                + "全部解释文案使用中文，简洁清晰，适合教学辅助场景。";
    }

    private String buildUserPrompt(NuteraLearningExplainRequest request, int selectedLine) {
        StringBuilder sb = new StringBuilder();
        sb.append("请对以下代码做结构化教学讲解。\n");
        sb.append("language=").append(safe(request.getLanguage())).append("\n");
        sb.append("fileName=").append(safe(request.getFileName())).append("\n");
        sb.append("selectedLine=").append(selectedLine).append("\n\n");
        sb.append("代码如下：\n");
        sb.append("```").append(safe(request.getLanguage())).append("\n");
        sb.append(safe(request.getCode())).append("\n");
        sb.append("```\n");
        sb.append("要求：\n");
        sb.append("1) line_explanations 至少覆盖所有非空代码行；\n");
        sb.append("2) code_blocks 必须按行号范围分块并尽量完整覆盖主逻辑；\n");
        sb.append("3) selected_block 必须是 selectedLine 所在代码块。\n");
        return sb.toString();
    }

    private JsonNode parseJson(String rawContent) throws Exception {
        String text = safe(rawContent).trim();
        if (text.startsWith("```")) {
            text = text.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("(?s)\\s*```$", "");
        }
        int left = text.indexOf('{');
        int right = text.lastIndexOf('}');
        if (left >= 0 && right > left) {
            text = text.substring(left, right + 1);
        }
        return objectMapper.readTree(text);
    }

    private List<NuteraLearningExplainResponse.LineExplanation> parseLineExplanations(JsonNode root, List<String> sourceLines) {
        JsonNode arr = arr(root, "line_explanations", "lineExplanations", "lines");
        Map<Integer, NuteraLearningExplainResponse.LineExplanation> byLine = new LinkedHashMap<>();
        if (arr != null) {
            for (JsonNode item : arr) {
                int lineNumber = intVal(item, "line_number", "lineNumber", "line");
                if (lineNumber <= 0 || lineNumber > sourceLines.size()) {
                    continue;
                }
                NuteraLearningExplainResponse.LineExplanation row = new NuteraLearningExplainResponse.LineExplanation();
                row.setLineNumber(lineNumber);
                String lineText = text(item, "line_text", "lineText", "code");
                row.setLineText(lineText.isBlank() ? sourceLines.get(lineNumber - 1) : lineText);
                row.setLineExplanation(nonBlankOrDefault(
                        text(item, "line_explanation", "lineExplanation", "explanation", "说明"),
                        "该行参与当前程序逻辑，请结合上下文理解其作用。"
                ));
                row.setSyntaxPoint(nonBlankOrDefault(
                        text(item, "syntax_point", "syntaxPoint", "语法点"),
                        "基础语法结构。"
                ));
                row.setCommonMistake(nonBlankOrDefault(
                        text(item, "common_mistake", "commonMistake", "常见错误"),
                        "注意标点、缩进与上下文依赖。"
                ));
                byLine.put(lineNumber, row);
            }
        }

        for (int line = 1; line <= sourceLines.size(); line++) {
            if (!byLine.containsKey(line)) {
                byLine.put(line, buildFallbackLine(line, sourceLines.get(line - 1)));
            }
        }
        List<NuteraLearningExplainResponse.LineExplanation> rows = new ArrayList<>(byLine.values());
        rows.sort(Comparator.comparingInt(NuteraLearningExplainResponse.LineExplanation::getLineNumber));
        return rows;
    }

    private List<NuteraLearningExplainResponse.CodeBlockExplanation> parseCodeBlocks(JsonNode root, int totalLines) {
        JsonNode arr = arr(root, "code_blocks", "codeBlocks", "blocks");
        if (arr == null) {
            return List.of();
        }
        List<NuteraLearningExplainResponse.CodeBlockExplanation> rows = new ArrayList<>();
        for (JsonNode item : arr) {
            NuteraLearningExplainResponse.CodeBlockExplanation block = parseBlockNode(item, totalLines);
            if (block == null) {
                continue;
            }
            rows.add(block);
        }
        rows.sort(Comparator.comparingInt(NuteraLearningExplainResponse.CodeBlockExplanation::getStartLine)
                .thenComparingInt(NuteraLearningExplainResponse.CodeBlockExplanation::getEndLine));
        return rows;
    }

    private NuteraLearningExplainResponse.CodeBlockExplanation parseSelectedBlock(JsonNode root,
                                                                                  List<NuteraLearningExplainResponse.CodeBlockExplanation> blocks,
                                                                                  int totalLines,
                                                                                  int selectedLine) {
        JsonNode selectedNode = obj(root, "selected_block", "selectedBlock", "active_block", "activeBlock");
        if (selectedNode != null && selectedNode.isObject()) {
            NuteraLearningExplainResponse.CodeBlockExplanation direct = parseBlockNode(selectedNode, totalLines);
            if (direct != null) {
                return direct;
            }
        }
        return selectBlockByLine(blocks, selectedLine);
    }

    private NuteraLearningExplainResponse.CodeBlockExplanation parseBlockNode(JsonNode item, int totalLines) {
        int start = intVal(item, "start_line", "startLine", "line_start", "from");
        int end = intVal(item, "end_line", "endLine", "line_end", "to");
        if (start <= 0 && end <= 0) {
            int line = intVal(item, "line_number", "lineNumber", "line");
            if (line > 0) {
                start = line;
                end = line;
            }
        }
        if (start <= 0 || totalLines <= 0) {
            return null;
        }
        start = Math.max(1, Math.min(totalLines, start));
        end = end <= 0 ? start : Math.max(start, Math.min(totalLines, end));

        NuteraLearningExplainResponse.CodeBlockExplanation block = new NuteraLearningExplainResponse.CodeBlockExplanation();
        block.setStartLine(start);
        block.setEndLine(end);
        block.setBlockTitle(nonBlankOrDefault(
                text(item, "block_title", "blockTitle", "title", "块标题"),
                "代码块 " + start + "-" + end
        ));
        block.setBlockType(nonBlankOrDefault(
                text(item, "block_type", "blockType", "type", "块类型"),
                "LOGIC_BLOCK"
        ));
        block.setBlockExplanation(nonBlankOrDefault(
                text(item, "block_explanation", "blockExplanation", "explanation", "说明"),
                "该代码块负责当前程序的一部分核心逻辑。"
        ));
        block.setKeyPoints(textArray(item, "key_points", "keyPoints", "knowledge_points", "knowledgePoints"));
        block.setCommonMistakes(textArray(item, "common_mistakes", "commonMistakes", "pitfalls", "errors"));
        if (block.getKeyPoints().isEmpty()) {
            block.setKeyPoints(List.of("建议关注该代码块的输入、处理与输出关系。"));
        }
        if (block.getCommonMistakes().isEmpty()) {
            block.setCommonMistakes(List.of("忽略上下文依赖会导致对该块行为理解偏差。"));
        }
        return block;
    }

    private List<String> textArray(JsonNode node, String... keys) {
        JsonNode arr = arr(node, keys);
        if (arr == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonNode item : arr) {
            String text = safe(item.asText("")).trim();
            if (!text.isBlank()) {
                out.add(text);
            }
        }
        return new ArrayList<>(out);
    }

    private List<NuteraLearningExplainResponse.LineExplanation> buildFallbackLineExplanations(List<String> sourceLines) {
        List<NuteraLearningExplainResponse.LineExplanation> rows = new ArrayList<>();
        for (int line = 1; line <= sourceLines.size(); line++) {
            rows.add(buildFallbackLine(line, sourceLines.get(line - 1)));
        }
        return rows;
    }

    private NuteraLearningExplainResponse.LineExplanation buildFallbackLine(int lineNumber, String lineTextRaw) {
        String lineText = safe(lineTextRaw).trim();
        NuteraLearningExplainResponse.LineExplanation row = new NuteraLearningExplainResponse.LineExplanation();
        row.setLineNumber(lineNumber);
        row.setLineText(lineText);
        if (lineText.isBlank()) {
            row.setLineExplanation("这是空行或分隔行，主要用于提升代码可读性。");
            row.setSyntaxPoint("空行不参与语义执行。");
            row.setCommonMistake("删除过多空行会降低代码阅读效率。");
            return row;
        }
        String lower = lineText.toLowerCase(Locale.ROOT);
        if (lower.startsWith("#include") || lower.startsWith("import ") || lower.startsWith("using ")) {
            row.setLineExplanation("该行用于引入依赖或命名空间，为后续语句提供能力支持。");
            row.setSyntaxPoint("导入语句与命名空间声明。");
            row.setCommonMistake("导入路径或包名拼写错误会导致编译失败。");
            return row;
        }
        if (lower.contains("if") || lower.contains("else")) {
            row.setLineExplanation("该行属于条件判断逻辑，会影响程序执行分支。");
            row.setSyntaxPoint("条件表达式与分支控制。");
            row.setCommonMistake("边界条件遗漏会导致错误分支被执行。");
            return row;
        }
        if (lower.contains("for") || lower.contains("while")) {
            row.setLineExplanation("该行属于循环结构，程序会按条件重复执行。");
            row.setSyntaxPoint("循环控制与终止条件。");
            row.setCommonMistake("循环变量更新错误可能导致死循环。");
            return row;
        }
        if (lower.contains("print(") || lower.contains("cout <<") || lower.contains("console.log") || lower.contains("system.out.print")) {
            row.setLineExplanation("该行用于输出内容，可用于观察程序运行结果。");
            row.setSyntaxPoint("输出语句与流操作。");
            row.setCommonMistake("引号或分号缺失会导致语法错误。");
            return row;
        }
        if (lower.contains("return")) {
            row.setLineExplanation("该行用于返回结果并结束当前函数执行。");
            row.setSyntaxPoint("函数返回值与控制流终止。");
            row.setCommonMistake("返回值类型不匹配会导致编译或运行错误。");
            return row;
        }
        row.setLineExplanation("该行参与当前逻辑处理，请结合上下文理解其作用。");
        row.setSyntaxPoint("基础语句结构。");
        row.setCommonMistake("忽略变量状态变化会造成理解偏差。");
        return row;
    }

    private List<NuteraLearningExplainResponse.CodeBlockExplanation> buildFallbackBlocks(List<String> sourceLines) {
        int totalLines = sourceLines.size();
        List<NuteraLearningExplainResponse.CodeBlockExplanation> blocks = new ArrayList<>();

        int importStart = -1;
        int importEnd = -1;
        for (int i = 0; i < sourceLines.size(); i++) {
            String line = safe(sourceLines.get(i)).trim().toLowerCase(Locale.ROOT);
            boolean importLike = line.startsWith("#include")
                    || line.startsWith("import ")
                    || line.startsWith("using ")
                    || line.startsWith("package ");
            if (importLike) {
                if (importStart < 0) {
                    importStart = i + 1;
                }
                importEnd = i + 1;
            } else if (importStart > 0) {
                break;
            }
        }
        if (importStart > 0) {
            blocks.add(block(importStart, importEnd, "依赖与导入区", "IMPORT_BLOCK",
                    "该代码块用于引入外部库、包或命名空间，是后续语句执行的基础。",
                    List.of("确认导入路径正确", "理解导入内容的用途"),
                    List.of("误删依赖导入会导致编译失败")));
        }

        int mainLine = findLine(sourceLines, "main\\s*\\(", "def\\s+main\\s*\\(");
        if (mainLine > 0) {
            int mainEnd = detectMainBlockEnd(sourceLines, mainLine);
            blocks.add(block(mainLine, mainEnd, "主函数入口区", "MAIN_FUNCTION_BLOCK",
                    "该代码块定义主执行入口，程序会从这里开始运行核心逻辑。",
                    List.of("从入口函数追踪执行路径", "关注输入与输出关系"),
                    List.of("忽略入口上下文会误判执行顺序")));
        }

        List<Integer> outputLines = findOutputLines(sourceLines);
        if (!outputLines.isEmpty()) {
            int start = outputLines.get(0);
            int end = outputLines.get(outputLines.size() - 1);
            blocks.add(block(start, end, "输出语句区", "OUTPUT_BLOCK",
                    "该代码块负责输出运行结果，可用于验证程序行为是否符合预期。",
                    List.of("输出内容可用于调试", "输出顺序反映执行路径"),
                    List.of("输出格式错误会影响结果判断")));
        }

        int returnLine = findLine(sourceLines, "\\breturn\\b");
        if (returnLine > 0) {
            blocks.add(block(returnLine, returnLine, "返回语句区", "RETURN_BLOCK",
                    "该语句用于返回结果并结束函数执行。",
                    List.of("确认返回值类型", "理解 return 对控制流的影响"),
                    List.of("返回值与函数声明不一致")));
        }

        if (blocks.isEmpty() && totalLines > 0) {
            blocks.add(block(1, totalLines, "整体逻辑区", "GLOBAL_BLOCK",
                    "当前代码结构较紧凑，建议按自上而下顺序理解整体执行流程。",
                    List.of("先看入口语句再看细节", "按数据流追踪变量变化"),
                    List.of("只看单行容易忽略上下文")));
        }

        blocks.sort(Comparator.comparingInt(NuteraLearningExplainResponse.CodeBlockExplanation::getStartLine)
                .thenComparingInt(NuteraLearningExplainResponse.CodeBlockExplanation::getEndLine));
        return deduplicateBlocks(blocks);
    }

    private List<NuteraLearningExplainResponse.CodeBlockExplanation> deduplicateBlocks(
            List<NuteraLearningExplainResponse.CodeBlockExplanation> source
    ) {
        Set<String> seen = new LinkedHashSet<>();
        List<NuteraLearningExplainResponse.CodeBlockExplanation> out = new ArrayList<>();
        for (NuteraLearningExplainResponse.CodeBlockExplanation item : source) {
            String key = item.getStartLine() + "-" + item.getEndLine() + "-" + safe(item.getBlockType());
            if (seen.add(key)) {
                out.add(item);
            }
        }
        return out;
    }

    private NuteraLearningExplainResponse.CodeBlockExplanation block(int startLine,
                                                                     int endLine,
                                                                     String title,
                                                                     String type,
                                                                     String explanation,
                                                                     List<String> keyPoints,
                                                                     List<String> commonMistakes) {
        NuteraLearningExplainResponse.CodeBlockExplanation row = new NuteraLearningExplainResponse.CodeBlockExplanation();
        row.setStartLine(startLine);
        row.setEndLine(Math.max(startLine, endLine));
        row.setBlockTitle(title);
        row.setBlockType(type);
        row.setBlockExplanation(explanation);
        row.setKeyPoints(new ArrayList<>(keyPoints));
        row.setCommonMistakes(new ArrayList<>(commonMistakes));
        return row;
    }

    private int detectMainBlockEnd(List<String> sourceLines, int startLine) {
        int braceDepth = 0;
        boolean sawBrace = false;
        for (int i = startLine - 1; i < sourceLines.size(); i++) {
            String line = safe(sourceLines.get(i));
            for (int c = 0; c < line.length(); c++) {
                char ch = line.charAt(c);
                if (ch == '{') {
                    braceDepth++;
                    sawBrace = true;
                } else if (ch == '}') {
                    braceDepth--;
                    if (sawBrace && braceDepth <= 0) {
                        return i + 1;
                    }
                }
            }
        }
        if (!sawBrace) {
            return sourceLines.size();
        }
        return sourceLines.size();
    }

    private int findLine(List<String> sourceLines, String... patterns) {
        for (int i = 0; i < sourceLines.size(); i++) {
            String line = safe(sourceLines.get(i));
            for (String pattern : patterns) {
                if (line.matches("(?i).*" + pattern + ".*")) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    private List<Integer> findOutputLines(List<String> sourceLines) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < sourceLines.size(); i++) {
            String line = safe(sourceLines.get(i)).toLowerCase(Locale.ROOT);
            if (line.contains("print(")
                    || line.contains("cout <<")
                    || line.contains("console.log")
                    || line.contains("system.out.print")) {
                out.add(i + 1);
            }
        }
        return out;
    }

    private NuteraLearningExplainResponse.CodeBlockExplanation selectBlockByLine(
            List<NuteraLearningExplainResponse.CodeBlockExplanation> blocks,
            int lineNumber
    ) {
        NuteraLearningExplainResponse.CodeBlockExplanation matched = null;
        int bestSpan = Integer.MAX_VALUE;
        for (NuteraLearningExplainResponse.CodeBlockExplanation block : blocks) {
            if (lineNumber < block.getStartLine() || lineNumber > block.getEndLine()) {
                continue;
            }
            int span = Math.max(1, block.getEndLine() - block.getStartLine() + 1);
            if (span < bestSpan) {
                bestSpan = span;
                matched = block;
            }
        }
        if (matched != null) {
            return matched;
        }
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    private int normalizeSelectedLine(Integer selectedLine, int totalLines) {
        int line = selectedLine == null ? 1 : selectedLine;
        if (totalLines <= 0) {
            return 1;
        }
        return Math.max(1, Math.min(totalLines, line));
    }

    private List<String> splitLines(String code) {
        String text = safe(code);
        String[] lines = text.split("\\R", -1);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            out.add(line == null ? "" : line);
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private String nonBlankOrDefault(String value, String fallback) {
        String text = safe(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private String text(JsonNode node, String... keys) {
        if (node == null) {
            return "";
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null) {
                continue;
            }
            String text = safe(value.asText("")).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private JsonNode arr(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isArray()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode obj(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isObject()) {
                return value;
            }
        }
        return null;
    }

    private int intVal(JsonNode node, String... keys) {
        if (node == null) {
            return 0;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null) {
                continue;
            }
            try {
                return value.asInt();
            } catch (Exception ignored) {
                // ignore
            }
            try {
                return Integer.parseInt(value.asText("").trim());
            } catch (Exception ignored) {
                // ignore
            }
        }
        return 0;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void append(List<String> logs, String message) {
        logs.add("[" + Instant.now() + "] " + message);
    }
}

