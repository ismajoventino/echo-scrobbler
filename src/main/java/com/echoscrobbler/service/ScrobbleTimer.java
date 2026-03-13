package com.echoscrobbler.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.echoscrobbler.model.Track;

public class ScrobbleTimer {

    private static final long MIN_SECONDS = 90;
    private static final long MAX_SECONDS = 4 * 60;

    // percentage: value between 0.3 and 1.0
    private double percentage = 0.5;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> pending;

    public void setPercentage(double percentage) {
        this.percentage = Math.max(0.3, Math.min(1.0, percentage));
    }

    public void start(Track track, Runnable onScrobble) {
        cancel();

        long threshold;

        if (track.getDurationSeconds() > 0) {
            long calculated = (long)(track.getDurationSeconds() * percentage);
            threshold = Math.max(MIN_SECONDS, Math.min(calculated, MAX_SECONDS));
        } else {
            threshold = MIN_SECONDS;
        }


        System.out.println("ScrobbleTimer started, threshold: " + threshold + "s");
        pending = scheduler.schedule(onScrobble, threshold, TimeUnit.SECONDS);
    }

    public void cancel() {
        if (pending != null && !pending.isDone()) {
            pending.cancel(false);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}