package com.tmp.core.support;

import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestPlatformComponent implements PlatformComponent {

    private final String id;
    private final AtomicInteger initializeCount = new AtomicInteger();
    private final AtomicInteger startCount = new AtomicInteger();
    private final AtomicInteger stopCount = new AtomicInteger();

    public TestPlatformComponent(String id) {
        this.id = id;
    }

    @Override
    public PlatformComponentMetadata metadata() {
        return new PlatformComponentMetadata(id, "Test " + id, "0.0.1", ComponentType.PLATFORM);
    }

    @Override
    public void initialize(PlatformCore platformCore) {
        initializeCount.incrementAndGet();
    }

    @Override
    public void start() {
        startCount.incrementAndGet();
    }

    @Override
    public void stop() {
        stopCount.incrementAndGet();
    }

    public int startCount() {
        return startCount.get();
    }

    public int stopCount() {
        return stopCount.get();
    }
}
