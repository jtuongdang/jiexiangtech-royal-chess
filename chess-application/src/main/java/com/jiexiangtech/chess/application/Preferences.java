package com.jiexiangtech.chess.application;

import java.util.Set;

public record Preferences(String language, String theme, boolean sound, boolean animation,
                          Orientation orientation, int pieceScale, boolean highQuality, boolean autosave) {
    public enum Orientation { AUTO, WHITE, BLACK }

    public Preferences {
        if (!Set.of("en", "vi").contains(language) || !Set.of("royal", "royal-slate").contains(theme) || orientation == null
                || pieceScale < 70 || pieceScale > 105) throw new IllegalArgumentException("Invalid preferences");
    }

    public static Preferences defaults(String language) {
        return new Preferences(language, "royal", true, true, Orientation.AUTO, 97, true, true);
    }
}
