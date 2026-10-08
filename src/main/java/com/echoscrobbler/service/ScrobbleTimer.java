package com.echoscrobbler.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.echoscrobbler.model.Track;

public class ScrobbleTimer {

    private static final long MAX_SECONDS = 240;
    private static final long UNKNOWN_DURATION_SECONDS = 90;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "echo-scrobble-timer");
            thread.setDaemon(true);
            return thread;
        });

    private ScheduledFuture<?> pending;
    private Runnable callback;

    private long remainingNanos;
    private long resumedAtNanos;
    private long generation;

    private boolean running;
    private boolean completed;
    private double percentage = 0.5;

    public synchronized void setPercentage(double percentage) {
        if (!Double.isFinite(percentage)) {
            throw new IllegalArgumentException("Invalid percentage");
        }

        this.percentage = Math.max(0.5, Math.min(1.0, percentage));
    }

    public synchronized void start(Track track, Runnable onScrobble) {
        cancel();

        long duration = track.getDurationSeconds();

        // last.fm excludes tracks lasting 30 seconds or less.
        if (duration > 0 && duration <= 30) {
            return;
        }

        long threshold = duration > 0
            ? Math.min((long) Math.ceil(duration * percentage), MAX_SECONDS)
            : UNKNOWN_DURATION_SECONDS;

        callback = onScrobble;
        remainingNanos = TimeUnit.SECONDS.toNanos(threshold);
        completed = false;

        System.out.println("Scrobble threshold: " + threshold + "s");
        resume();
    }

    public synchronized void pause() {
        if (!running) {
            return;
        }

        long elapsed = System.nanoTime() - resumedAtNanos;
        remainingNanos = Math.max(0, remainingNanos - elapsed);

        running = false;
        generation++;

        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }

    public synchronized void resume() {
        if (running || completed || callback == null) {
            return;
        }

        running = true;
        resumedAtNanos = System.nanoTime();
        long token = ++generation;

        pending = scheduler.schedule(
            () -> finish(token),
            remainingNanos,
            TimeUnit.NANOSECONDS
        );
    }

    private void finish(long token) {
        Runnable action;

        synchronized (this) {
            if (token != generation || !running || completed) {
                return;
            }

            running = false;
            completed = true;
            remainingNanos = 0;
            pending = null;

            action = callback;
            callback = null;
        }

        // execute outside the lock so network work cannot block pause/cancel.
        action.run();
    }

    public synchronized void cancel() {
        generation++;

        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }

        callback = null;
        remainingNanos = 0;
        running = false;
        completed = false;
    }

    public synchronized void shutdown() {
        cancel();
        scheduler.shutdownNow();
    }
}