package com.jiexiangtech.chess.domain;

import java.util.List;

public final class San {
    private San() {}

    public static String format(Position p, Move move) {
        List<Move> legal = Rules.legalMoves(p);
        if (!legal.contains(move)) throw new IllegalArgumentException("Illegal move: " + move.uci());
        return format(p, move, legal);
    }

    static String format(Position p, Move move, List<Move> legal) {
        Kind kind = p.piece(move.from()).kind();
        StringBuilder name = new StringBuilder(8);
        if (kind == Kind.KING && Math.abs(move.to() - move.from()) == 2) {
            name.append(move.to() > move.from() ? "O-O" : "O-O-O");
        } else {
            boolean capture = p.captured(move) != null;
            String from = Square.name(move.from());
            if (kind == Kind.PAWN) {
                if (capture) name.append(from.charAt(0));
            } else {
                name.append(kind.symbol);
                boolean ambiguous = false, sameRank = false, sameFile = false;
                for (Move other : legal) {
                    if (other.from() == move.from() || other.to() != move.to() || p.piece(other.from()).kind() != kind) continue;
                    ambiguous = true;
                    sameRank |= Square.rank(other.from()) == Square.rank(move.from());
                    sameFile |= Square.file(other.from()) == Square.file(move.from());
                }
                if (ambiguous) {
                    if (!sameFile || sameRank) name.append(from.charAt(0));
                    if (sameFile) name.append(from.charAt(1));
                }
            }
            if (capture) name.append('x');
            name.append(Square.name(move.to()));
            if (move.promotion() != null) name.append('=').append(move.promotion().symbol);
        }
        Position next = p.afterGeneratedMove(move);
        if (Rules.inCheck(next, next.turn())) name.append(Rules.legalMoves(next).isEmpty() ? '#' : '+');
        return name.toString();
    }

    public static Move parse(Position p, String token) {
        List<Move> legal = Rules.legalMoves(p);
        String expected = normalize(token);
        for (Move move : legal) {
            if (normalize(format(p, move, legal)).equals(expected)) return move;
        }
        throw new IllegalArgumentException("Illegal SAN: " + token);
    }

    private static String normalize(String token) {
        return token.replace('0', 'O').replaceAll("[+#!?]+$", "");
    }
}
