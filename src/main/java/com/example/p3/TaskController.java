package com.example.p3;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    // simple DTO to hold the incoming JSON
    public static class NameRequest {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @GetMapping("/hello")
    public String sayHello(@RequestBody NameRequest request)
    {
        return "hi "+request.getName()+"! ";
    }

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
        System.out.println(logsDir);
        new File(logsDir).mkdirs();

        String containerName = "task-" + task.getId();
        String logFilePath  = logsDir + "/task-" + task.getId() + ".log";
        String errFilePath    = logsDir + "/task-" + task.getId() + "-error.log";

        // 1) pull the "user command" from the request-backed Task
        String userCmd = task.getCommand();

        // 2) build the docker invocation as an arg list
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm"); // we have commented this as we need memory stats later on
        cmd.add("--memory=" + task.getMemMb() + "M");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("-v");
        cmd.add(logsDir + ":/logs");
        cmd.add("p3/task-base:1");
        // we’ll shell-interpret the user command and redirect inside the container:
//        cmd.add("bash");
//        cmd.add("-c");
        cmd.add(userCmd );

        try {
            // 3) spin it up
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    // (optional) if you want to capture docker’s own stderr/stdout:
//                    .redirectErrorStream(true) // stderr → stdout
                    // (not needed if you rely wholly on in-container redirection)
                    .redirectOutput(new File(logFilePath)) // stdout → outFile
                    .redirectError(new File(errFilePath))
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


//            // --- fetch memory stats before removal ---
//            Process stats = new ProcessBuilder(
//                    "docker", "stats", containerName,
//                    "--no-stream",
//                    "--format", "{{.MemUsage}}"
//            ).start();
//            String out = new String(stats.getInputStream().readAllBytes(), UTF_8).trim();
//            // e.g. "12.34MiB / 64MiB"
//            // after you’ve read `out` from docker stats…
//            String usedPart = out.split("/")[0].trim();   // e.g. "12.34MiB" or "0B"
//
//            double usedBytes;
//            if (usedPart.endsWith("GiB")) {
//                usedBytes = Double.parseDouble(
//                        usedPart.substring(0, usedPart.length() - 3)
//                ) * 1024 * 1024 * 1024;
//            }
//            else if (usedPart.endsWith("MiB")) {
//                usedBytes = Double.parseDouble(
//                        usedPart.substring(0, usedPart.length() - 3)
//                ) * 1024 * 1024;
//            }
//            else if (usedPart.endsWith("KiB")) {
//                usedBytes = Double.parseDouble(
//                        usedPart.substring(0, usedPart.length() - 3)
//                ) * 1024;
//            }
//            else if (usedPart.endsWith("B")) {
//                usedBytes = Double.parseDouble(
//                        usedPart.substring(0, usedPart.length() - 1)
//                );
//            }
//            else {
//                throw new IllegalStateException("Unknown mem unit: " + usedPart);
//            }
//
//            // convert to MB (ceiling to catch partial MB)
//            int usedMb = (int)Math.ceil(usedBytes / 1024 / 1024);
//            task.setMemoryUsedMb(usedMb);
//
//            // finally, remove the container
//            new ProcessBuilder("docker", "rm", containerName).start()
//                    .waitFor();

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
    public ResponseEntity<String> getLogs(@PathVariable Long id) {
        Task task = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such task"));

        String logPath = task.getLogPath();
        if (logPath == null || logPath.isBlank()) {
            // the task never created a log
            return ResponseEntity
                    .status(HttpStatus.NO_CONTENT)
                    .body("No logs available for task " + id);
        }

        Path p = Paths.get(logPath);
        if (!Files.exists(p)) {
            // logPath was set but file isn’t on disk
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Log file not found at: " + logPath);
        }

        try {
            String contents = Files.readString(p);
            return ResponseEntity.ok(contents);
        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read log file" + e.getMessage()
            );
        }
    }
}
