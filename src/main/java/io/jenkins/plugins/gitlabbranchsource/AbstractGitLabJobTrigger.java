package io.jenkins.plugins.gitlabbranchsource;

import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.Timer;

public abstract class AbstractGitLabJobTrigger<E> {

    private static final Logger LOGGER = Logger.getLogger(AbstractGitLabJobTrigger.class.getName());

    private final E payload;

    protected AbstractGitLabJobTrigger(E payload) {
        this.payload = payload;
    }

    /**
     * Matches the trigger against jobs and schedules builds on the calling thread.
     *
     * @param trigger the trigger
     */
    public static void fireNow(AbstractGitLabJobTrigger trigger) {
        trigger.isMatch();
    }

    /**
     * Matches the trigger against jobs and schedules builds on a background thread, so the webhook
     * request returns without waiting for the GitLab API calls matching can make (project members,
     * the credential's user). GitLab times out slow webhook deliveries and disables hooks that keep
     * failing.
     *
     * @param trigger the trigger
     * @return the background task
     */
    public static Future<?> fireLater(AbstractGitLabJobTrigger trigger) {
        return Timer.get().submit(() -> {
            try {
                trigger.isMatch();
            } catch (RuntimeException e) {
                LOGGER.log(
                        Level.WARNING,
                        "GitLab job trigger " + trigger.getClass().getSimpleName() + " failed",
                        e);
            }
        });
    }

    public E getPayload() {
        return this.payload;
    }

    public abstract void isMatch();
}
