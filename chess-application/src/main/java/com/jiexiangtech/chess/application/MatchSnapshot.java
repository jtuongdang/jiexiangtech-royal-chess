package com.jiexiangtech.chess.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.jiexiangtech.chess.domain.GameClock.Times;
import com.jiexiangtech.chess.domain.Outcome;

public record MatchSnapshot(UUID id, long owner, MatchOptions options, Instant started, Instant updated,
                            String initialFen, String currentFen, List<Ply> moves, Times times, Outcome outcome,
                            boolean assisted, long elapsedMillis) {
    public record Ply(String uci, String san, Times times) {}

    public MatchSnapshot {
        Objects.requireNonNull(id);
        Objects.requireNonNull(options);
        Objects.requireNonNull(started);
        Objects.requireNonNull(updated);
        Objects.requireNonNull(initialFen);
        Objects.requireNonNull(currentFen);
        Objects.requireNonNull(times);
        moves = List.copyOf(moves);
        if (elapsedMillis < 0) throw new IllegalArgumentException("Negative elapsed time");
    }
}
