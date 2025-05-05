package com.example.p3;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Task {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String command;
    private int memMb;
    private int timeoutSec;

    private int memoryUsedMb; // peak memory used by this task

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    private int exitCode;
    private String logPath;

    private Instant created;
    private Instant started;
    private Instant ended;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public int getTimeoutSec() {
        return timeoutSec;
    }

    public void setTimeoutSec(int timeoutSec) {
        this.timeoutSec = timeoutSec;
    }

    public int getMemMb() {
        return memMb;
    }

    public void setMemMb(int memMb) {
        this.memMb = memMb;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getStarted() {
        return started;
    }

    public void setStarted(Instant started) {
        this.started = started;
    }

    public Instant getEnded() {
        return ended;
    }

    public void setEnded(Instant ended) {
        this.ended = ended;
    }

    public int getMemoryUsedMb() {
        return memoryUsedMb;
    }

    public void setMemoryUsedMb(int memoryUsedMb) {
        this.memoryUsedMb = memoryUsedMb;
    }
}
