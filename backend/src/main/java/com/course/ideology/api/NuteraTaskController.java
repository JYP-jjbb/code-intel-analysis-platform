package com.course.ideology.api;

import com.course.ideology.api.dto.NuteraTaskRequest;
import com.course.ideology.api.dto.TaskSubmitResponse;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/tasks/nutera")
@Validated
public class NuteraTaskController {
    private final TaskService taskService;

    public NuteraTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public TaskSubmitResponse submitNutera(@Valid @RequestBody NuteraTaskRequest request) {
        TaskRecord record = taskService.submitNuteraTask(request);
        return TaskSubmitResponse.from(record);
    }
}

