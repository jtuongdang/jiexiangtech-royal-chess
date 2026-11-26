package com.jiexiangtech.chess.domain;

import java.util.Arrays;

public final class Position {
    public static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    static final int WHITE_SHORT = 1, WHITE_LONG = 2, BLACK_SHORT = 4, BLACK_LONG = 8;
    private static final String RIGHTS = "KQkq";
    private final Piece[] pieces;
    private final Side turn;
    private final int castling, enPassant, halfmove, fullmove;

    private Position(Piece[] pieces, Side turn, int castling, int enPassant, int halfmove, int fullmove) {
        this.pieces = pieces;
        this.turn = turn;
        this.castling = castling;
        this.enPassant = enPassant;
        this.halfmove = halfmove;
        this.fullmove = fullmove;
    }

    public static Position initial() { return fromFen(INITIAL_FEN); }
    public Piece piece(int square) { return pieces[square]; }
    public Side turn() { return turn; }
    public int enPassant() { return enPassant; }
    public int halfmove() { return halfmove; }
    public int fullmove() { return fullmove; }

    public int king(Side side) {
        for (int square = 0; square < 64; square++) {
            if (pieces[square] == Piece.of(side, Kind.KING)) return square;
        }
        throw new IllegalStateException("Missing king");
    }

    boolean mayCastle(boolean shortSide) {
        int bit = shortSide ? WHITE_SHORT : WHITE_LONG;
        return (castling & (turn == Side.WHITE ? bit : bit << 2)) != 0;
    }

    public Piece captured(Move move) {
        if (pieces[move.from()].kind() == Kind.PAWN && move.to() == enPassant && pieces[move.to()] == null) {
            return pieces[move.to() - 8 * turn.forward];
        }
        return pieces[move.to()];
    }

    // Callers that already hold generated legal moves can use this path without regenerating them.
    public Position afterGeneratedMove(Move move) {
        Piece[] next = pieces.clone();
        Piece mover = next[move.from()];
        Piece captured = captured(move);
        next[move.from()] = null;
        next[move.to()] = move.promotion() == null ? mover : Piece.of(turn, move.promotion());
        int rights = castling;
        if (mover.kind() == Kind.KING) {
            rights &= turn == Side.WHITE ? ~(WHITE_SHORT | WHITE_LONG) : ~(BLACK_SHORT | BLACK_LONG);
            if (Math.abs(move.to() - move.from()) == 2) {
                boolean shortSide = move.to() > move.from();
                int rook = move.from() + (shortSide ? 3 : -4);
                next[move.from() + (shortSide ? 1 : -1)] = next[rook];
                next[rook] = null;
            }
        }
        if (mover.kind() == Kind.ROOK) rights &= ~rookRight(move.from());
        if (captured != null && captured.kind() == Kind.ROOK) rights &= ~rookRight(move.to());
        boolean pawn = mover.kind() == Kind.PAWN;
        if (pawn && move.to() == enPassant && pieces[move.to()] == null) next[enPassant - turn.forward * 8] = null;
        int target = pawn && Math.abs(move.to() - move.from()) == 16 ? (move.to() + move.from()) / 2 : -1;
        return new Position(next, turn.opposite(), rights, target, pawn || captured != null ? 0 : halfmove + 1,
                fullmove + (turn == Side.BLACK ? 1 : 0));
    }

    private static int rookRight(int square) {
        return switch (square) {
            case 0 -> WHITE_LONG;
            case 7 -> WHITE_SHORT;
            case 56 -> BLACK_LONG;
            case 63 -> BLACK_SHORT;
            default -> 0;
        };
    }

