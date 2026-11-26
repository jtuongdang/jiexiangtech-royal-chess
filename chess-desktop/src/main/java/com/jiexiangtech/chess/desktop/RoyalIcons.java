package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.*;
import javax.swing.Icon;

final class RoyalIcons {
    enum Glyph {
        HOME, NEW_GAME, PUZZLES, HISTORY, LEADERBOARD, STATISTICS, ACHIEVEMENTS, SETTINGS,
        ABOUT, SIGN_OUT, COLLAPSE, EXPAND,
        SAVE, UNDO, HINT, DRAW, RESIGN, FLIP, SOUND_ON, SOUND_OFF, FULLSCREEN, PAUSE,
        FIRST, PREV, NEXT, LAST, PLAY, STOP, ANALYZE,
        SEARCH, CROWN, TROPHY, LOCK, CHECK, CPU, PLAYERS, SPARK, CLOCK, EXPORT, IMPORT
    }

    private RoyalIcons() {}

    static Icon icon(Glyph glyph, int size, Color color) {
        return new VectorIcon(glyph, size, color);
    }

    static void draw(Graphics2D g, Glyph glyph, int x, int y, int size, Color color) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        double s = size / 20.0;
        g2.scale(s, s);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.65f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (glyph) {
            case HOME -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 10); p.lineTo(10, 3.5); p.lineTo(17, 10);
                g2.draw(p);
                g2.draw(new RoundRectangle2D.Double(5, 9.5, 10, 7.5, 1.5, 1.5));
                g2.draw(new Line2D.Double(8.5, 17, 8.5, 13));
                g2.draw(new Line2D.Double(11.5, 17, 11.5, 13));
            }
            case NEW_GAME -> {
                g2.draw(new Line2D.Double(4, 16, 15.5, 4.5));
                g2.draw(new Line2D.Double(16, 16, 4.5, 4.5));
                g2.draw(new Line2D.Double(12, 4.5, 15.5, 4.5));
                g2.draw(new Line2D.Double(15.5, 4.5, 15.5, 8));
                g2.draw(new Line2D.Double(8, 4.5, 4.5, 4.5));
                g2.draw(new Line2D.Double(4.5, 4.5, 4.5, 8));
            }
            case PUZZLES -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 17); p.lineTo(15.5, 17);
                p.moveTo(6, 17); p.lineTo(6.5, 12); p.quadTo(6.5, 7, 10.5, 4);
                p.lineTo(13.5, 6); p.lineTo(15, 9.5); p.lineTo(12, 10.5);
                p.quadTo(11, 13, 13.5, 17);
                g2.draw(p);
                g2.fill(new Ellipse2D.Double(10.3, 6.2, 1.6, 1.6));
            }
            case HISTORY -> {
                g2.draw(new Arc2D.Double(3.5, 3.5, 13, 13, 30, 300, Arc2D.OPEN));
                Path2D a = new Path2D.Double();
                a.moveTo(3.5, 7.5); a.lineTo(3.8, 4); a.lineTo(7.2, 4.8);
                g2.draw(a);
                g2.draw(new Line2D.Double(10, 6.5, 10, 10.2));
                g2.draw(new Line2D.Double(10, 10.2, 12.8, 12));
            }
            case LEADERBOARD, TROPHY -> {
                Path2D cup = new Path2D.Double();
                cup.moveTo(6, 4); cup.lineTo(14, 4); cup.lineTo(13.2, 9.5);
                cup.quadTo(10, 12.5, 6.8, 9.5); cup.closePath();
                g2.draw(cup);
                g2.draw(new Arc2D.Double(3.5, 4.5, 3, 4.5, 90, 180, Arc2D.OPEN));
                g2.draw(new Arc2D.Double(13.5, 4.5, 3, 4.5, -90, 180, Arc2D.OPEN));
                g2.draw(new Line2D.Double(10, 11.5, 10, 15.5));
                g2.draw(new Line2D.Double(6.5, 16, 13.5, 16));
            }
            case STATISTICS -> {
                g2.draw(new Line2D.Double(3.5, 16.5, 16.5, 16.5));
                g2.draw(new RoundRectangle2D.Double(4.8, 11, 2.6, 5.5, 1, 1));
                g2.draw(new RoundRectangle2D.Double(8.7, 7, 2.6, 9.5, 1, 1));
                g2.draw(new RoundRectangle2D.Double(12.6, 4, 2.6, 12.5, 1, 1));
            }
            case ACHIEVEMENTS -> {
                g2.draw(new Ellipse2D.Double(5.5, 3.5, 9, 9));
                Path2D rib = new Path2D.Double();
                rib.moveTo(7.5, 12); rib.lineTo(6.5, 17); rib.lineTo(10, 15.2);
                rib.lineTo(13.5, 17); rib.lineTo(12.5, 12);
                g2.draw(rib);
                g2.fill(new Ellipse2D.Double(9.1, 7.1, 1.8, 1.8));
            }
            case SETTINGS -> {
                g2.draw(new Ellipse2D.Double(7.2, 7.2, 5.6, 5.6));
                for (int i = 0; i < 8; i++) {
                    double rad = i * Math.PI / 4;
                    g2.draw(new Line2D.Double(10 + Math.cos(rad) * 5.2, 10 + Math.sin(rad) * 5.2,
                            10 + Math.cos(rad) * 7.2, 10 + Math.sin(rad) * 7.2));
                }
            }
            case ABOUT -> {
                g2.draw(new Ellipse2D.Double(3.5, 3.5, 13, 13));
                g2.fill(new Ellipse2D.Double(9.1, 6.2, 1.8, 1.8));
                g2.draw(new Line2D.Double(10, 9.6, 10, 13.8));
            }
            case SIGN_OUT -> {
                Path2D p = new Path2D.Double();
                p.moveTo(11, 4); p.lineTo(5, 4); p.lineTo(5, 16); p.lineTo(11, 16);
                g2.draw(p);
                g2.draw(new Line2D.Double(9, 10, 16.5, 10));
                g2.draw(new Line2D.Double(13.5, 7, 16.5, 10));
                g2.draw(new Line2D.Double(13.5, 13, 16.5, 10));
            }
            case COLLAPSE -> {
                g2.draw(new Line2D.Double(5, 4.5, 5, 15.5));
                g2.draw(new Line2D.Double(15, 10, 8.5, 10));
                g2.draw(new Line2D.Double(11.5, 6.8, 8.3, 10));
                g2.draw(new Line2D.Double(11.5, 13.2, 8.3, 10));
            }
            case EXPAND -> {
                g2.draw(new Line2D.Double(5, 4.5, 5, 15.5));
                g2.draw(new Line2D.Double(8.5, 10, 15, 10));
                g2.draw(new Line2D.Double(11.8, 6.8, 15, 10));
                g2.draw(new Line2D.Double(11.8, 13.2, 15, 10));
            }
            case SAVE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4.5, 4); p.lineTo(13.5, 4); p.lineTo(16, 6.5); p.lineTo(16, 16); p.lineTo(4.5, 16); p.closePath();
                g2.draw(p);
                g2.draw(new Rectangle2D.Double(7, 4, 5.5, 4));
                g2.draw(new Rectangle2D.Double(7, 11, 6, 5));
            }
            case UNDO -> {
                Path2D p = new Path2D.Double();
                p.moveTo(7.5, 5.5); p.lineTo(4, 8.8); p.lineTo(7.5, 12);
                g2.draw(p);
                g2.draw(new Arc2D.Double(5.5, 8.8, 10.5, 7, 0, 165, Arc2D.OPEN));
            }
            case HINT, SPARK -> {
                Path2D star = new Path2D.Double();
                star.moveTo(10, 3); star.quadTo(10.5, 9.5, 17, 10);
                star.quadTo(10.5, 10.5, 10, 17); star.quadTo(9.5, 10.5, 3, 10);
                star.quadTo(9.5, 9.5, 10, 3);
                g2.draw(star);
            }
            case DRAW -> {
                g2.draw(new Line2D.Double(4.5, 7.5, 15.5, 7.5));
                g2.draw(new Line2D.Double(4.5, 12.5, 15.5, 12.5));
            }
            case RESIGN -> {
                g2.draw(new Line2D.Double(5.5, 4, 5.5, 16.5));
                Path2D flag = new Path2D.Double();
                flag.moveTo(5.5, 4.5); flag.lineTo(15.5, 6.5); flag.lineTo(5.5, 11);
                g2.draw(flag);
            }
            case FLIP -> {
                g2.draw(new Line2D.Double(7, 4.5, 7, 15.5));
                g2.draw(new Line2D.Double(4.5, 7, 7, 4.5));
                g2.draw(new Line2D.Double(9.5, 7, 7, 4.5));
                g2.draw(new Line2D.Double(13, 15.5, 13, 4.5));
                g2.draw(new Line2D.Double(10.5, 13, 13, 15.5));
                g2.draw(new Line2D.Double(15.5, 13, 13, 15.5));
            }
            case SOUND_ON -> {
                Path2D sp = new Path2D.Double();
                sp.moveTo(4, 8); sp.lineTo(7, 8); sp.lineTo(11, 5); sp.lineTo(11, 15); sp.lineTo(7, 12); sp.lineTo(4, 12); sp.closePath();
                g2.draw(sp);
                g2.draw(new Arc2D.Double(11.5, 7.2, 3.2, 5.6, -55, 110, Arc2D.OPEN));
                g2.draw(new Arc2D.Double(13, 5.2, 4.5, 9.6, -55, 110, Arc2D.OPEN));
            }
            case SOUND_OFF -> {
                Path2D sp = new Path2D.Double();
                sp.moveTo(4, 8); sp.lineTo(7, 8); sp.lineTo(11, 5); sp.lineTo(11, 15); sp.lineTo(7, 12); sp.lineTo(4, 12); sp.closePath();
                g2.draw(sp);
                g2.draw(new Line2D.Double(13.5, 7.5, 17, 12.5));
                g2.draw(new Line2D.Double(17, 7.5, 13.5, 12.5));
            }
            case FULLSCREEN -> {
                g2.draw(new Line2D.Double(4, 8, 4, 4)); g2.draw(new Line2D.Double(4, 4, 8, 4));
                g2.draw(new Line2D.Double(12, 4, 16, 4)); g2.draw(new Line2D.Double(16, 4, 16, 8));
                g2.draw(new Line2D.Double(4, 12, 4, 16)); g2.draw(new Line2D.Double(4, 16, 8, 16));
                g2.draw(new Line2D.Double(12, 16, 16, 16)); g2.draw(new Line2D.Double(16, 12, 16, 16));
            }
            case PAUSE -> {
                g2.draw(new RoundRectangle2D.Double(5.5, 4.5, 3, 11, 1, 1));
                g2.draw(new RoundRectangle2D.Double(11.5, 4.5, 3, 11, 1, 1));
            }
            case FIRST -> {
                g2.draw(new Line2D.Double(5, 5, 5, 15));
                Path2D tri = new Path2D.Double();
                tri.moveTo(15, 5.5); tri.lineTo(8.5, 10); tri.lineTo(15, 14.5); tri.closePath();
                g2.fill(tri);
            }
            case PREV -> {
                Path2D tri = new Path2D.Double();
                tri.moveTo(13.5, 5); tri.lineTo(6.5, 10); tri.lineTo(13.5, 15); tri.closePath();
                g2.fill(tri);
            }
            case NEXT, PLAY -> {
                Path2D tri = new Path2D.Double();
                tri.moveTo(6.5, 5); tri.lineTo(14.5, 10); tri.lineTo(6.5, 15); tri.closePath();
                g2.fill(tri);
            }
            case LAST -> {
                g2.draw(new Line2D.Double(15, 5, 15, 15));
                Path2D tri = new Path2D.Double();
                tri.moveTo(5, 5.5); tri.lineTo(11.5, 10); tri.lineTo(5, 14.5); tri.closePath();
                g2.fill(tri);
            }
            case STOP -> g2.fill(new RoundRectangle2D.Double(5.5, 5.5, 9, 9, 1.5, 1.5));
            case ANALYZE, SEARCH -> {
                g2.draw(new Ellipse2D.Double(4, 4, 9.5, 9.5));
                g2.draw(new Line2D.Double(12.2, 12.2, 16.5, 16.5));
            }
            case CROWN -> {
                Path2D c = new Path2D.Double();
                c.moveTo(3.5, 14.5); c.lineTo(4.5, 7); c.lineTo(8, 10.5);
                c.lineTo(10, 5); c.lineTo(12, 10.5); c.lineTo(15.5, 7);
                c.lineTo(16.5, 14.5); c.closePath();
                g2.draw(c);
                g2.draw(new Line2D.Double(4, 16.5, 16, 16.5));
            }
            case LOCK -> {
                g2.draw(new RoundRectangle2D.Double(5, 9, 10, 7.5, 2, 2));
                g2.draw(new Arc2D.Double(7, 4, 6, 8, 0, 180, Arc2D.OPEN));
            }
            case CHECK -> {
                Path2D ck = new Path2D.Double();
                ck.moveTo(4.5, 10.5); ck.lineTo(8.5, 14.5); ck.lineTo(15.5, 6);
                g2.draw(ck);
            }
            case CPU -> {
                g2.draw(new RoundRectangle2D.Double(5.5, 5.5, 9, 9, 2, 2));
                g2.draw(new Rectangle2D.Double(8, 8, 4, 4));
                g2.draw(new Line2D.Double(8, 3, 8, 5.5)); g2.draw(new Line2D.Double(12, 3, 12, 5.5));
                g2.draw(new Line2D.Double(8, 14.5, 8, 17)); g2.draw(new Line2D.Double(12, 14.5, 12, 17));
                g2.draw(new Line2D.Double(3, 8, 5.5, 8)); g2.draw(new Line2D.Double(3, 12, 5.5, 12));
                g2.draw(new Line2D.Double(14.5, 8, 17, 8)); g2.draw(new Line2D.Double(14.5, 12, 17, 12));
            }
            case PLAYERS -> {
                g2.draw(new Ellipse2D.Double(5, 4.5, 4.5, 4.5));
                g2.draw(new Arc2D.Double(3, 11, 8.5, 7, 0, 180, Arc2D.OPEN));
                g2.draw(new Ellipse2D.Double(11.2, 5.5, 4, 4));
                g2.draw(new Arc2D.Double(10.5, 11.8, 6.5, 5.5, 0, 165, Arc2D.OPEN));
            }
            case CLOCK -> {
                g2.draw(new Ellipse2D.Double(3.5, 3.5, 13, 13));
                g2.draw(new Line2D.Double(10, 6.5, 10, 10.3));
                g2.draw(new Line2D.Double(10, 10.3, 13, 12));
            }
            case EXPORT -> {
                g2.draw(new Line2D.Double(10, 13, 10, 4));
                g2.draw(new Line2D.Double(6.8, 7, 10, 3.8));
                g2.draw(new Line2D.Double(13.2, 7, 10, 3.8));
                Path2D tray = new Path2D.Double();
                tray.moveTo(4.5, 12.5); tray.lineTo(4.5, 16); tray.lineTo(15.5, 16); tray.lineTo(15.5, 12.5);
                g2.draw(tray);
            }
            case IMPORT -> {
                g2.draw(new Line2D.Double(10, 4, 10, 13));
                g2.draw(new Line2D.Double(6.8, 9.8, 10, 13));
                g2.draw(new Line2D.Double(13.2, 9.8, 10, 13));
                Path2D tray = new Path2D.Double();
                tray.moveTo(4.5, 12.5); tray.lineTo(4.5, 16); tray.lineTo(15.5, 16); tray.lineTo(15.5, 12.5);
                g2.draw(tray);
            }
        }
        g2.dispose();
    }

    private record VectorIcon(Glyph glyph, int size, Color color) implements Icon {
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Color tint = c != null && !c.isEnabled() ? new Color(color.getRed(), color.getGreen(), color.getBlue(), 110) : color;
            draw((Graphics2D) g, glyph, x, y, size, tint);
        }
        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }
}
