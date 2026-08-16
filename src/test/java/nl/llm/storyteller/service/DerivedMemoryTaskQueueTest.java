package nl.llm.storyteller.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DerivedMemoryTaskQueueTest {
    @Test
    void shouldRunBackgroundTasksSequentially() throws Exception {
        DerivedMemoryTaskQueue queue = new DerivedMemoryTaskQueue();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try {
            queue.submit(() -> {
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            });
            queue.submit(secondStarted::countDown);

            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            assertFalse(secondStarted.await(100, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            queue.close();
        }
    }
}
