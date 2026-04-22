package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraVerificationSummaryRequest;
import com.course.ideology.api.dto.NuteraVerificationSummaryResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NuteraVerificationSummaryServiceTest {

    @Test
    void shouldApplyTerminationSemanticColorRolesForLoopExample() {
        NuteraVerificationSummaryService service = new NuteraVerificationSummaryService();
        NuteraVerificationSummaryRequest request = new NuteraVerificationSummaryRequest();
        request.setCode(String.join("\n",
                "int x = args[0].length();",
                "int y = args[1].length();",
                "while (x >= y && y > 0) {",
                "  x = x - y;",
                "  r = r + 1;",
                "}"
        ));
        request.setLanguage("java");
        request.setCandidateFunction("ReLU(x - y)");
        request.setCheckerStatus("COMPLETED");
        request.setCheckerVerdict("NOT_PROVED");
        request.setCheckerConclusion("NO");
        request.setSelectedLine(4);

        NuteraVerificationSummaryResponse response = service.buildSummary(request);
        List<NuteraVerificationSummaryResponse.GraphNode> nodes = response.getGraph().getNodes();
        assertNotNull(nodes);

        NuteraVerificationSummaryResponse.GraphNode inputX = findNode(nodes, 1, "args[0]");
        NuteraVerificationSummaryResponse.GraphNode inputY = findNode(nodes, 2, "args[1]");
        NuteraVerificationSummaryResponse.GraphNode loopGuard = findNode(nodes, 3, "while");
        NuteraVerificationSummaryResponse.GraphNode xUpdate = findNode(nodes, 4, "x = x - y");
        NuteraVerificationSummaryResponse.GraphNode rUpdate = findNode(nodes, 5, "r = r + 1");

        assertEquals("input", inputX.getStatus());
        assertEquals("input", inputY.getStatus());
        assertEquals("support", loopGuard.getStatus());
        assertEquals("support", xUpdate.getStatus());
        assertEquals("normal", rUpdate.getStatus());

        boolean hasSupportControlDep = response.getGraph().getEdges().stream()
                .anyMatch(edge ->
                        "control_dep".equalsIgnoreCase(edge.getType())
                                && loopGuard.getId().equals(edge.getSource())
                                && xUpdate.getId().equals(edge.getTarget())
                                && "support".equalsIgnoreCase(edge.getStatus())
                );
        assertTrue(hasSupportControlDep, "loop_guard -> x update should be support edge");
    }

    private NuteraVerificationSummaryResponse.GraphNode findNode(List<NuteraVerificationSummaryResponse.GraphNode> nodes,
                                                                 int line,
                                                                 String fragment) {
        return nodes.stream()
                .filter(node -> node.getLineStart() == line && node.getLineEnd() == line)
                .filter(node -> node.getLabel() != null && node.getLabel().contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Node not found at line " + line + " containing: " + fragment));
    }
}

