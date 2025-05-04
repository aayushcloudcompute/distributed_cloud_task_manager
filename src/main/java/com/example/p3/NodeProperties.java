package com.example.p3;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "node")
public class NodeProperties {
    private int totalMemMb;

    public int getTotalMemMb() {
        return totalMemMb;
    }

    public void setTotalMemMb(int totalMemMb) {
        this.totalMemMb = totalMemMb;
    }
}
