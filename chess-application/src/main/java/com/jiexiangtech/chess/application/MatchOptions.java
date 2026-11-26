package com.jiexiangtech.chess.application;

import java.util.Objects;
import com.jiexiangtech.chess.domain.Side;

public record MatchOptions(Mode mode, Side human, Level level, long baseMillis, long incrementMillis, String theme) {
    public enum Mode { LOCAL, COMPUTER }
    public enum Level {
        EASY(700), MEDIUM(1000), HARD(1350), EXPERT(1700);
        public final int referenceRating;
        Level(int referenceRating) { this.referenceRating = referenceRating; }
    }

    public MatchOptions {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(human);
        Objects.requireNonNull(level);
        if (baseMillis < 0 || baseMillis > 86_400_000 || incrementMillis < 0 || incrementMillis > 60_000
                || (baseMillis == 0 && incrementMillis != 0) || Objects.requireNonNull(theme).isBlank()) {
            throw new IllegalArgumentException("Invalid match options");
        }
    }

    public boolean timed() { return baseMillis > 0; }
}
