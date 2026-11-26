package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

/**
 * Horizontal obsidian-and-gold pill toolbar below the 2.5D chessboard in Royal Focus Mode,
 * matching royal-focus-reference.png with hover-reactive focus dimming.
 */
final class RoyalBottomToolbar extends JPanel {
    private boolean focusDimmed;
    private boolean hovered;
    private float alpha = 1.0f;
    private final Timer fadeTimer;

    RoyalBottomToolbar(Component... controls) {
        super(new FlowLayout(FlowLayout.CENTER, 7, 2));
        setOpaque(false);
        setBorder(new EmptyBorder(4, 10, 8, 10));
        for (Component c : controls) add(c);

        fadeTimer = new Timer(16, e -> {
            float target = (!focusDimmed || hovered) ? 1.0f : 0.78f;
            float diff = target - alpha;
            if (Math.abs(diff) < 0.02f) {
                alpha = target;
                ((Timer) e.getSource()).stop();
            } else {
                alpha += diff * 0.22f;
            }
            repaint();
        });

        MouseAdapter hoverTracker = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { setHovered(true); }
            @Override public void mouseExited(MouseEvent e) {
                Point p = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), RoyalBottomToolbar.this);
                if (!contains(p)) setHovered(false);
            }
        };
        addMouseListener(hoverTracker);
        for (Component c : controls) c.addMouseListener(hoverTracker);
    }

    void setFocusDimmed(boolean dimmed) {
        this.focusDimmed = dimmed;
        fadeTimer.start();
    }

    private void setHovered(boolean h) {
        if (hovered != h) {
            hovered = h;
            fadeTimer.start();
        }
    }

    @Override public void removeNotify() { fadeTimer.stop(); super.removeNotify(); }

    @Override public void paint(Graphics graphics) {
        if (alpha >= 0.99f) {
            super.paint(graphics);
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0.35f, alpha)));
        super.paint(g);
        g.dispose();
    }

    static RoyalButton pillAction(String label, RoyalButton.Variant variant, RoyalIcons.Glyph glyph, Runnable action) {
        RoyalButton btn = new RoyalButton(label, variant, glyph, action);
        btn.setRadius(18);
        btn.setBorder(new EmptyBorder(7, 13, 7, 13));
        btn.setFont(sans(Font.BOLD, 12f));
        btn.setIconTextGap(6);
        btn.setMinimumSize(new Dimension(42, 34));
        btn.setToolTipText(label);
        return btn;
    }
}
