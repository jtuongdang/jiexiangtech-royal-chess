package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;

final class RoyalPieceRenderer {
    private record CacheKey(Piece piece, int pw, int ph, boolean highQuality) {}

    private static final int MAX_CACHE = 128;
    private final AssetManager assets;
    private final Map<CacheKey, BufferedImage> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<CacheKey, BufferedImage> eldest) {
            return size() > MAX_CACHE;
        }
    };

    RoyalPieceRenderer(AssetManager assets) {
        this.assets = assets;
    }

    static double heightRatio(Kind kind) {
        return switch (kind) {
            case KING -> 0.94;
            case QUEEN -> 0.90;
            case BISHOP -> 0.84;
            case KNIGHT -> 0.82;
            case ROOK -> 0.76;
            case PAWN -> 0.67;
        };
    }

    void drawPiece(Graphics2D g, Piece piece, Rectangle tile, int pieceScalePercent,
                   boolean highQuality, boolean onDarkSquare, boolean dragged) {
        if (piece == null) return;
        BufferedImage source = assets.piece(piece);
        Rectangle srcBounds = assets.pieceBounds(piece);

        double userScale = pieceScalePercent / 97.0;
        double lift = dragged ? 1.07 : 1.0;
        double targetH = tile.height * heightRatio(piece.kind()) * userScale * lift;
        double maxW = tile.width * 0.86 * lift;
        double scale = Math.min(targetH / srcBounds.height, maxW / srcBounds.width);

        int dw = Math.max(4, (int) Math.round(srcBounds.width * scale));
        int dh = Math.max(4, (int) Math.round(srcBounds.height * scale));
        int baseY = tile.y + (int) Math.round(tile.height * 0.915) - (dragged ? Math.max(4, tile.height / 10) : 0);
        int dx = tile.x + (tile.width - dw) / 2;
        int dy = baseY - dh;

        if (piece.side() == Side.BLACK && onDarkSquare && !dragged) {
            g.setColor(new Color(228, 192, 102, 42));
            g.fillOval(dx - 3, dy + dh / 5, dw + 6, dh * 3 / 4);
        }

        int shadowW = Math.max(8, (int) Math.round(dw * (dragged ? 1.08 : 0.98)));
        int shadowH = Math.max(4, (int) Math.round(tile.height * (dragged ? 0.16 : 0.135)));
        int shadowX = tile.x + (tile.width - shadowW) / 2;
        int shadowY = tile.y + (int) Math.round(tile.height * 0.915) - shadowH / 2 + (dragged ? 3 : 0);
        g.setColor(new Color(0, 0, 0, dragged ? 78 : 108));
        g.fillOval(shadowX - 2, shadowY, shadowW + 4, shadowH + 2);
        g.setColor(new Color(0, 0, 0, dragged ? 90 : 155));
        g.fillOval(shadowX + shadowW / 10, shadowY + 1, shadowW * 4 / 5, Math.max(2, shadowH * 2 / 3));

        if (source == null) {
            g.setColor(piece.side() == Side.WHITE ? RoyalStyle.IVORY : new Color(0x201913));
            g.fillOval(dx, dy, dw, dh);
            return;
        }

        AffineTransform tx = g.getTransform();
        double dpiScale = Math.max(1.0, Math.min(2.0, Math.hypot(tx.getScaleX(), tx.getShearY())));
        int pw = Math.max(4, (int) Math.round(dw * dpiScale));
        int ph = Math.max(4, (int) Math.round(dh * dpiScale));

        BufferedImage cached = scaledSprite(piece, source, srcBounds, pw, ph, highQuality);
        g.drawImage(cached, dx, dy, dw, dh, null);
    }

    /**
     * Ultra-fast 60 FPS 2.5D billboard piece renderer using pre-cached Retina sprites and lightweight oval shadows
     * (zero per-frame RadialGradientPaint allocations or source image rescaling).
     */
    Dimension drawPerspectivePiece(Graphics2D g, Piece piece, double anchorX, double anchorY, double localTileWidth,
                                   int pieceScalePercent, boolean highQuality, boolean onDarkSquare,
                                   double liftPixels, ShadowRenderer shadows, boolean directionalShadow) {
        if (piece == null) return new Dimension(0, 0);
        BufferedImage source = assets.piece(piece);
        Rectangle srcBounds = assets.pieceBounds(piece);

        double userScale = pieceScalePercent / 97.0;
        double targetH = localTileWidth * 1.16 * heightRatio(piece.kind()) * userScale;
        double maxW = localTileWidth * 0.88 * userScale;
        double scale = Math.min(targetH / srcBounds.height, maxW / (srcBounds.width * 1.18));

        int dw = Math.max(4, (int) Math.round(srcBounds.width * scale * 1.18));
        int dh = Math.max(4, (int) Math.round(srcBounds.height * scale));
        int dx = (int) Math.round(anchorX - dw / 2.0);
        int dy = (int) Math.round(anchorY - dh - liftPixels);

        if (shadows != null) {
            shadows.paintPieceShadow(g, anchorX, anchorY, dw, localTileWidth, liftPixels, directionalShadow);
        }

        // Fast lightweight contrast halos (single alpha oval instead of RadialGradientPaint allocation)
        if (piece.side() == Side.BLACK && onDarkSquare && liftPixels < 2.0) {
            g.setColor(new Color(235, 196, 102, 40));
            g.fillOval(dx - 4, dy + dh / 5, dw + 8, dh * 3 / 4);
        } else if (piece.side() == Side.WHITE && liftPixels < 2.0) {
            g.setColor(new Color(246, 204, 98, 26));
            g.fillOval(dx - 2, (int) Math.round(anchorY - localTileWidth * 0.14), dw + 4, (int) Math.round(localTileWidth * 0.28));
        }

        if (source == null) {
            g.setColor(piece.side() == Side.WHITE ? RoyalStyle.IVORY : new Color(0x201913));
            g.fillOval(dx, dy, dw, dh);
            return new Dimension(dw, dh);
        }

        AffineTransform tx = g.getTransform();
        double dpiScale = Math.max(1.0, Math.min(2.0, Math.hypot(tx.getScaleX(), tx.getShearY())));
        int pw = Math.max(4, (int) Math.round(dw * dpiScale));
        int ph = Math.max(4, (int) Math.round(dh * dpiScale));

        BufferedImage cached = scaledSprite(piece, source, srcBounds, pw, ph, highQuality);
        g.drawImage(cached, dx, dy, dw, dh, null);
        return new Dimension(dw, dh);
    }

    private BufferedImage scaledSprite(Piece piece, BufferedImage source, Rectangle srcBounds,
                                       int targetW, int targetH, boolean highQuality) {
        CacheKey key = new CacheKey(piece, targetW, targetH, highQuality);
        BufferedImage existing = cache.get(key);
        if (existing != null) return existing;

        BufferedImage cropped = source.getSubimage(srcBounds.x, srcBounds.y, srcBounds.width, srcBounds.height);
        BufferedImage scaled = highQuality ? downscaleProgressive(cropped, targetW, targetH)
                : scaleDirect(cropped, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        cache.put(key, scaled);
        return scaled;
    }

    private static BufferedImage downscaleProgressive(BufferedImage src, int targetW, int targetH) {
        BufferedImage current = src;
        int w = src.getWidth(), h = src.getHeight();
        while (w / 2 >= targetW && h / 2 >= targetH) {
            w = Math.max(targetW, w / 2);
            h = Math.max(targetH, h / 2);
            current = scaleDirect(current, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (current.getWidth() != targetW || current.getHeight() != targetH) {
            current = scaleDirect(current, targetW, targetH, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return current;
    }

    private static BufferedImage scaleDirect(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }
}
