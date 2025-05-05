package com.example.p3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
public class TaskController {

    @Autowired TaskRepository repo;
    @Autowired MemoryAccountant mem;
    @Autowired TaskQueue queue;

    ExecutorService pool = Executors.newCachedThreadPool();

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody Task task) {
        task.setCreated(Instant.now());
        task.setStatus(TaskStatus.PENDING);
        repo.save(task);

        if (mem.tryReserve(task.getMemMb())) {
            task.setStatus(TaskStatus.RESERVED);
            repo.save(task);
            pool.submit(() -> runTask(task));
        } else {
            task.setStatus(TaskStatus.QUEUED);
            queue.enqueue(task);
            repo.save(task);
        }

        return Map.of("id", task.getId());
    }

    private void runTask(Task task) {
        try {
            task.setStatus(TaskStatus.RUNNING);
            task.setStarted(Instant.now());
            repo.save(task);

            String logDir = "logs";
            new File(logDir).mkdirs();

            String logFilePath = logDir + "/task-" + task.getId() + ".log";
            String containerName = "task-" + task.getId();

            // Full Docker command with memory, name, mount, logging
            String dockerCommand = String.format(
                    "docker run --rm --memory=%dM --name %s -v %s:/logs p3/task-base:1 \"bash -c 'python3 -c \\\"print(42 * 2)\\\" > /logs/task-%d.log 2>&1'\"",
                    task.getMemMb(),
                    containerName,
                    new File("logs").getAbsolutePath(),
                    task.getId()
            );


            Process process = new ProcessBuilder("bash", "-c", dockerCommand).start();

            boolean finished = process.waitFor(task.getTimeoutSec(), TimeUnit.SECONDS);
            task.setEnded(Instant.now());

            if (!finished) {
                // Timeout → force kill the Docker container
                new ProcessBuilder("docker", "kill", containerName).start();
                task.setStatus(TaskStatus.FAILED_TIMEOUT);
            } else {
                int exitCode = process.exitValue();
                task.setExitCode(exitCode);
                task.setStatus(exitCode == 0 ? TaskStatus.SUCCEEDED : TaskStatus.FAILED);
            }

            task.setLogPath(new File(logFilePath).getAbsolutePath());
            repo.save(task);

        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            repo.save(task);
        } finally {
            mem.release(task.getMemMb());

            // Try to schedule next queued tasks
            while (!queue.isEmpty()) {
                Task next = queue.peek();
                if (mem.tryReserve(next.getMemMb())) {
                    queue.poll();
                    next.setStatus(TaskStatus.RESERVED);
                    repo.save(next);
                    pool.submit(() -> runTask(next));
                } else {
                    break;
                }
            }
        }
    }

    @GetMapping("/status/{id}")
    public Task getStatus(@PathVariable Long id) {
        return repo.findById(id).orElseThrow();
    }

    @GetMapping("/logs/{id}")
    public String getLogs(@PathVariable Long id) throws Exception {
        Task task = repo.findById(id).orElseThrow();
        return java.nio.file.Files.readString(new File(task.getLogPath()).toPath());
    }
}
