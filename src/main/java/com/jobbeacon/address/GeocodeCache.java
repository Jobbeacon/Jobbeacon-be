package com.jobbeacon.address;

import com.jobbeacon.configuration.GeocodeProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GeocodeCache {

    private final ConcurrentHashMap<String, Entry> values = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private long nextAt;
    private final long intervalNanos;

    public GeocodeCache(GeocodeProperties properties) {
        Duration interval = properties.minInterval() == null ? Duration.ofSeconds(1) : properties.minInterval();
        this.intervalNanos = Math.max(0, interval.toNanos());
    }

    public String get(String key) {
        Entry entry = values.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt < System.currentTimeMillis()) {
            values.remove(key, entry);
            return null;
        }
        return entry.wardCode;
    }

    public void put(String key, String wardCode, Duration ttl) {
        values.put(key, new Entry(wardCode, System.currentTimeMillis() + ttl.toMillis()));
    }

    public void awaitTurn() {
        if (intervalNanos == 0) {
            return;
        }
        long waitNanos;
        synchronized (lock) {
            long now = System.nanoTime();
            waitNanos = Math.max(0, nextAt - now);
            nextAt = Math.max(now, nextAt) + intervalNanos;
        }
        if (waitNanos == 0) {
            return;
        }
        try {
            Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Entry(String wardCode, long expiresAt) {
    }
}
