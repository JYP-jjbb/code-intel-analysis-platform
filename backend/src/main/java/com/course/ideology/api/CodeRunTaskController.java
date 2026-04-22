package com.course.ideology.api;

import com.course.ideology.api.dto.CodeRunTaskCreateResponse;
import com.course.ideology.api.dto.CodeRunTaskDetailResponse;
import com.course.ideology.api.dto.CodeRunTaskRequest;
import com.course.ideology.service.CodeRunTaskService;
import com.course.ideology.task.TaskRecord;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/code-run/tasks")
@Validated
public class CodeRunTaskController {
    private final CodeRunTaskService codeRunTaskService;

    public CodeRunTaskController(CodeRunTaskService codeRunTaskService) {
        this.codeRunTaskService = codeRunTaskService;
    }

    @PostMapping
    public CodeRunTaskCreateResponse createTask(@Valid @RequestBody CodeRunTaskRequest request) {
        TaskRecord record = codeRunTaskService.submit(request);
        return new CodeRunTaskCreateResponse(record.getTaskId(), record.getStatus().name());
    }

    @GetMapping("/{taskId}")
    public CodeRunTaskDetailResponse getTask(@PathVariable("taskId") String taskId) {
        return codeRunTaskService.query(taskId);
    }
}
