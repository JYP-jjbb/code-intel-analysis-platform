package com.course.ideology.task;

import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTaskRepository implements TaskRepository {
    private final Map<String, TaskRecord> storage = new ConcurrentHashMap<>();

    @Override
    public void save(TaskRecord record) {
        storage.put(record.getTaskId(), record);
    }

    @Override
    public Optional<TaskRecord> findById(String taskId) {
        return Optional.ofNullable(storage.get(taskId));
    }

    @Override
    public List<TaskRecord> findAll() {
        List<TaskRecord> list = new ArrayList<>(storage.values());
        list.sort(Comparator.comparing(TaskRecord::getCreatedAt).reversed());
        return list;
    }
}
