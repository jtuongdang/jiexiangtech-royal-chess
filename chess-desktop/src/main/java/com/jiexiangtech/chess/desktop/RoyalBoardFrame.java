package com.jiexiangtech.chess.desktop;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.image.BufferedImage;

final class RoyalBoardFrame {
    private static final int[] SOURCE_X = {0, BoardCoordinateMapper.FRAME_LEFT,
            BoardCoordinateMapper.FRAME_LEFT + BoardCoordinateMapper.REFERENCE_GRID_WIDTH, 828};
    private static final int[] SOURCE_Y = {0, BoardCoordinateMapper.FRAME_TOP,
            BoardCoordinateMapper.FRAME_TOP + BoardCoordinateMapper.REFERENCE_GRID_HEIGHT, 842};

    private RoyalBoardFrame() {}

    static void paintOuterFrame(Graphics2D g, BoardCoordinateMapper board, BufferedImage frame) {
        int x = board.x(), y = board.y(), span = board.tile() * 8;
        int left = x - board.leftBezel(), top = y - board.topBezel();
        int right = x + span + board.rightBezel(), bottom = y + span + board.bottomBezel();
        if (frame == null) {
            g.setPaint(new GradientPaint(left, top, new Color(0xD3A952), right, bottom, new Color(0x6E4C1E)));
            g.fillRoundRect(left, top, right - left, bottom - top, 14, 14);
            g.setColor(new Color(0x211912));
            g.fillRoundRect(left + 3, top + 3, right - left - 6, bottom - top - 6, 11, 11);
            return;
        }

        int[] destX = {left, x, x + span, right};
        int[] destY = {top, y, y + span, bottom};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (row == 1 && col == 1) continue;
                g.drawImage(frame, destX[col], destY[row], destX[col + 1], destY[row + 1],
                        SOURCE_X[col], SOURCE_Y[row], SOURCE_X[col + 1], SOURCE_Y[row + 1], null);
            }
        }
    }

    static void paintCoordinates(Graphics2D g, BoardCoordinateMapper board) {
        int x = board.x(), y = board.y(), tile = board.tile();
        Font ranks = RoyalStyle.serif(Font.BOLD, Math.max(10, Math.round(tile * .36f)));
        Font files = RoyalStyle.serif(Font.BOLD, Math.max(10, Math.round(tile * .31f)));
        int rankX = x - Math.round(board.leftBezel() * .43f);
        int fileY = y + tile * 8 + Math.round(board.bottomBezel() * .36f);
        for (int i = 0; i < 8; i++) {
            String file = String.valueOf((char) (board.flipped() ? 'H' - i : 'A' + i));
            String rank = String.valueOf((char) (board.flipped() ? '1' + i : '8' - i));
            drawCentered(g, files, file, x + tile * i + tile / 2, fileY);
            drawCentered(g, ranks, rank, rankX, y + tile * i + tile / 2);
        }
    }

    private static void drawCentered(Graphics2D g, Font font, String text, int centerX, int centerY) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int x = centerX - metrics.stringWidth(text) / 2;
        int baseline = centerY + Math.round(font.getSize2D() * .36f);
        g.setColor(new Color(0x261309));
        g.drawString(text, x + 1, baseline + 1);
        g.setColor(new Color(0xF3CB77));
        g.drawString(text, x, baseline);
    }
}
