package com.reactive_task.task_spring.controller;

import com.reactive_task.task_spring.model.Task;
import com.reactive_task.task_spring.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;

import java.time.Duration;
import java.util.UUID;



@RestController
@RequestMapping("/task")
public class TaskController {

    @Autowired
    private final TaskRepository taskRepository;

    /*--------------------------------------------------------------------------------------------------
    ----------------------------------------------------------------------------------------------------

                                    NOTIFICADOR DE NOTIFICACIONES

    ----------------------------------------------------------------------------------------------------
     -------------------------------------------------------------------------------------------------*/

    private EmitterProcessor<Task> taskEmitterProcessor;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @PostConstruct
    private void createProcessor() {
        taskEmitterProcessor = EmitterProcessor.<Task>create();
    }

    /*--------------------------------------------------------------------------------------------------
    ----------------------------------------------------------------------------------------------------

                                            METODOS GET

    ----------------------------------------------------------------------------------------------------
     -------------------------------------------------------------------------------------------------*/

    @GetMapping(value = "/all",  produces = "application/json")
    private Flux<Task> getAllTask() {
        return taskRepository.findAll().delayElements(Duration.ofMillis(1));
    }

    @GetMapping(value = "/byId/{id}")
    public Mono<ResponseEntity<Task>> getTaskId(@PathVariable(value = "id") String id) {
        return taskRepository.findById(id).map(saveId -> ResponseEntity.ok(saveId))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }


    private Flux<ServerSentEvent<Task>> getAllTaskSSE() {
        return taskEmitterProcessor.log()
                .map((result) -> {
                    System.out.println("SENDIND TASK: " + result.getId());
                    return ServerSentEvent.<Task>builder()
                            .id(UUID.randomUUID().toString())
                            .event("task-result")
                            .data(result)
                            .build();
                }).concatWith(Flux.never());
    }

    @GetMapping("/Notification")
    private Flux<ServerSentEvent<Task>> getResultNotification() {
        return Flux.merge(getNotificationHearBet(), getAllTaskSSE());
    }

    /*--------------------------------------------------------------------------------------------------
    ----------------------------------------------------------------------------------------------------

                                   METODO PARA MANTENER LA CONEXION ABIERTA

    ----------------------------------------------------------------------------------------------------
     -------------------------------------------------------------------------------------------------*/

    private Flux<ServerSentEvent<Task>> getNotificationHearBet() {
        return Flux.interval(Duration.ofSeconds(2))
                .map(result -> {
                    System.out.println(String.format("HEARBEAT: [%s]", result.toString()));
                    return ServerSentEvent.<Task>builder()
                            .id(String.valueOf(result))
                            .event("hearbeat-result")
                            .data(null)
                            .build();
                });
    }


    /*--------------------------------------------------------------------------------------------------
    ----------------------------------------------------------------------------------------------------

                                      METODO POST (CREANDO NUEVA TAREA)

    ----------------------------------------------------------------------------------------------------
     -------------------------------------------------------------------------------------------------*/

    @PostMapping(value = "/create")
    private Mono<Task> create(@RequestBody Task task) {

        //ACA NOTIFICAMOS QUE SE AGREGO UNA NUEVA TAREA

        System.out.println("NOTIFICANDO NUEVA TAREA AGREAGADA: " + task.getTitle());
        taskEmitterProcessor.onNext(task);

        return taskRepository.save(task);
    }


    /*--------------------------------------------------------------------------------------------------
    ----------------------------------------------------------------------------------------------------

                                  METODO PUT (ACTUALIZA UNA TAREA EXISTENTE)

    ----------------------------------------------------------------------------------------------------
     -------------------------------------------------------------------------------------------------*/

    @PutMapping(value = "/update/{id}")
    private Mono<ResponseEntity<Task>> update(@PathVariable(value = "id") String id, @RequestBody Task task) {

        return taskRepository.findById(id)
                .flatMap(existingMap -> {
                    existingMap.setTitle(task.getTitle());
                    existingMap.setComplete(task.getComplete());
                    System.out.println("NOTIFICANDO TAREA ACTUALIZADA: " + task.getTitle());

                    //NOTIFICANDO DE TAREA ACTUALIZADA
                    taskEmitterProcessor.onNext(task);
                    return taskRepository.save(existingMap);
                }).map(updatedTask -> new ResponseEntity<>(updatedTask, HttpStatus.OK))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /*--------------------------------------------------------------------------------------------------
    ----------------------------------------------------------------------------------------------------

                                  METODO DELETE (ELIMINA UNA TAREA EXISTENTE)

    ----------------------------------------------------------------------------------------------------
     -------------------------------------------------------------------------------------------------*/

    @DeleteMapping(value = "/deleteTask/{id}")
    public Mono<ResponseEntity<Void>> deleteTask(@PathVariable(value = "id") String id) {
        return taskRepository.findById(id)
                .flatMap(existingTask ->
                        taskRepository.delete(existingTask))
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.ACCEPTED)))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

}
