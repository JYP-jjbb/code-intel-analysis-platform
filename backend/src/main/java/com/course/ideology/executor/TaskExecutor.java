package com.course.ideology.executor;

import com.course.ideology.api.dto.NuteraTaskRequest;
import com.course.ideology.task.TaskRecord;

public interface TaskExecutor {
    void execute(TaskRecord record, NuteraTaskRequest request);
}

