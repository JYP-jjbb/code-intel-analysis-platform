package com.course.ideology.api;

import com.course.ideology.api.dto.CodeReviewProjectCleanupRequest;
import com.course.ideology.api.dto.CodeReviewProjectCleanupResponse;
import com.course.ideology.api.dto.CodeReviewProjectDownloadRequest;
import com.course.ideology.api.dto.CodeReviewProjectDownloadResponse;
import com.course.ideology.api.dto.CodeReviewProjectErrorResponse;
import com.course.ideology.api.dto.CodeReviewProjectFileResponse;
import com.course.ideology.service.CodeReviewTempProjectService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/code-review/projects")
public class CodeReviewProjectController {
    private final CodeReviewTempProjectService tempProjectService;
    private final ObjectMapper objectMapper;

    public CodeReviewProjectController(CodeReviewTempProjectService tempProjectService, ObjectMapper objectMapper) {
        this.tempProjectService = tempProjectService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/download")
    public ResponseEntity<?> download(@RequestBody CodeReviewProjectDownloadRequest request) {
        try {
            boolean forceRefresh = Boolean.TRUE.equals(request.getForceRefresh());
            CodeReviewProjectDownloadResponse response = tempProjectService.downloadRepository(request.getPageSessionId(), request.getRepoUrl(), forceRefresh);
            return ResponseEntity.ok(response);
        } catch (CodeReviewTempProjectService.RepoCloneException ex) {
            CodeReviewProjectErrorResponse body = new CodeReviewProjectErrorResponse();
            body.setMessage(ex.getMessage());
            body.setDetail(ex.getDetail());
            body.setErrorCode(ex.getErrorCode());
            body.setHints(ex.getHints());
            return ResponseEntity.status(BAD_REQUEST).body(body);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            CodeReviewProjectErrorResponse body = new CodeReviewProjectErrorResponse();
            body.setMessage("Git clone failed");
            body.setDetail(ex.getMessage());
            body.setErrorCode("GIT_CLONE_FAILED");
            body.setHints(java.util.List.of("Check repository URL, network connectivity, and Git environment."));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    @GetMapping("/{projectId}/file")
    public CodeReviewProjectFileResponse readFile(
            @PathVariable String projectId,
            @RequestParam String pageSessionId,
            @RequestParam String path
    ) {
        try {
            return tempProjectService.readProjectFile(pageSessionId, projectId, path);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/cleanup")
    public CodeReviewProjectCleanupResponse cleanup(@RequestBody CodeReviewProjectCleanupRequest request) {
        try {
            return tempProjectService.cleanupSession(request.getPageSessionId(), request.getProjectId());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping(value = "/cleanup-beacon", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> cleanupBeacon(@RequestBody(required = false) String body) {
        if (body == null || body.isBlank()) {
            return ResponseEntity.noContent().build();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            String pageSessionId = jsonNode.path("pageSessionId").asText("");
            String projectId = jsonNode.path("projectId").asText("");
            if (!pageSessionId.isBlank()) {
                tempProjectService.cleanupSession(pageSessionId, projectId);
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.noContent().build();
    }
}


