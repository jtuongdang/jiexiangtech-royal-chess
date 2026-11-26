package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

class RoyalCard extends RoyalPanel {
    private boolean goldAccent;
    private boolean hoverable;
    private boolean hovered;
    private boolean selected;

    RoyalCard(LayoutManager layout) {
        super(layout, new Color(0x1B191F), new Color(0x131216), LINE, 16);
        setBorder(new EmptyBorder(16, 18, 16, 18));
    }

    RoyalCard withGoldAccent() {
        this.goldAccent = true;
        repaint();
        return this;
    }

    RoyalCard interactive(Runnable onClick) {
        this.hoverable = true;
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
            @Override public void mouseClicked(MouseEvent e) { if (onClick != null) onClick.run(); }
        });
        return this;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        // Subtle ambient elevation shadow
        g.setColor(new Color(0, 0, 0, 75));
        g.fillRoundRect(2, 3, w - 4, h - 3, 18, 18);

        Color top = selected ? new Color(0x2A2317) : (hoverable && hovered) ? new Color(0x24201B) : new Color(0x1B191F);
        Color bottom = selected ? new Color(0x1B1710) : (hoverable && hovered) ? new Color(0x191614) : new Color(0x131216);
        g.setPaint(new GradientPaint(0, 0, top, 0, h - 2, bottom));
        g.fillRoundRect(0, 0, w, h - 1, 16, 16);

        if (goldAccent || selected) {
            g.setPaint(new GradientPaint(18, 0, new Color(212, 175, 55, 25), w / 2f, 0,
                    new Color(242, 215, 139, selected ? 210 : 140), true));
            g.fillRect(18, 1, Math.max(0, w - 36), 2);
        }

        g.setColor(new Color(255, 245, 215, 14));
        g.drawRoundRect(1, 1, w - 3, h - 4, 14, 14);
        g.setColor(selected ? GOLD : (hoverable && hovered) ? LINE_GOLD : LINE);
        g.drawRoundRect(0, 0, w - 1, h - 2, 16, 16);
        g.dispose();
    }
}
