package com.reactive_task.task_spring.repository;

import com.reactive_task.task_spring.model.Task;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface TaskRepository extends ReactiveMongoRepository<Task,String> {
}
