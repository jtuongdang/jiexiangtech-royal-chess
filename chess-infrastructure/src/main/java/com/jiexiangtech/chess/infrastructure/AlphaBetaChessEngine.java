package com.jiexiangtech.chess.infrastructure;

public final class AlphaBetaChessEngine extends MinimaxChessEngine {
    public AlphaBetaChessEngine(boolean expert) {
        super(expert ? 7 : 5, true, expert ? 3 : 0);
    }
}
