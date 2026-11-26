package com.jiexiangtech.chess.infrastructure;

import java.util.ArrayList;
import java.util.List;
import com.jiexiangtech.chess.domain.*;

final class PositionEvaluator {
    private static final int[] CENTER = {27, 28, 35, 36};
    private static final int[][] PIECE_SQUARE = pieceSquareTables();

    int evaluate(Position position, int mobility) {
        int[] totals = new int[2];
        int[][] pawns = new int[2][8];
        List<Integer> pawnSquares = new ArrayList<>(16);
        for (int square = 0; square < 64; square++) {
            Piece piece = position.piece(square);
            if (piece == null) continue;
            int side = piece.side().ordinal();
            int oriented = piece.side() == Side.WHITE ? square : square ^ 56;
            totals[side] += piece.kind().value + PIECE_SQUARE[piece.kind().ordinal()][oriented];
            if (piece.kind() == Kind.PAWN) {
                pawns[side][Square.file(square)]++;
                pawnSquares.add(square);
            } else if (piece.kind() != Kind.KING && Rules.attacked(position, square, piece.side().opposite())
                    && !Rules.attacked(position, square, piece.side())) {
                totals[side] -= piece.kind().value / 18;
            }
        }
        for (int square : pawnSquares) {
            Piece pawn = position.piece(square);
            int side = pawn.side().ordinal(), file = Square.file(square);
            int progress = pawn.side() == Side.WHITE ? Square.rank(square) : 7 - Square.rank(square);
            if (pawns[side][file] > 1) totals[side] -= 9;
            if ((file == 0 || pawns[side][file - 1] == 0) && (file == 7 || pawns[side][file + 1] == 0)) totals[side] -= 12;
            boolean passed = true;
            for (int targetFile = Math.max(0, file - 1); targetFile <= Math.min(7, file + 1); targetFile++) {
                for (int rank = Square.rank(square) + pawn.side().forward; rank >= 0 && rank < 8; rank += pawn.side().forward) {
                    if (position.piece(Square.at(targetFile, rank)) == Piece.of(pawn.side().opposite(), Kind.PAWN)) passed = false;
                }
            }
            if (passed) totals[side] += progress * 6;
        }
        for (Side side : Side.values()) {
            int king = position.king(side), file = Square.file(king), rank = Square.rank(king);
            for (int center : CENTER) if (Rules.attacked(position, center, side)) totals[side.ordinal()] += 6;
            for (int f = file - 1; f <= file + 1; f++) {
                int front = rank + side.forward;
                if (!Square.valid(f, front)) continue;
                int square = Square.at(f, front);
                if (position.piece(square) == Piece.of(side, Kind.PAWN)) totals[side.ordinal()] += 7;
                if (Rules.attacked(position, square, side.opposite())) totals[side.ordinal()] -= 9;
            }
        }
        return totals[position.turn().ordinal()] - totals[position.turn().opposite().ordinal()] + mobility * 2 - 40;
    }

    private static int[][] pieceSquareTables() {
        int[][] scores = new int[Kind.values().length][64];
        for (int square = 0; square < 64; square++) {
            int rank = Square.rank(square), file = Square.file(square);
            int centrality = 14 - Math.abs(2 * file - 7) - Math.abs(2 * rank - 7);
            for (Kind kind : Kind.values()) {
                scores[kind.ordinal()][square] = switch (kind) {
                    case PAWN -> rank * 5 + centrality;
                    case KNIGHT -> centrality * 4;
                    case BISHOP -> centrality * 2;
                    case ROOK -> rank * 2 + (rank == 6 ? 15 : 0);
                    case QUEEN -> centrality;
                    case KING -> rank == 0 && (file == 6 || file == 2) ? 18 : -centrality;
                };
            }
        }
        return scores;
    }
}
