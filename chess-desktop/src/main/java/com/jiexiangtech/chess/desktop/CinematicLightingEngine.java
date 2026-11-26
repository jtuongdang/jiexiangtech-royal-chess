package com.jiexiangtech.chess.desktop;

import java.awt.*;
import com.jiexiangtech.chess.application.Preferences;

/** Orchestrates ambient darkness, overhead golden spotlight, and corner vignette for Royal Focus Mode. */
final class CinematicLightingEngine {
    private final SpotlightRenderer spotlight;
    private final VignetteRenderer vignette;

    CinematicLightingEngine(AssetManager assets) {
        this.spotlight = new SpotlightRenderer(assets);
        this.vignette = new VignetteRenderer(assets);
    }

    void paintBackdropLighting(Graphics2D g, int width, int height, Preferences.GraphicsQuality quality, float transition) {
        float spotStrength = switch (quality) {
            case LOW -> 0.45f;
            case MEDIUM -> 0.72f;
            case HIGH -> 0.92f;
            case ULTRA -> 1.0f;
        };
        spotlight.paint(g, width, height, spotStrength * transition);
    }

    void paintForegroundVignette(Graphics2D g, int width, int height, Preferences.GraphicsQuality quality, float transition) {
        if (quality == Preferences.GraphicsQuality.LOW) return;
        float vig = switch (quality) {
            case MEDIUM -> 0.55f;
            case HIGH -> 0.78f;
            case ULTRA -> 0.88f;
            default -> 0f;
        };
        vignette.paint(g, width, height, vig * transition);
    }
}
