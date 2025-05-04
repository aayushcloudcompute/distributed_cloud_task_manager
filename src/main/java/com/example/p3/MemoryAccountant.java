package com.example.p3;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MemoryAccountant {
    private final AtomicInteger freeMemMb;

    public MemoryAccountant(NodeProperties props) {
        this.freeMemMb = new AtomicInteger(props.getTotalMemMb());
    }

    public boolean tryReserve(int mb) {
        while (true) {
            int cur = freeMemMb.get();
            if (cur < mb) return false;
            if (freeMemMb.compareAndSet(cur, cur - mb)) return true;
        }
    }

    public void release(int mb) {
        freeMemMb.addAndGet(mb);
    }

    public int getFree() {
        return freeMemMb.get();
    }
}
