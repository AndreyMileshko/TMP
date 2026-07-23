package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.Session;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SessionContextTest {

    @Test
    void openCloseLifecycle() {
        SessionContext context = new SessionContext();
        assertTrue(context.current().isEmpty());
        assertFalse(context.isAuthenticated());
        Session session = Session.of(
                SessionId.generate(), UserId.generate(), Login.of("a"), Instant.now());
        context.open(session);
        assertTrue(context.isAuthenticated());
        assertEquals(session.id(), context.current().orElseThrow().id());
        context.close();
        assertTrue(context.current().isEmpty());
    }

    @Test
    void concurrentReadsSeeConsistentState() throws Exception {
        SessionContext context = new SessionContext();
        Session session = Session.of(
                SessionId.generate(), UserId.generate(), Login.of("a"), Instant.now());
        context.open(session);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger seen = new AtomicInteger();
        Thread[] readers = new Thread[8];
        for (int i = 0; i < readers.length; i++) {
            readers[i] = new Thread(() -> {
                try {
                    start.await();
                    if (context.current().isPresent()) {
                        seen.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            readers[i].start();
        }
        start.countDown();
        for (Thread reader : readers) {
            reader.join();
        }
        assertEquals(8, seen.get());
    }
}
