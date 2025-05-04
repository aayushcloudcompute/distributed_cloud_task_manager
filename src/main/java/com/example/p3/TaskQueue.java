package com.example.p3;

import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class TaskQueue {
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

    public void enqueue(Task t) {
        queue.offer(t);
    }

    public Task peek() {
        return queue.peek();
    }

    public Task poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
