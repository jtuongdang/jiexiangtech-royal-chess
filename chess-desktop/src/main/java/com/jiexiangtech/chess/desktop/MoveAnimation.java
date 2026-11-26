package com.jiexiangtech.chess.desktop;

import java.awt.Point;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Move;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Position;
import com.jiexiangtech.chess.domain.Square;

record MoveAnimation(Move move, Piece piece, Point release, Move rook, Piece captured, int capturedSquare) {
    static MoveAnimation from(Position before, Move move, Point release) {
        if (before == null || move == null) return null;
        Piece piece = before.piece(move.from());
        if (piece == null) return null;
        int travel = Square.file(move.to()) - Square.file(move.from());
        Move rook = null;
        if (piece.kind() == Kind.KING && Math.abs(travel) == 2) {
            int rank = Square.rank(move.from());
            rook = new Move(Square.at(travel > 0 ? 7 : 0, rank), Square.at(travel > 0 ? 5 : 3, rank));
        }
        Piece captured = before.captured(move);
        int capturedSquare = captured != null && before.piece(move.to()) == null
                ? move.to() - 8 * before.turn().forward : move.to();
        return new MoveAnimation(move, piece, release, rook, captured, capturedSquare);
    }

    int durationMillis() {
        if (release != null) return 160;
        double distance = Math.hypot(Square.file(move.to()) - Square.file(move.from()),
                Square.rank(move.to()) - Square.rank(move.from()));
        return 185 + (int) Math.round(15 * Math.min(7, distance));
    }

    static double ease(double t) { return t * t * (3.0 - 2.0 * t); }

    static double lerp(double from, double to, double progress) { return from + (to - from) * progress; }

    static float captureAlpha(double t) {
        return (float) (1 - ease(Math.max(0, (t - 0.38) / 0.62)));
    }
}
