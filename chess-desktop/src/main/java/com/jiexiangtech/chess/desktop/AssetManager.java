package com.jiexiangtech.chess.desktop;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
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
    private static final int ALPHA_THRESHOLD = 16;

    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Map<Piece, Rectangle> contentBounds = new HashMap<>();
    private final List<String> missing = new ArrayList<>();

    AssetManager() {
        for (Side side : Side.values()) {
            for (Kind kind : Kind.values()) {
                Piece piece = Piece.of(side, kind);
                String key = piecePath(piece);
                load(key);
                BufferedImage img = images.get(key);
                if (img != null) contentBounds.put(piece, computeVisibleBounds(img));
            }
        }
        for (String name : List.of("board", "board-grid", "tile-light", "tile-dark")) load("board/" + name + ".png");
        for (String path : List.of(BrandingManager.SPLASH, BrandingManager.LOGIN, BrandingManager.DASHBOARD,
                BrandingManager.GAMEPLAY_BG, BrandingManager.GAME, BrandingManager.COMPANY,
                BrandingManager.COMPANY_HORIZONTAL, BrandingManager.EMBLEM, "branding/app-icon.png")) {
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

    Rectangle pieceBounds(Piece piece) {
        Rectangle bounds = contentBounds.get(piece);
        if (bounds != null) return bounds;
        BufferedImage img = piece(piece);
        return img == null ? new Rectangle(0, 0, 512, 512) : new Rectangle(0, 0, img.getWidth(), img.getHeight());
    }

    /** Draws the visible content of a chess piece proportionally fitted and bottom-aligned inside the target rectangle. */
    void drawPieceFitted(Graphics2D g, Piece piece, int x, int y, int width, int height) {
        BufferedImage img = piece(piece);
        if (img == null || width <= 0 || height <= 0) return;
        Rectangle src = pieceBounds(piece);
        double scale = Math.min((double) width / src.width, (double) height / src.height);
        int dw = Math.max(1, (int) Math.round(src.width * scale));
        int dh = Math.max(1, (int) Math.round(src.height * scale));
        int dx = x + (width - dw) / 2;
        int dy = y + height - dh;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, dx, dy, dx + dw, dy + dh,
                src.x, src.y, src.x + src.width, src.y + src.height, null);
    }

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

    private static Rectangle computeVisibleBounds(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int left = w, right = -1, top = h, bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = image.getRGB(x, y) >>> 24;
                if (alpha <= ALPHA_THRESHOLD) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                if (y > bottom) bottom = y;
            }
        }
        if (right < left || bottom < top) return new Rectangle(0, 0, w, h);
        // Add 1px safety margin inside image bounds for anti-aliased edges
        left = Math.max(0, left - 1);
        top = Math.max(0, top - 1);
        right = Math.min(w - 1, right + 1);
        bottom = Math.min(h - 1, bottom + 1);
        return new Rectangle(left, top, right - left + 1, bottom - top + 1);
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
