package com.jiexiangtech.chess.application;

import java.util.Set;

public record Preferences(String language, String theme, boolean sound, boolean animation,
                          Orientation orientation, int pieceScale, boolean highQuality, boolean autosave,
                          ViewMode viewMode, GraphicsQuality graphicsQuality) {
    public enum Orientation { AUTO, WHITE, BLACK }
    public enum ViewMode { CLASSIC_2D, ROYAL_FOCUS_25D }
    public enum GraphicsQuality { LOW, MEDIUM, HIGH, ULTRA }

    public Preferences {
        if (!Set.of("en", "vi").contains(language) || !Set.of("royal", "royal-slate").contains(theme)
                || orientation == null || pieceScale < 70 || pieceScale > 105
                || viewMode == null || graphicsQuality == null) {
            throw new IllegalArgumentException("Invalid preferences");
        }
    }

    public Preferences(String language, String theme, boolean sound, boolean animation,
                       Orientation orientation, int pieceScale, boolean highQuality, boolean autosave) {
        this(language, theme, sound, animation, orientation, pieceScale, highQuality, autosave,
                ViewMode.CLASSIC_2D, highQuality ? GraphicsQuality.HIGH : GraphicsQuality.MEDIUM);
    }

    public Preferences withViewMode(ViewMode next) {
        return new Preferences(language, theme, sound, animation, orientation, pieceScale,
                highQuality, autosave, next, graphicsQuality);
    }

    public static Preferences defaults(String language) {
        return new Preferences(language, "royal", true, true, Orientation.AUTO, 97, true, true,
                ViewMode.ROYAL_FOCUS_25D, GraphicsQuality.HIGH);
    }
}
