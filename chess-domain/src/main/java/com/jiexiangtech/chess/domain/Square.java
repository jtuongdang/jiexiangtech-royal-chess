package com.jiexiangtech.chess.domain;

public final class Square {
    private Square() {}
    public static int at(int file, int rank) { return rank * 8 + file; }
    public static int file(int square) { return square & 7; }
    public static int rank(int square) { return square >>> 3; }
    public static boolean valid(int file, int rank) { return file >= 0 && file < 8 && rank >= 0 && rank < 8; }
    public static boolean valid(int square) { return square >= 0 && square < 64; }

    public static int parse(String text) {
        if (text.length() != 2 || !valid(text.charAt(0) - 'a', text.charAt(1) - '1')) {
            throw new IllegalArgumentException("Invalid square: " + text);
        }
        return at(text.charAt(0) - 'a', text.charAt(1) - '1');
    }

    public static String name(int square) {
        if (!valid(square)) throw new IllegalArgumentException("Invalid square index");
        return "" + (char) ('a' + file(square)) + (char) ('1' + rank(square));
    }
}
