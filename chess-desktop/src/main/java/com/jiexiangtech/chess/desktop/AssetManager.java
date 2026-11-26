package com.jiexiangtech.chess.desktop;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;

final class AssetManager {
    private static final String ROOT = "assets/themes/royal/";
    private final Map<String, BufferedImage> images = new HashMap<>();
    private final List<String> missing = new ArrayList<>();

    AssetManager() {
        for (Side side : Side.values()) for (Kind kind : Kind.values()) load(piecePath(Piece.of(side, kind)));
        for (String name : List.of("board", "board-grid", "tile-light", "tile-dark")) load("board/" + name + ".png");
        for (String path : List.of(BrandingManager.SPLASH, BrandingManager.LOGIN, BrandingManager.DASHBOARD,
                BrandingManager.GAME, BrandingManager.COMPANY, BrandingManager.EMBLEM, "branding/app-icon.png")) {
            load(path, "assets/" + path);
        }
        for (int size : new int[]{16, 32, 64, 128, 256, 512}) {
            String path = "icons/app-icon-" + size + ".png";
            load(path, "assets/" + path);
        }
        for (String path : List.of(ROOT + "theme.json", "assets/themes/royal-slate/theme.json",
                "assets/sounds/move.wav", "assets/sounds/capture.wav", "assets/sounds/check.wav", "assets/sounds/result.wav")) {
            if (AssetManager.class.getClassLoader().getResource(path) == null) missing.add(path);
        }
    }

    BufferedImage piece(Piece piece) { return images.get(piecePath(piece)); }
    BufferedImage board() { return images.get("board/board.png"); }
    BufferedImage tile(boolean light) { return images.get(light ? "board/tile-light.png" : "board/tile-dark.png"); }
    List<String> missing() { return List.copyOf(missing); }
    BufferedImage brand(String path) { return images.get(path); }

    List<Image> windowIcons() {
        var list = new ArrayList<Image>();
        for (int size : new int[]{16, 32, 64, 128, 256, 512}) {
            var image = brand("icons/app-icon-" + size + ".png");
            if (image != null) list.add(image);
        }
        return List.copyOf(list);
    }

    static BufferedImage read(String path) throws IOException {
        try (var stream = AssetManager.class.getClassLoader().getResourceAsStream(path)) {
            return stream == null ? null : ImageIO.read(stream);
        }
    }

    private static String piecePath(Piece piece) {
        return "pieces/" + piece.side().name().toLowerCase(Locale.ROOT) + "/"
                + piece.kind().name().toLowerCase(Locale.ROOT) + ".png";
    }

    private void load(String path) { load(path, ROOT + path); }

    private void load(String key, String resource) {
        try {
            BufferedImage image = read(resource);
            if (image == null) missing.add(resource);
            else images.put(key, image);
        } catch (IOException e) {
            missing.add(resource);
        }
    }
}
