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

            File logFile = new File("logs/task-" + task.getId() + ".log");
            logFile.getParentFile().mkdirs();
            Process p = new ProcessBuilder("bash", "-c", task.getCommand())
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                    .redirectErrorStream(true)
                    .start();

            boolean finished = p.waitFor(task.getTimeoutSec(), java.util.concurrent.TimeUnit.SECONDS);
            task.setEnded(Instant.now());

            if (!finished) {
                p.destroyForcibly();
                task.setStatus(TaskStatus.FAILED_TIMEOUT);
            } else {
                task.setExitCode(p.exitValue());
                task.setStatus(p.exitValue() == 0 ? TaskStatus.SUCCEEDED : TaskStatus.FAILED);
            }

            task.setLogPath(logFile.getAbsolutePath());
            repo.save(task);

        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            repo.save(task);
        } finally {
            mem.release(task.getMemMb());

            while (!queue.isEmpty()) {
                Task next = queue.peek();
                if (mem.tryReserve(next.getMemMb())) {
                    queue.poll();
                    next.setStatus(TaskStatus.RESERVED);
                    repo.save(next);
                    pool.submit(() -> runTask(next));
                } else break;
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
