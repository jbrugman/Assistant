package nl.llm.storyteller.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmBackendGuardTest {
    @Test
    @DisplayName("""
        Given repeated backend failures up to the configured threshold,
        When another call is attempted during the cooldown window,
        Then the guard should fail fast without calling the backend again
        """)
    void shouldFailFastAfterConfiguredFailureThreshold() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T10:00:00Z"));
        LlmBackendGuard guard = new LlmBackendGuard("Chat backend", 2, 30, clock);
        AtomicInteger delegateCalls = new AtomicInteger();

        assertThrows(IOException.class, () -> guard.execute(() -> fail(delegateCalls)));
        assertThrows(IOException.class, () -> guard.execute(() -> fail(delegateCalls)));

        IOException cooldownError = assertThrows(
            IOException.class,
            () -> guard.execute(() -> {
                delegateCalls.incrementAndGet();
                return "should not run";
            })
        );

        assertEquals(2, delegateCalls.get());
        assertTrue(guard.isOpen());
        assertTrue(cooldownError.getMessage().contains("temporarily in cooldown"));
    }

    @Test
    @DisplayName("""
        Given an open backend guard whose cooldown has expired,
        When the next probe call succeeds,
        Then the guard should close again and reset the failure count
        """)
    void shouldCloseAgainAfterSuccessfulProbe() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T10:00:00Z"));
        LlmBackendGuard guard = new LlmBackendGuard("Background memory backend", 1, 10, clock);

        assertThrows(IOException.class, () -> guard.execute(() -> {
            throw new IOException("backend unavailable");
        }));
        assertTrue(guard.isOpen());

        clock.advanceSeconds();
        String response = guard.execute(() -> "ok");

        assertEquals("ok", response);
        assertFalse(guard.isOpen());
        assertEquals(0, guard.consecutiveFailures());
    }

    private String fail(AtomicInteger delegateCalls) throws IOException {
        delegateCalls.incrementAndGet();
        throw new IOException("backend unavailable");
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advanceSeconds() {
            now = now.plusSeconds(11);
        }
    }
}
