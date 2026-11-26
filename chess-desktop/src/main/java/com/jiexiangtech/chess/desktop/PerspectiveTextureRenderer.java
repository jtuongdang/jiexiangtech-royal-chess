package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import com.jiexiangtech.chess.application.Preferences;

/**
 * Warps tile-light.png and tile-dark.png across all 64 perspective squares using exact inverse
 * homography texture mapping and caches the resulting offscreen image for 60 FPS playback.
 */
final class PerspectiveTextureRenderer {
    private record CacheKey(int width, int height, boolean flipped, String theme,
                            Preferences.GraphicsQuality quality, double dpiScale) {}

    private final AssetManager assets;
    private CacheKey key;
    private BufferedImage cachedSurface;
    private Rectangle cachedBounds;

    PerspectiveTextureRenderer(AssetManager assets) {
        this.assets = assets;
    }

    void paintWarpedBoard(Graphics2D g, PerspectiveCoordinateMapper mapper, String theme,
                          Preferences.GraphicsQuality quality) {
        var tx = g.getTransform();
        double dpi = Math.max(1.0, Math.min(2.0, Math.hypot(tx.getScaleX(), tx.getShearY())));
        double renderScale = switch (quality) {
            case LOW -> 0.75;
            case MEDIUM -> 1.0;
            case HIGH -> Math.min(1.5, dpi);
            case ULTRA -> dpi;
        };
        CacheKey nextKey = new CacheKey(mapper.width(), mapper.height(), mapper.flipped(), theme, quality, renderScale);
        if (!nextKey.equals(key) || cachedSurface == null) {
            rebuild(mapper, theme, quality, renderScale);
            key = nextKey;
        }
        if (cachedSurface != null && cachedBounds != null) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    quality == Preferences.GraphicsQuality.LOW
                            ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                            : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(cachedSurface, cachedBounds.x, cachedBounds.y, cachedBounds.width, cachedBounds.height, null);
        }
    }

    private void rebuild(PerspectiveCoordinateMapper mapper, String theme,
                         Preferences.GraphicsQuality quality, double scale) {
        PerspectiveProjection proj = mapper.projection();
        var tl = proj.project(0, 0);
        var tr = proj.project(1, 0);
        var br = proj.project(1, 1);
        var bl = proj.project(0, 1);

        int bx = (int) Math.floor(Math.min(tl.x, bl.x)) - 1;
        int by = (int) Math.floor(Math.min(tl.y, tr.y)) - 1;
        int bw = (int) Math.ceil(Math.max(tr.x, br.x)) - bx + 2;
        int bh = (int) Math.ceil(Math.max(bl.y, br.y)) - by + 2;
        if (bw <= 2 || bh <= 2) return;

        int iw = Math.max(4, (int) Math.round(bw * scale));
        int ih = Math.max(4, (int) Math.round(bh * scale));
        BufferedImage surface = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
        int[] dst = ((DataBufferInt) surface.getRaster().getDataBuffer()).getData();

        BufferedImage lightTile = assets.tile(true);
        BufferedImage darkTile = assets.tile(false);
        int lw = lightTile != null ? lightTile.getWidth() : 1;
        int lh = lightTile != null ? lightTile.getHeight() : 1;
        int dw = darkTile != null ? darkTile.getWidth() : 1;
        int dh = darkTile != null ? darkTile.getHeight() : 1;
        int[] lightPx = lightTile != null ? lightTile.getRGB(0, 0, lw, lh, null, 0, lw) : new int[]{0xFFD8C29D};
        int[] darkPx = darkTile != null ? darkTile.getRGB(0, 0, dw, dh, null, 0, dw) : new int[]{0xFF211B16};

        Color lightGlaze = ThemeManager.glaze(theme, true);
        Color darkGlaze = ThemeManager.glaze(theme, false);
        boolean bilinear = quality == Preferences.GraphicsQuality.HIGH || quality == Preferences.GraphicsQuality.ULTRA;

        double invScale = 1.0 / scale;
        double[] uv = new double[2];
        for (int py = 0; py < ih; py++) {
            double sy = by + (py + 0.5) * invScale;
            int rowOff = py * iw;
            for (int px = 0; px < iw; px++) {
                double sx = bx + (px + 0.5) * invScale;
                proj.unprojectInto(sx, sy, uv);
                double u = uv[0], v = uv[1];
                if (u < 0.0 || u >= 1.0 || v < 0.0 || v >= 1.0) continue;

                double gu = u * 8.0, gv = v * 8.0;
                int col = Math.min(7, (int) gu);
                int row = Math.min(7, (int) gv);
                boolean isLight = (col + row) % 2 == 0;
                double tu = gu - col, tv = gv - row;

                int argb = isLight
                        ? sample(lightPx, lw, lh, tu, tv, bilinear)
                        : sample(darkPx, dw, dh, tu, tv, bilinear);

                // Dramatic 3D tabletop radial spotlight: warm golden irradiance in center (cols C-F, rows 2-6),
                // natural depth vignette towards far corners, and crisp inter-tile bevel line
                double du = (u - 0.50) / 0.56;
                double dv = (v - 0.54) / 0.58;
                double radial = Math.max(0.0, 1.0 - (du * du + dv * dv));
                double spotBoost = radial * radial * (isLight ? 0.22 : 0.28);
                double depthShade = (0.76 + 0.22 * v) + spotBoost;

                // Subtle bevel darkening right along tile seams (tu or tv near 0 or 1)
                double edgeDist = Math.min(Math.min(tu, 1.0 - tu), Math.min(tv, 1.0 - tv));
                if (edgeDist < 0.022) {
                    depthShade *= 0.86 + 0.14 * (edgeDist / 0.022);
                }

                int r = (argb >> 16) & 0xFF;
                int gr = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                Color glaze = isLight ? lightGlaze : darkGlaze;
                if (glaze != null) {
                    float a = glaze.getAlpha() / 255f;
                    r = Math.round(r * (1 - a) + glaze.getRed() * a);
                    gr = Math.round(gr * (1 - a) + glaze.getGreen() * a);
                    b = Math.round(b * (1 - a) + glaze.getBlue() * a);
                }
                // Warm golden light tint at the center of the spotlight
                int warmR = (int) Math.round(r * depthShade + spotBoost * 34);
                int warmG = (int) Math.round(gr * depthShade + spotBoost * 22);
                int warmB = (int) Math.round(b * depthShade + spotBoost * 4);
                dst[rowOff + px] = (0xFF << 24) | (clamp(warmR) << 16) | (clamp(warmG) << 8) | clamp(warmB);
            }
        }
        cachedSurface = surface;
        cachedBounds = new Rectangle(bx, by, bw, bh);
    }

    private static int sample(int[] pixels, int w, int h, double u, double v, boolean bilinear) {
        if (w <= 1 || h <= 1) return pixels[0];
        if (!bilinear) {
            int x = Math.min(w - 1, Math.max(0, (int) (u * w)));
            int y = Math.min(h - 1, Math.max(0, (int) (v * h)));
            return pixels[y * w + x];
        }
        double fx = u * (w - 1);
        double fy = v * (h - 1);
        int x0 = Math.min(w - 1, Math.max(0, (int) fx));
        int y0 = Math.min(h - 1, Math.max(0, (int) fy));
        int x1 = Math.min(w - 1, x0 + 1);
        int y1 = Math.min(h - 1, y0 + 1);
        double tx = fx - x0, ty = fy - y0;
        int c00 = pixels[y0 * w + x0], c10 = pixels[y0 * w + x1];
        int c01 = pixels[y1 * w + x0], c11 = pixels[y1 * w + x1];
        int r = bilerp((c00 >> 16) & 0xFF, (c10 >> 16) & 0xFF, (c01 >> 16) & 0xFF, (c11 >> 16) & 0xFF, tx, ty);
        int g = bilerp((c00 >> 8) & 0xFF, (c10 >> 8) & 0xFF, (c01 >> 8) & 0xFF, (c11 >> 8) & 0xFF, tx, ty);
        int b = bilerp(c00 & 0xFF, c10 & 0xFF, c01 & 0xFF, c11 & 0xFF, tx, ty);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static int bilerp(int c00, int c10, int c01, int c11, double tx, double ty) {
        double top = c00 + (c10 - c00) * tx;
        double bot = c01 + (c11 - c01) * tx;
        return clamp((int) Math.round(top + (bot - top) * ty));
    }

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(255, v); }
}
