package com.jiexiangtech.chess.domain;

import java.util.ArrayList;
import java.util.List;

public final class Rules {
    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] RAYS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[] PAWN_FILES = {-1, 1};
    private static final Kind[] PROMOTIONS = {Kind.QUEEN, Kind.ROOK, Kind.BISHOP, Kind.KNIGHT};

    private Rules() {}

    public static boolean inCheck(Position p, Side side) { return attacked(p, p.king(side), side.opposite()); }

    public static boolean attacked(Position p, int square, Side by) {
        int file = Square.file(square), rank = Square.rank(square);
        for (int delta : PAWN_FILES) {
            if (has(p, file + delta, rank - by.forward, by, Kind.PAWN)) return true;
        }
        for (int[] delta : KNIGHT) {
            if (has(p, file + delta[0], rank + delta[1], by, Kind.KNIGHT)) return true;
        }
        for (int ray = 0; ray < RAYS.length; ray++) {
            int dx = RAYS[ray][0], dy = RAYS[ray][1];
            for (int f = file + dx, r = rank + dy; Square.valid(f, r); f += dx, r += dy) {
                Piece piece = p.piece(Square.at(f, r));
                if (piece == null) continue;
                if (piece.side() == by && (piece.kind() == Kind.QUEEN || piece.kind() == (ray < 4 ? Kind.ROOK : Kind.BISHOP)
                        || (piece.kind() == Kind.KING && f == file + dx && r == rank + dy))) return true;
                break;
            }
        }
        return false;
    }

    private static boolean has(Position p, int file, int rank, Side side, Kind kind) {
        return Square.valid(file, rank) && p.piece(Square.at(file, rank)) == Piece.of(side, kind);
    }

    public static List<Move> legalMoves(Position p) {
        List<Move> moves = new ArrayList<>(40);
        for (int square = 0; square < 64; square++) addMoves(p, square, moves);
        return moves;
    }

    public static List<Move> legalMoves(Position p, int from) {
        List<Move> moves = new ArrayList<>(12);
        addMoves(p, from, moves);
        return moves;
    }

    private static void addMoves(Position p, int from, List<Move> moves) {
        Piece piece = p.piece(from);
        if (piece == null || piece.side() != p.turn()) return;
        int file = Square.file(from), rank = Square.rank(from);
        switch (piece.kind()) {
            case PAWN -> pawns(p, from, file, rank, moves);
            case KNIGHT -> {
                for (int[] step : KNIGHT) walk(p, from, file, rank, step, 1, moves);
            }
            case KING -> {
                for (int[] step : RAYS) walk(p, from, file, rank, step, 1, moves);
                castle(p, from, true, moves);
                castle(p, from, false, moves);
            }
            case QUEEN, ROOK, BISHOP -> {
                int first = piece.kind() == Kind.BISHOP ? 4 : 0;
                int end = piece.kind() == Kind.ROOK ? 4 : 8;
                for (int i = first; i < end; i++) walk(p, from, file, rank, RAYS[i], 7, moves);
            }
        }
    }

    private static void walk(Position p, int from, int file, int rank, int[] step, int limit, List<Move> moves) {
        for (int n = 1; n <= limit; n++) {
            int f = file + step[0] * n, r = rank + step[1] * n;
            if (!Square.valid(f, r)) break;
            int to = Square.at(f, r);
            Piece target = p.piece(to);
            if (target == null || (target.side() != p.turn() && target.kind() != Kind.KING)) {
                addIfSafe(p, new Move(from, to), moves);
            }
            if (target != null) break;
        }
    }

    private static void pawns(Position p, int from, int file, int rank, List<Move> moves) {
        int nextRank = rank + p.turn().forward;
        int to = Square.at(file, nextRank);
        if (p.piece(to) == null) {
            pawnMove(p, from, to, moves);
            if (rank == (p.turn() == Side.WHITE ? 1 : 6)) {
                int doublePush = to + 8 * p.turn().forward;
                if (p.piece(doublePush) == null) addIfSafe(p, new Move(from, doublePush), moves);
            }
        }
        for (int delta : PAWN_FILES) {
            if (!Square.valid(file + delta, nextRank)) continue;
            int capture = Square.at(file + delta, nextRank);
            Piece target = p.piece(capture);
            if (capture == p.enPassant() || (target != null && target.side() != p.turn() && target.kind() != Kind.KING)) {
                pawnMove(p, from, capture, moves);
            }
        }
    }

    private static void pawnMove(Position p, int from, int to, List<Move> moves) {
        if (Square.rank(to) == 0 || Square.rank(to) == 7) {
            for (Kind kind : PROMOTIONS) addIfSafe(p, new Move(from, to, kind), moves);
        } else addIfSafe(p, new Move(from, to), moves);
    }

    private static void castle(Position p, int from, boolean shortSide, List<Move> moves) {
        if (!p.mayCastle(shortSide) || inCheck(p, p.turn())) return;
        int delta = shortSide ? 1 : -1;
        int rook = from + (shortSide ? 3 : -4);
        if (p.piece(rook) != Piece.of(p.turn(), Kind.ROOK)) return;
        for (int square = from + delta; square != rook; square += delta) {
            if (p.piece(square) != null) return;
        }
        if (inCheck(p.afterGeneratedMove(new Move(from, from + delta)), p.turn())) return;
        addIfSafe(p, new Move(from, from + 2 * delta), moves);
    }

    private static void addIfSafe(Position p, Move move, List<Move> moves) {
        if (!inCheck(p.afterGeneratedMove(move), p.turn())) moves.add(move);
    }

    static boolean hasLegalEnPassant(Position p) {
        int to = p.enPassant(), rank = Square.rank(to) - p.turn().forward;
        for (int delta : PAWN_FILES) {
            int file = Square.file(to) + delta;
            if (has(p, file, rank, p.turn(), Kind.PAWN)
                    && !inCheck(p.afterGeneratedMove(new Move(Square.at(file, rank), to)), p.turn())) return true;
        }
        return false;
    }

    // A proven subset of dead positions based on remaining mating material.
    public static boolean canPossiblyMate(Position p, Side winner) {
        int ownKnights = 0, ownBishops = 0, enemyNonKing = 0, bishopColors = 0;
        boolean bishopsOnly = true;
        for (int square = 0; square < 64; square++) {
            Piece piece = p.piece(square);
            if (piece == null || piece.kind() == Kind.KING) continue;
            if (piece.kind() == Kind.BISHOP) bishopColors |= 1 << ((Square.file(square) + Square.rank(square)) % 2);
            else bishopsOnly = false;
            if (piece.side() != winner) {
                enemyNonKing++;
                continue;
            }
            switch (piece.kind()) {
                case QUEEN, ROOK, PAWN -> { return true; }
                case KNIGHT -> ownKnights++;
                case BISHOP -> ownBishops++;
                default -> throw new IllegalStateException();
            }
        }
        if (ownBishops + ownKnights == 0) return false;
        if (bishopsOnly && bishopColors != 3) return false;
        if (ownBishops + ownKnights == 1) return enemyNonKing > 0;
        return true;
    }

    public static boolean dead(Position p) {
        return (!canPossiblyMate(p, Side.WHITE) && !canPossiblyMate(p, Side.BLACK)) || sealedPawnPosition(p);
    }

    // A pawn wall is permanent if no pawn can advance or capture and neither king
    // can reach a pawn it could capture. Ignoring the other king enlarges the search.
    private static boolean sealedPawnPosition(Position p) {
        boolean pawnFound = false;
        for (int square = 0; square < 64; square++) {
            Piece piece = p.piece(square);
            if (piece == null || piece.kind() == Kind.KING) continue;
            if (piece.kind() != Kind.PAWN) return false;
            pawnFound = true;
            int rank = Square.rank(square) + piece.side().forward;
            int file = Square.file(square);
            Piece ahead = p.piece(Square.at(file, rank));
            if (ahead == null || ahead.kind() != Kind.PAWN) return false;
            for (int delta : PAWN_FILES) {
                if (!Square.valid(file + delta, rank)) continue;
                Piece target = p.piece(Square.at(file + delta, rank));
                if (target != null && target.side() != piece.side()) return false;
            }
        }
        return pawnFound && p.enPassant() < 0
                && !canReachPawn(p, Side.WHITE) && !canReachPawn(p, Side.BLACK);
    }

    private static boolean canReachPawn(Position p, Side side) {
        boolean[] seen = new boolean[64];
        int[] queue = new int[64];
        int start = p.king(side);
        seen[start] = true;
        queue[0] = start;
        for (int head = 0, tail = 1; head < tail; head++) {
            int from = queue[head];
            for (int[] step : RAYS) {
                int file = Square.file(from) + step[0], rank = Square.rank(from) + step[1];
                if (!Square.valid(file, rank)) continue;
                int to = Square.at(file, rank);
                if (seen[to]) continue;
                seen[to] = true;
                boolean attacked = false;
                for (int offset : PAWN_FILES) {
                    attacked |= has(p, file + offset, rank + side.forward, side.opposite(), Kind.PAWN);
                }
                if (attacked) continue;
                Piece target = p.piece(to);
                if (target != null && target.kind() == Kind.PAWN) {
                    if (target.side() != side) return true;
                } else {
                    queue[tail++] = to;
                }
            }
        }
        return false;
    }

    public static long perft(Position p, int depth) {
        if (depth < 0) throw new IllegalArgumentException("Negative depth");
        if (depth == 0) return 1;
        List<Move> moves = legalMoves(p);
        if (depth == 1) return moves.size();
        long nodes = 0;
        for (Move move : moves) nodes += perft(p.afterGeneratedMove(move), depth - 1);
        return nodes;
    }
}
