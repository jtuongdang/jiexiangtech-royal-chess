package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import javax.swing.Icon;
import com.jiexiangtech.chess.domain.Kind;
import com.jiexiangtech.chess.domain.Piece;
import com.jiexiangtech.chess.domain.Side;
import com.jiexiangtech.chess.online.RoyalAvatar;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

record AvatarIcon(AssetManager assets, RoyalAvatar avatar, Side side, int size) implements Icon {
    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override public void paintIcon(Component target, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        paintPortrait(g, assets, avatar, side, size, size);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(2, 2, size - 5, size - 5);
        g.dispose();
    }

    static void paintPortrait(Graphics2D graphics, AssetManager assets, RoyalAvatar avatar, Side side, int w, int h) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.clip(new Ellipse2D.Double(2, 2, w - 4, h - 4));
        var image = assets.avatar(avatar);
        if (image != null) g.drawImage(image, 2, 2, w - 4, h - 4, null);
        else {
            g.setPaint(new GradientPaint(0, 0, new Color(side == Side.WHITE ? 0x46371F : 0x272226),
                    0, h, new Color(0x171419)));
            g.fillRect(0, 0, w, h);
            assets.drawPieceFitted(g, Piece.of(side, Kind.KING), 8, 5, w - 16, h - 10);
        }
        g.dispose();
    }
}
