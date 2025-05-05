package com.example.p3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        String logsDir = new File("logs").getAbsolutePath();
        new File(logsDir).mkdirs();

        String containerName = "task-" + task.getId();
        String logFilePath  = logsDir + "/task-" + task.getId() + ".log";

        // 1) pull the "user command" from the request-backed Task
        //    e.g. "python3 -c \"print(42 * 2)\""
        String userCmd = task.getCommand();

        // 2) build the docker invocation as an arg list
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("--memory=" + task.getMemMb() + "M");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("-v");
        cmd.add(logsDir + ":/logs");
        cmd.add("p3/task-base:1");
        // we’ll shell-interpret the user command and redirect inside the container:
//        cmd.add("bash");
//        cmd.add("-c");
        cmd.add(userCmd + " > /logs/task-" + task.getId() + ".log 2>&1");

        try {
            // 3) spin it up
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    // (optional) if you want to capture docker’s own stderr/stdout:
                    .redirectErrorStream(true)
                    // (not needed if you rely wholly on in-container redirection)
                    // .redirectOutput(new File(logFilePath))
                    ;

            Process process = pb.start();

            boolean finished = process.waitFor(task.getTimeoutSec(), TimeUnit.SECONDS);
            task.setEnded(Instant.now());

            if (!finished) {
                // timed out → kill the container
                new ProcessBuilder("docker", "kill", containerName).start();
                task.setStatus(TaskStatus.FAILED_TIMEOUT);
            } else {
                int exitCode = process.exitValue();
                task.setExitCode(exitCode);
                task.setStatus(exitCode == 0
                        ? TaskStatus.SUCCEEDED
                        : TaskStatus.FAILED
                );
            }

            task.setLogPath(logFilePath);
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
