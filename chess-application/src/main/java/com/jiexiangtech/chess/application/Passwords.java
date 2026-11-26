package com.jiexiangtech.chess.application;

public interface Passwords {
    String hash(char[] password);
    boolean matches(char[] password, String encoded);
}
