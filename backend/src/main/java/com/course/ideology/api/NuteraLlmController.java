package com.course.ideology.api;

import com.course.ideology.api.dto.NuteraGenerateRequest;
import com.course.ideology.api.dto.NuteraGenerateResponse;
import com.course.ideology.api.dto.NuteraCaseSourceResponse;
import com.course.ideology.api.dto.NuteraBatchStartResponse;
import com.course.ideology.api.dto.NuteraBatchStatusResponse;
import com.course.ideology.api.dto.NuteraBatchPauseRequest;
import com.course.ideology.api.dto.NuteraBatchPauseResponse;
import com.course.ideology.api.dto.NuteraBatchReportSummaryResponse;
import com.course.ideology.api.dto.NuteraBatchReportDetailResponse;
import com.course.ideology.api.dto.NuteraLearningExplainRequest;
import com.course.ideology.api.dto.NuteraLearningExplainResponse;
import com.course.ideology.api.dto.NuteraVerificationSummaryRequest;
import com.course.ideology.api.dto.NuteraVerificationSummaryResponse;
import com.course.ideology.service.ApiKeyNotConfiguredException;
import com.course.ideology.service.NuteraBatchTaskService;
import com.course.ideology.service.NuteraBatchReportStoreService;
import com.course.ideology.service.NuteraCaseSourceService;
import com.course.ideology.service.NuteraLearningExplainService;
import com.course.ideology.service.NuteraLlmService;
import com.course.ideology.service.NuteraVerificationSummaryService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/nutera")
@Validated
public class NuteraLlmController {
    private final NuteraLlmService nuteraLlmService;
    private final NuteraCaseSourceService nuteraCaseSourceService;
    private final NuteraBatchTaskService nuteraBatchTaskService;
    private final NuteraBatchReportStoreService nuteraBatchReportStoreService;
    private final NuteraLearningExplainService nuteraLearningExplainService;
    private final NuteraVerificationSummaryService nuteraVerificationSummaryService;

    public NuteraLlmController(NuteraLlmService nuteraLlmService,
                               NuteraCaseSourceService nuteraCaseSourceService,
                               NuteraBatchTaskService nuteraBatchTaskService,
                               NuteraBatchReportStoreService nuteraBatchReportStoreService,
                               NuteraLearningExplainService nuteraLearningExplainService,
                               NuteraVerificationSummaryService nuteraVerificationSummaryService) {
        this.nuteraLlmService = nuteraLlmService;
        this.nuteraCaseSourceService = nuteraCaseSourceService;
        this.nuteraBatchTaskService = nuteraBatchTaskService;
        this.nuteraBatchReportStoreService = nuteraBatchReportStoreService;
        this.nuteraLearningExplainService = nuteraLearningExplainService;
        this.nuteraVerificationSummaryService = nuteraVerificationSummaryService;
    }

    @GetMapping("/case-source")
    public NuteraCaseSourceResponse loadCaseSource(@RequestParam("dataset") String dataset,
                                                   @RequestParam(value = "entryIndex", required = false) Integer entryIndex) {
        try {
            return nuteraCaseSourceService.loadCaseSource(dataset, entryIndex);
        } catch (Exception ex) {
            NuteraCaseSourceResponse failed = new NuteraCaseSourceResponse();
            failed.setStatus("FAILED");
            failed.setDataset(dataset);
            failed.setMessage(ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Failed to load case source."
                    : ex.getMessage());
            failed.setLog("[" + Instant.now() + "] Case source loading failed: " + failed.getMessage());
            return failed;
        }
    }

    @PostMapping("/generate-ranking-function")
    public NuteraGenerateResponse generateRankingFunction(@Valid @RequestBody NuteraGenerateRequest request) {
        try {
            return nuteraLlmService.generateRankingFunction(request);
        } catch (ApiKeyNotConfiguredException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/learning/explain-code")
    public NuteraLearningExplainResponse explainCodeForLearning(@Valid @RequestBody NuteraLearningExplainRequest request) {
        try {
            return nuteraLearningExplainService.explainCode(request);
        } catch (ApiKeyNotConfiguredException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PostMapping("/verification/summary-graph")
    public NuteraVerificationSummaryResponse buildVerificationSummary(@Valid @RequestBody NuteraVerificationSummaryRequest request) {
        try {
            return nuteraVerificationSummaryService.buildSummary(request);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PostMapping("/batch/start")
    public NuteraBatchStartResponse startBatch(@Valid @RequestBody NuteraGenerateRequest request) {
        try {
            request.setBatchMode(true);
            return nuteraBatchTaskService.start(request);
        } catch (ApiKeyNotConfiguredException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @GetMapping("/batch/status")
    public NuteraBatchStatusResponse queryBatchStatus(@RequestParam("taskId") String taskId) {
        NuteraBatchStatusResponse response = nuteraBatchTaskService.status(taskId);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch task not found: " + taskId);
        }
        return response;
    }

    @PostMapping("/batch/pause")
    public NuteraBatchPauseResponse pauseBatch(@Valid @RequestBody NuteraBatchPauseRequest request) {
        NuteraBatchTaskService.PauseResult result = nuteraBatchTaskService.pause(request.getTaskId());
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch task not found: " + request.getTaskId());
        }
        NuteraBatchPauseResponse response = new NuteraBatchPauseResponse();
        response.setSuccess(result.isSuccess());
        response.setTaskId(result.getTaskId());
        response.setStatus(result.getStatus());
        response.setMessage(result.getMessage());
        response.setCurrentIndex(result.getCurrentIndex());
        return response;
    }

    @GetMapping("/reports")
    public List<NuteraBatchReportSummaryResponse> listBatchReports(
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {
        return nuteraBatchReportStoreService.listReports(limit == null ? 50 : limit);
    }

    @GetMapping("/reports/{taskId}")
    public NuteraBatchReportDetailResponse getBatchReport(@PathVariable("taskId") String taskId) {
        NuteraBatchReportDetailResponse report = nuteraBatchReportStoreService.getReport(taskId);
        if (report == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch report not found: " + taskId);
        }
        return report;
    }

    @GetMapping("/reports/{taskId}/cases")
    public List<NuteraGenerateResponse.BatchCaseResult> getBatchReportCases(@PathVariable("taskId") String taskId) {
        List<NuteraGenerateResponse.BatchCaseResult> rows = nuteraBatchReportStoreService.getReportCases(taskId);
        if (rows == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch report not found: " + taskId);
        }
        return rows;
    }

    @GetMapping("/reports/{taskId}/export")
    public ResponseEntity<byte[]> exportBatchReportCsv(@PathVariable("taskId") String taskId) {
        NuteraBatchReportStoreService.ExportPayload payload = nuteraBatchReportStoreService.exportReportCsv(taskId);
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch report not found: " + taskId);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.getFilename() + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(payload.getContent());
    }

    @DeleteMapping("/reports/{taskId}")
    public Map<String, Object> deleteBatchReport(@PathVariable("taskId") String taskId) {
        boolean deleted = nuteraBatchReportStoreService.deleteReport(taskId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch report not found: " + taskId);
        }
        return Map.of(
                "success", true,
                "taskId", taskId,
                "message", "Report deleted."
        );
    }
}
