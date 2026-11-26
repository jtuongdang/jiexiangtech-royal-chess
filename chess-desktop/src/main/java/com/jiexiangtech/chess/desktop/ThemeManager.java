package com.jiexiangtech.chess.desktop;

import java.awt.Color;
import java.util.List;

final class ThemeManager {
    static final String ROYAL = "royal";
    static final String SLATE = "royal-slate";
    private static final Color LIGHT_SLATE_GLAZE = new Color(104, 149, 172, 77);
    private static final Color DARK_SLATE_GLAZE = new Color(36, 65, 86, 101);

    private ThemeManager() {}

    static String[] available(List<String> achievements) {
        return achievements.contains("puzzle-master") ? new String[]{ROYAL, SLATE} : new String[]{ROYAL};
    }

    static Color glaze(String theme, boolean light) {
        return SLATE.equals(theme) ? (light ? LIGHT_SLATE_GLAZE : DARK_SLATE_GLAZE) : null;
    }
}
