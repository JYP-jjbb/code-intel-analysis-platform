package com.course.ideology.api;

import com.course.ideology.api.dto.CodeReviewTaskRequest;
import com.course.ideology.api.dto.TaskSubmitResponse;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/code-review")
public class CodeReviewTaskController {
    private final TaskService taskService;

    public CodeReviewTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    public TaskSubmitResponse submitCodeReview(@RequestBody CodeReviewTaskRequest request) {
        TaskRecord record = taskService.submitCodeReviewTask(request);
        return TaskSubmitResponse.from(record);
    }
}

