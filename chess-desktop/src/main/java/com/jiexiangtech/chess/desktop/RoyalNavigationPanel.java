package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalNavigationPanel extends JPanel {
    record Item(String id, String label, RoyalIcons.Glyph glyph, Runnable action) {}

    private static final int EXPANDED_WIDTH = 194;
    private static final int COMPACT_WIDTH = 64;
    private final AssetManager assets;
    private final boolean compact;
    private boolean focusDimmed;
    private boolean hovered;
    private float fadeAlpha = 1.0f;
    private final Timer fadeTimer;

    RoyalNavigationPanel(BrandingManager branding, AssetManager assets, List<Item> items, String selectedId,
                         String playerName, String aboutLabel, String signOutLabel, String toggleTooltip,
                         boolean compact, Runnable onToggleCompact, Runnable onAbout, Runnable onSignOut) {
        super(new BorderLayout(0, 10));
        this.assets = assets;
        this.compact = compact;
        setOpaque(false);
        setPreferredSize(new Dimension(compact ? COMPACT_WIDTH : EXPANDED_WIDTH, 480));
        setBorder(new EmptyBorder(14, compact ? 8 : 12, 10, compact ? 9 : 12));
        fadeTimer = new Timer(16, e -> {
            float target = (!focusDimmed || hovered) ? 1.0f : 0.78f;
            float diff = target - fadeAlpha;
            if (Math.abs(diff) < 0.02f) {
                fadeAlpha = target;
                ((Timer) e.getSource()).stop();
            } else {
                fadeAlpha += diff * 0.22f;
            }
            repaint();
        });

        // Top brand mark + compact toggle
        JPanel top = transparent(new BorderLayout(0, 6));
        if (compact) {
            JPanel logoWrap = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
            logoWrap.add(branding.logo(BrandingManager.EMBLEM, 38, 30, BrandConstants.PRODUCT));
            top.add(logoWrap, BorderLayout.NORTH);
            JPanel toggleWrap = transparent(new FlowLayout(FlowLayout.CENTER, 0, 0));
            toggleWrap.add(new RoyalIconButton(RoyalIcons.Glyph.EXPAND, toggleTooltip, 15, 34, onToggleCompact));
            top.add(toggleWrap, BorderLayout.SOUTH);
        } else {
            JPanel headerRow = transparent(new BorderLayout(2, 0));
            headerRow.add(branding.logo(BrandingManager.GAME, 154, 84, BrandConstants.PRODUCT), BorderLayout.CENTER);
            JPanel btnWrap = transparent(new BorderLayout());
            btnWrap.add(new RoyalIconButton(RoyalIcons.Glyph.COLLAPSE, toggleTooltip, 14, 28, onToggleCompact), BorderLayout.NORTH);
            headerRow.add(btnWrap, BorderLayout.EAST);
            top.add(headerRow, BorderLayout.CENTER);
        }
        add(top, BorderLayout.NORTH);

        // Navigation menu items (including About inline just like reference)
        JPanel menu = transparent(new GridLayout(0, 1, 0, 4));
        for (Item item : items) {
            menu.add(new NavButton(item, item.id().equals(selectedId), compact));
        }
        menu.add(new NavButton(new Item("ABOUT", aboutLabel, RoyalIcons.Glyph.ABOUT, onAbout), false, compact));

        JPanel navWrap = transparent(new BorderLayout());
        navWrap.add(menu, BorderLayout.NORTH);
        add(navWrap, BorderLayout.CENTER);

        // Bottom decorative Knight + subtle Sign Out control
        if (compact) {
            JPanel compactFooter = transparent(new FlowLayout(FlowLayout.CENTER, 0, 4));
            compactFooter.add(new RoyalIconButton(RoyalIcons.Glyph.SIGN_OUT, signOutLabel, 16, 36, onSignOut));
            add(compactFooter, BorderLayout.SOUTH);
        } else {
            JPanel ornamentFooter = new JPanel(new BorderLayout(0, 4)) {
                @Override protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    // Warm golden floor aura behind decorative knight
                    RoyalGlowEffect.paintAmbientSpotlight(g, w * 0.44f, h * 0.84f, w * 0.62f, new Color(224, 178, 66, 76));
                    int kh = Math.min(h - 6, 134);
                    int kw = Math.min(w - 16, 126);
                    assets.drawPieceFitted(g, Piece.of(Side.BLACK, Kind.KNIGHT), (w - kw) / 2 - 12, h - kh - 2, kw, kh);
                    g.dispose();
                }
            };
            ornamentFooter.setOpaque(false);
            ornamentFooter.setPreferredSize(new Dimension(180, 138));
            JPanel signOutRow = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            RoyalIconButton outIcon = new RoyalIconButton(RoyalIcons.Glyph.SIGN_OUT,
                    playerName + " · " + signOutLabel, 15, 32, onSignOut);
            signOutRow.add(outIcon);
            ornamentFooter.add(signOutRow, BorderLayout.SOUTH);
            add(ornamentFooter, BorderLayout.SOUTH);
        }
        installHoverListeners(this);
    }

    void setFocusDimmed(boolean dimmed) {
        this.focusDimmed = dimmed;
        fadeAlpha = dimmed && !hovered ? 0.80f : 1.0f;
        repaint();
    }

    private void installHoverListeners(Container root) {
        var listener = new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (!hovered) { hovered = true; fadeTimer.start(); }
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), RoyalNavigationPanel.this);
                if (!contains(p) && hovered) { hovered = false; fadeTimer.start(); }
            }
        };
        root.addMouseListener(listener);
        for (Component c : root.getComponents()) {
            if (c instanceof Container sub) installHoverListeners(sub);
            else c.addMouseListener(listener);
        }
    }

    @Override public void removeNotify() { fadeTimer.stop(); super.removeNotify(); }

    @Override public void paint(Graphics graphics) {
        if (fadeAlpha >= 0.99f) {
            super.paint(graphics);
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.45f, fadeAlpha)));
        super.paint(g);
        g.dispose();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        int w = getWidth(), h = getHeight();
        // Translucent obsidian-bronze glass backdrop
        g.setPaint(new GradientPaint(0, 0, new Color(16, 14, 15, 232), 0, h, new Color(11, 10, 12, 240)));
        g.fillRect(0, 0, w, h);

        // Luminous metallic gold vertical right border (signature reference line)
        g.setPaint(new LinearGradientPaint(0, 0, 0, h,
                new float[]{0f, 0.30f, 0.72f, 1f},
                new Color[]{new Color(196, 152, 58, 120), new Color(242, 210, 118, 220),
                        new Color(186, 142, 52, 165), new Color(245, 212, 118, 215)}));
        g.fillRect(w - 2, 0, 2, h);
        // Soft warm inward golden reflection beside the border
        g.setPaint(new GradientPaint(w - 14, 0, new Color(212, 175, 55, 0), w - 2, 0, new Color(212, 175, 55, 26)));
        g.fillRect(w - 14, 0, 12, h);
        g.dispose();
    }

    private static final class NavButton extends JButton {
        private final RoyalIcons.Glyph glyph;
        private final boolean selected;

        NavButton(Item item, boolean selected, boolean compact) {
            super(compact ? "" : item.label());
            this.glyph = item.glyph();
            this.selected = selected;
            setToolTipText(item.label());
            getAccessibleContext().setAccessibleName(item.label());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setHorizontalAlignment(compact ? SwingConstants.CENTER : SwingConstants.LEFT);
            setIconTextGap(12);
            setBorder(compact ? new EmptyBorder(8, 0, 8, 0) : new EmptyBorder(9, 14, 9, 10));
            setFont(sans(selected ? Font.BOLD : Font.PLAIN, 13.5f));
            setForeground(selected ? IVORY : new Color(0xD8D1C4));
            setPreferredSize(new Dimension(compact ? 46 : 182, 38));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setIcon(RoyalIcons.icon(glyph, 17, selected ? PALE : new Color(0xD7B359)));
            addActionListener(e -> item.action().run());
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            boolean hover = getModel().isRollover();
            if (selected) {
                RoyalGlowEffect.paintOuterGlow(g, 2, 2, w - 4, h - 4, 11, new Color(235, 188, 72, 95), 2);
                g.setPaint(new GradientPaint(0, 0, new Color(186, 140, 44, 96), w, h, new Color(76, 56, 18, 62)));
                g.fillRoundRect(1, 1, w - 2, h - 2, 11, 11);
                RoyalGlowEffect.paintMetallicGoldBorder(g, 1, 1, w - 2, h - 2, 11, 1.5f, true);
            } else if (hover) {
                g.setColor(new Color(212, 175, 55, 24));
                g.fillRoundRect(1, 1, w - 2, h - 2, 11, 11);
                g.setColor(new Color(212, 175, 55, 72));
                g.drawRoundRect(1, 1, w - 3, h - 3, 11, 11);
            }
            setIcon(RoyalIcons.icon(glyph, 17, selected ? PALE : hover ? PALE : new Color(0xD4AF4E)));
            setForeground(selected ? IVORY : hover ? PALE : new Color(0xD8D1C4));
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
