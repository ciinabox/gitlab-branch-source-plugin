package io.jenkins.plugins.gitlabbranchsource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AbstractGitLabJobTriggerTest {

    private static final class RecordingTrigger extends AbstractGitLabJobTrigger<String> {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<Thread> thread = new AtomicReference<>();
        final boolean fail;

        RecordingTrigger(boolean fail) {
            super("payload");
            this.fail = fail;
        }

        @Override
        public void isMatch() {
            thread.set(Thread.currentThread());
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            done.countDown();
            if (fail) {
                throw new IllegalStateException("boom");
            }
        }
    }

    @Test
    void fireLaterReturnsBeforeMatchingFinishes() throws Exception {
        RecordingTrigger trigger = new RecordingTrigger(false);
        AbstractGitLabJobTrigger.fireLater(trigger);
        // matching is still blocked on release, so returning proves the caller didn't wait
        assertEquals(1, trigger.done.getCount());
        trigger.release.countDown();
        assertTrue(trigger.done.await(10, TimeUnit.SECONDS));
        assertNotSame(Thread.currentThread(), trigger.thread.get());
    }

    @Test
    void fireLaterContainsFailures() throws Exception {
        RecordingTrigger trigger = new RecordingTrigger(true);
        trigger.release.countDown();
        AbstractGitLabJobTrigger.fireLater(trigger).get(10, TimeUnit.SECONDS);
        assertEquals(0, trigger.done.getCount());
    }
}
