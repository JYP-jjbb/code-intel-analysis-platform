package com.course.ideology.task;

import java.util.List;
import java.util.Optional;

public interface TaskRepository {
    void save(TaskRecord record);

    Optional<TaskRecord> findById(String taskId);

    List<TaskRecord> findAll();
}