    public static Position fromFen(String fen) {
        String[] fields = fen.strip().split("\\s+");
        if (fields.length != 6) throw new IllegalArgumentException("FEN requires six fields");
        Piece[] pieces = new Piece[64];
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) throw new IllegalArgumentException("FEN requires eight ranks");
        for (int rank = 0; rank < 8; rank++) {
            int file = 0;
            for (char symbol : ranks[7 - rank].toCharArray()) {
                if (symbol >= '1' && symbol <= '8') file += symbol - '0';
                else {
                    Kind kind = Kind.from(symbol);
                    if (file >= 8 || (kind == Kind.PAWN && (rank == 0 || rank == 7))) {
                        throw new IllegalArgumentException("Invalid FEN placement");
                    }
                    pieces[Square.at(file++, rank)] = Piece.of(Character.isUpperCase(symbol) ? Side.WHITE : Side.BLACK, kind);
                }
            }
            if (file != 8) throw new IllegalArgumentException("FEN rank width must be eight");
        }
        for (Side side : Side.values()) {
            if (Arrays.stream(pieces).filter(p -> p == Piece.of(side, Kind.KING)).count() != 1) {
                throw new IllegalArgumentException("FEN requires exactly one king per side");
            }
        }
        Side turn = switch (fields[1]) {
            case "w" -> Side.WHITE;
            case "b" -> Side.BLACK;
            default -> throw new IllegalArgumentException("Invalid FEN active color");
        };
        int rights = 0;
        if (!fields[2].equals("-")) for (char symbol : fields[2].toCharArray()) {
            int ix = RIGHTS.indexOf(symbol);
            if (ix < 0 || (rights & (1 << ix)) != 0) throw new IllegalArgumentException("Invalid castling rights");
            rights |= 1 << ix;
        }
        for (Side side : Side.values()) {
            int backRank = side == Side.WHITE ? 0 : 56;
            for (int rook : new int[]{backRank, backRank + 7}) {
                if ((rights & rookRight(rook)) != 0 && (pieces[backRank + 4] != Piece.of(side, Kind.KING)
                        || pieces[rook] != Piece.of(side, Kind.ROOK))) throw new IllegalArgumentException("Missing castling pieces");
            }
        }
        int ep = fields[3].equals("-") ? -1 : Square.parse(fields[3]);
        if (ep >= 0 && (Square.rank(ep) != (turn == Side.WHITE ? 5 : 2) || pieces[ep] != null
                || pieces[ep - 8 * turn.forward] != Piece.of(turn.opposite(), Kind.PAWN)
                || pieces[ep + 8 * turn.forward] != null)) {
            throw new IllegalArgumentException("Invalid en passant square");
        }
        int halfmove = Integer.parseInt(fields[4]), fullmove = Integer.parseInt(fields[5]);
        if (halfmove < 0 || halfmove == Integer.MAX_VALUE || fullmove < 1 || fullmove == Integer.MAX_VALUE
                || (ep >= 0 && halfmove != 0)) throw new IllegalArgumentException("Invalid move counters");
        Position position = new Position(pieces, turn, rights, ep, halfmove, fullmove);
        if (Rules.inCheck(position, turn.opposite())) throw new IllegalArgumentException("Inactive king is in check");
        return position;
    }

    public String fen() { return identity(enPassant) + " " + halfmove + " " + fullmove; }

    public String repetitionKey() {
        return identity(enPassant >= 0 && Rules.hasLegalEnPassant(this) ? enPassant : -1);
    }

    private String identity(int target) {
        StringBuilder fen = new StringBuilder(80);
        for (int rank = 7; rank >= 0; rank--) {
            if (rank < 7) fen.append('/');
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = pieces[Square.at(file, rank)];
                if (piece == null) empty++;
                else {
                    if (empty > 0) fen.append(empty);
                    empty = 0;
                    fen.append(piece.fen());
                }
            }
            if (empty > 0) fen.append(empty);
        }
        fen.append(turn == Side.WHITE ? " w " : " b ");
        if (castling == 0) fen.append('-');
        else for (int bit = 0; bit < 4; bit++) if ((castling & (1 << bit)) != 0) fen.append(RIGHTS.charAt(bit));
        return fen.append(' ').append(target < 0 ? "-" : Square.name(target)).toString();
    }

    @Override public String toString() { return fen(); }
}
