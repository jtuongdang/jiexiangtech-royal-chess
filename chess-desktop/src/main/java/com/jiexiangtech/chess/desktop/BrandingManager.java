package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class BrandingManager {
    static final String SPLASH = "backgrounds/splash-background.png";
    static final String LOGIN = "backgrounds/login-background.png";
    static final String DASHBOARD = "backgrounds/dashboard-background.png";
    static final String GAMEPLAY_BG = "backgrounds/gameplay-background.png";
    static final String FOCUS_BG = "backgrounds/royal-focus-background.png";
    static final String ARENA_BG = "backgrounds/royal-arena.jpg";
    static final String GAME = "branding/game-logo.png";
    static final String COMPANY = "branding/company-logo.png";
    static final String COMPANY_HORIZONTAL = "branding/company-horizontal.png";
    static final String EMBLEM = "branding/company-emblem.png";

    private final AssetManager assets;

    BrandingManager(AssetManager assets) { this.assets = assets; }

    JPanel background(String image, LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                BufferedImage artwork = assets.brand(image);
                if (artwork == null) {
                    g.setPaint(new GradientPaint(0, 0, SURFACE, getWidth(), getHeight(), new Color(0x463318)));
                    g.fillRect(0, 0, getWidth(), getHeight());
                } else {
                    drawCover(g, artwork, getWidth(), getHeight());
                }
                g.dispose();
            }
        };
        panel.setBackground(BG);
        return panel;
    }

    JComponent logo(String image, int width, int height, String accessibleName) {
        BufferedImage logo = assets.brand(image);
        if (logo == null) return label(accessibleName, PALE);
        JLabel label = new JLabel() {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                drawContain(g, logo, Math.min(width, getWidth()), Math.min(height, getHeight()), getWidth(), getHeight());
                g.dispose();
            }
        };
        label.setPreferredSize(new Dimension(width, height));
        label.setMaximumSize(new Dimension(width, height));
        label.setMinimumSize(new Dimension(Math.min(width, 32), Math.min(height, 24)));
        label.getAccessibleContext().setAccessibleName(accessibleName);
        label.setToolTipText(accessibleName);
        return label;
    }

    void apply(Window window) {
        List<Image> icons = assets.windowIcons();
        if (!icons.isEmpty()) window.setIconImages(icons);
    }

    static void drawCover(Graphics2D g, BufferedImage image, int width, int height) {
        double scale = Math.max((double) width / image.getWidth(), (double) height / image.getHeight());
        draw(g, image, width, height, scale);
    }

    static void drawContain(Graphics2D g, BufferedImage image, int maxWidth, int maxHeight, int width, int height) {
        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());
        draw(g, image, width, height, scale);
    }

    private static void draw(Graphics2D g, BufferedImage image, int width, int height, double scale) {
        int w = (int) Math.round(image.getWidth() * scale), h = (int) Math.round(image.getHeight() * scale);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
    }
}
