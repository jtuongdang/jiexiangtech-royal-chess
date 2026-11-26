package com.jiexiangtech.chess.domain;

import java.util.function.LongSupplier;

public final class GameClock {
    public record Times(long whiteMillis, long blackMillis) {
        public Times {
            if (whiteMillis < 0 || blackMillis < 0) throw new IllegalArgumentException("Negative clock");
        }
        public long get(Side side) { return side == Side.WHITE ? whiteMillis : blackMillis; }
        public Times with(Side side, long millis) {
            return side == Side.WHITE ? new Times(millis, blackMillis) : new Times(whiteMillis, millis);
        }
    }

    private final LongSupplier nanos;
    private final boolean timed;
    private final long incrementMillis;
    private Times saved;
    private Side running;
    private long started;

    public GameClock(boolean timed, long incrementMillis, Times saved, LongSupplier nanos) {
        if (incrementMillis < 0) throw new IllegalArgumentException("Negative increment");
        this.timed = timed;
        this.incrementMillis = incrementMillis;
        this.saved = saved;
        this.nanos = nanos;
    }

    public void start(Side side) {
        if (!timed) return;
        if (running != null) throw new IllegalStateException("Clock already running");
        running = side;
        started = nanos.getAsLong();
    }

    public Times read() {
        if (running == null) return saved;
        long elapsed = (nanos.getAsLong() - started) / 1_000_000;
        return saved.with(running, Math.max(0, saved.get(running) - elapsed));
    }

    public void pause() { saved = read(); running = null; }
    public boolean expired() { return running != null && read().get(running) == 0; }
    public void addIncrement(Side side) {
        if (running != null) throw new IllegalStateException("Pause before adding increment");
        if (timed) saved = saved.with(side, saved.get(side) + incrementMillis);
    }
    public void restore(Times times) { running = null; saved = times; }
}
