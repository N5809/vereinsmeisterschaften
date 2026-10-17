package de.dlrg.vereinsmeisterschaft.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Epochenaehnliche Referenzzeit, die innerhalb dieses Serverstarts monoton bleibt. */
@Component
public class ServerClock {
    private final long originNanos = System.nanoTime();
    private final long originMillis = System.currentTimeMillis();
    private volatile String bootId = UUID.randomUUID().toString();
    public double now() { return originMillis + (System.nanoTime() - originNanos) / 1_000_000.0; }
    public String bootId() { return bootId; }
    public void renewBootId() {bootId=UUID.randomUUID().toString();}
}
