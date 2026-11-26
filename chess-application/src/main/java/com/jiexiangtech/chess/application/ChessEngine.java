package com.jiexiangtech.chess.application;

import java.time.Duration;
import java.util.Optional;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Position;

public interface ChessEngine {
    record Analysis(Move move, int centipawns, int depth, long nodes) {}
    Optional<Analysis> analyze(Position position, Duration budget);
}
