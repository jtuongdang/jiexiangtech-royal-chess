package com.jiexiangtech.chess.desktop;

import com.jiexiangtech.chess.application.Preferences;

/** Coordinates live switching between Classic 2D and Royal Focus 2.5D without interrupting match state. */
final class FocusModeController {
    private Preferences.ViewMode mode;
    private boolean drawerOpen;

    FocusModeController(Preferences.ViewMode initialMode) {
        this.mode = initialMode == null ? Preferences.ViewMode.ROYAL_FOCUS_25D : initialMode;
    }

    Preferences.ViewMode mode() { return mode; }
    boolean isFocusMode() { return mode == Preferences.ViewMode.ROYAL_FOCUS_25D; }
    boolean isDrawerOpen() { return drawerOpen; }

    Preferences.ViewMode toggleMode() {
        mode = isFocusMode() ? Preferences.ViewMode.CLASSIC_2D : Preferences.ViewMode.ROYAL_FOCUS_25D;
        if (!isFocusMode()) drawerOpen = false;
        return mode;
    }

    void setMode(Preferences.ViewMode next) {
        this.mode = next;
        if (!isFocusMode()) drawerOpen = false;
    }

    boolean toggleDrawer() {
        drawerOpen = !drawerOpen;
        return drawerOpen;
    }

    void closeDrawer() { drawerOpen = false; }
}
