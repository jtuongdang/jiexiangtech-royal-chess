package com.jiexiangtech.chess.domain;

import java.util.Objects;

public record Outcome(Reason reason, Side winner) {
    public enum Reason {
        CHECKMATE, STALEMATE, DEAD_POSITION, THREEFOLD, FIVEFOLD, FIFTY_MOVE, SEVENTY_FIVE_MOVE,
        RESIGNATION, AGREEMENT, TIMEOUT
    }

    public Outcome {
        Objects.requireNonNull(reason);
        if (reason == Reason.CHECKMATE && winner == null) throw new IllegalArgumentException("Missing winner");
        if (winner != null && reason != Reason.CHECKMATE && reason != Reason.RESIGNATION && reason != Reason.TIMEOUT) {
            throw new IllegalArgumentException("Draw cannot have a winner");
        }
    }

    public String pgn() { return winner == null ? "1/2-1/2" : winner == Side.WHITE ? "1-0" : "0-1"; }
}
