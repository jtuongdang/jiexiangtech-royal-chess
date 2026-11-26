package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import com.jiexiangtech.chess.domain.Game;
import com.jiexiangtech.chess.domain.Side;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class RoyalMoveHistory extends JPanel {
    private record Row(int number, String white, int whitePly, String black, int blackPly) {}

    private final List<Row> rows = new ArrayList<>();
    private final JPanel listPanel;
    private final JScrollPane scrollPane;
    private final String emptyMessage;
    private final IntConsumer onSelectPly;
    private int activePly = -1;

    RoyalMoveHistory(I18n i18n, IntConsumer onSelectPly) {
        super(new BorderLayout());
        this.onSelectPly = onSelectPly;
        this.emptyMessage = i18n.get("emptyMoves");
        setOpaque(false);

        // Column header bar
        JPanel header = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setColor(new Color(0x1B191F));
                g.fillRoundRect(0, 0, getWidth(), getHeight() + 8, 10, 10);
                g.setColor(new Color(212, 175, 55, 55));
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                g.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(7, 10, 7, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.weightx = 0.16; header.add(headerLabel("#"), c);
        c.gridx = 1; c.weightx = 0.42; header.add(headerLabel(i18n.get("side.WHITE")), c);
        c.gridx = 2; c.weightx = 0.42; header.add(headerLabel(i18n.get("side.BLACK")), c);
        add(header, BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        listPanel.setBorder(new EmptyBorder(4, 4, 6, 4));

        scrollPane = scroll(listPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
    }

    private static JLabel headerLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase(java.util.Locale.ROOT));
        l.setForeground(new Color(0xA99776));
        l.setFont(sans(Font.BOLD, 10.5f));
        return l;
    }

    void update(Game game, int count) {
        update(game, count, count);
    }

    void update(Game game, int count, int highlightPly) {
        this.activePly = highlightPly;
        rows.clear();
        var moves = game.moves();
        int limit = Math.min(count, moves.size());
        for (int i = 0; i < limit; i++) {
            var before = game.positionAt(i);
            String san = moves.get(i).san();
            int plyNumber = i + 1;
            if (before.turn() == Side.WHITE) {
                rows.add(new Row(before.fullmove(), san, plyNumber, "", -1));
            } else if (rows.isEmpty()) {
                rows.add(new Row(before.fullmove(), "…", -1, san, plyNumber));
            } else {
                var prev = rows.remove(rows.size() - 1);
                rows.add(new Row(prev.number(), prev.white(), prev.whitePly(), san, plyNumber));
            }
        }
        rebuild();
    }

    void setActivePly(int ply) {
        this.activePly = ply;
        rebuild();
    }

    private void rebuild() {
        listPanel.removeAll();
        if (rows.isEmpty()) {
            JLabel empty = new JLabel(emptyMessage, SwingConstants.CENTER);
            empty.setForeground(MUTED);
            empty.setFont(sans(Font.ITALIC, 12.5f));
            empty.setBorder(new EmptyBorder(26, 12, 16, 12));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(empty);
        } else {
            JComponent activeRow = null;
            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                JPanel rowComp = createRow(r, i % 2 == 1);
                rowComp.setAlignmentX(Component.LEFT_ALIGNMENT);
                listPanel.add(rowComp);
                if (r.whitePly() == activePly || r.blackPly() == activePly) activeRow = rowComp;
            }
            listPanel.add(Box.createVerticalGlue());
            final JComponent scrollTarget = activeRow;
            SwingUtilities.invokeLater(() -> {
                if (scrollTarget != null) {
                    listPanel.scrollRectToVisible(scrollTarget.getBounds());
                } else {
                    JScrollBar bar = scrollPane.getVerticalScrollBar();
                    bar.setValue(bar.getMaximum());
                }
            });
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createRow(Row row, boolean alt) {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                if (alt) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(255, 245, 225, 8));
                    g.fillRoundRect(0, 1, getWidth(), getHeight() - 2, 7, 7);
                    g.dispose();
                }
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(2, 6, 2, 6));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
        panel.setPreferredSize(new Dimension(220, 31));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.fill = GridBagConstraints.HORIZONTAL;
        JLabel num = new JLabel(row.number() + ".");
        num.setForeground(new Color(0x8D7E63));
        num.setFont(mono(Font.PLAIN, 12f));
        c.gridx = 0; c.weightx = 0.16; panel.add(num, c);

        c.gridx = 1; c.weightx = 0.42; panel.add(moveCell(row.white(), row.whitePly()), c);
        c.gridx = 2; c.weightx = 0.42; panel.add(moveCell(row.black(), row.blackPly()), c);
        return panel;
    }

    private JComponent moveCell(String san, int ply) {
        boolean isCurrent = ply > 0 && ply == activePly;
        boolean clickable = onSelectPly != null && ply > 0;
        JLabel cell = new JLabel(san) {
            private boolean hover;
            {
                if (clickable) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                        @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                        @Override public void mouseClicked(MouseEvent e) { onSelectPly.accept(ply); }
                    });
                }
            }
            @Override protected void paintComponent(Graphics graphics) {
                if (isCurrent || hover) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(isCurrent ? new Color(212, 175, 55, 42) : new Color(255, 245, 220, 16));
                    g.fillRoundRect(0, 1, getWidth() - 4, getHeight() - 2, 7, 7);
                    if (isCurrent) {
                        g.setColor(new Color(212, 175, 55, 130));
                        g.drawRoundRect(0, 1, getWidth() - 5, getHeight() - 3, 7, 7);
                    }
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        cell.setOpaque(false);
        cell.setBorder(new EmptyBorder(3, 7, 3, 7));
        cell.setFont(sans(isCurrent ? Font.BOLD : Font.PLAIN, 13.5f));
        cell.setForeground(isCurrent ? PALE : san.equals("…") ? MUTED : IVORY);
        return cell;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x111014));
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        g.setColor(new Color(0x2E271E));
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
        g.dispose();
    }
}
