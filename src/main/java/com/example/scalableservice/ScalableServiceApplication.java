package com.example.scalableservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@SpringBootApplication
@EnableAsync
public class ScalableServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScalableServiceApplication.class, args);
    }

    @Bean
    public AsyncTaskExecutor applicationTaskExecutor() {
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        return new TaskExecutorAdapter(executorService);
    }

    @RestController
    public class ScalableController {

        @GetMapping("/api/process")
        @Async
        public CompletableFuture<String> processRequest() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Simulate some work
                    Thread.sleep(Duration.ofSeconds(2));
                    return "Request processed by " + Thread.currentThread();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Request interrupted";
                }
            });
        }

        @GetMapping("/api/structured")
        public String structuredConcurrencyExample() throws Exception {
            try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                Future<String> task1 = scope.fork(() -> {
                    Thread.sleep(1000);
                    return "Result from task 1";
                });

                Future<String> task2 = scope.fork(() -> {
                    Thread.sleep(1500);
                    return "Result from task 2";
                });

                scope.join();
                scope.throwIfFailed();

                return task1.resultNow() + " | " + task2.resultNow();
            }
        }
    }
}