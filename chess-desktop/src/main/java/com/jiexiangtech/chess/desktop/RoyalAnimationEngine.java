package com.jiexiangtech.chess.desktop;

import java.awt.Component;
import javax.swing.Timer;

final class RoyalAnimationEngine {
    private static final double MOVE_DURATION_NS = 190_000_000.0;
    private static final double CELEBRATION_DURATION_NS = 1_250_000_000.0;
    private static final double OVERLAY_DURATION_NS = 320_000_000.0;

    private final Timer timer;
    private boolean enabled = true;
    private long moveStartedAt;
    private long celebrationStartedAt;
    private long overlayStartedAt;
    private boolean celebrating;
    private boolean overlayVisible;
    private boolean ambientPulseActive;

    RoyalAnimationEngine(Component target) {
        timer = new Timer(16, event -> {
            if (celebrating && celebrationProgress() >= 1.0) celebrating = false;
            boolean moveDone = moveProgress() >= 1.0;
            boolean overlayDone = !overlayVisible || overlayProgress() >= 1.0;
            if (moveDone && !celebrating && overlayDone && !ambientPulseActive) {
                ((Timer) event.getSource()).stop();
            }
            target.repaint();
        });
        timer.setCoalesce(true);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            celebrating = false;
            ambientPulseActive = false;
            timer.stop();
        }
    }

    void triggerMove() {
        moveStartedAt = enabled ? System.nanoTime() : 0;
        if (moveStartedAt != 0) timer.start();
    }

    void clearMove() {
        moveStartedAt = 0;
    }

    void triggerCelebration() {
        if (!enabled) return;
        celebrating = true;
        celebrationStartedAt = System.nanoTime();
        timer.start();
    }

    void setAmbientPulse(boolean active) {
        this.ambientPulseActive = enabled && active;
        if (this.ambientPulseActive) timer.start();
    }

    void triggerOverlayFade() {
        overlayVisible = true;
        overlayStartedAt = enabled ? System.nanoTime() : 0;
        if (overlayStartedAt != 0) timer.start();
    }

    void clearOverlay() {
        overlayVisible = false;
        overlayStartedAt = 0;
    }

    double moveProgress() {
        if (moveStartedAt == 0 || !enabled) return 1.0;
        return Math.min(1.0, (System.nanoTime() - moveStartedAt) / MOVE_DURATION_NS);
    }

    double easedMoveProgress() {
        double t = moveProgress();
        return 1.0 - Math.pow(1.0 - t, 3);
    }

    boolean isCelebrating() { return celebrating; }

    double celebrationProgress() {
        if (!celebrating || celebrationStartedAt == 0) return 1.0;
        return Math.min(1.0, (System.nanoTime() - celebrationStartedAt) / CELEBRATION_DURATION_NS);
    }

    double overlayProgress() {
        if (!overlayVisible) return 0.0;
        if (overlayStartedAt == 0 || !enabled) return 1.0;
        return Math.min(1.0, (System.nanoTime() - overlayStartedAt) / OVERLAY_DURATION_NS);
    }

    /** Smooth sinusoidal pulse [0..1] for selection glow and check warning when active. */
    float pulse() {
        if (!enabled) return 0.5f;
        return (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 420_000_000.0));
    }

    void stop() {
        ambientPulseActive = false;
        celebrating = false;
        timer.stop();
    }
}
