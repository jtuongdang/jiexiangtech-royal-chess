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
    private final JLabel countLabel;
    private final I18n i18n;
    private final String emptyMessage;
    private final IntConsumer onSelectPly;
    private int activePly = -1;

    RoyalMoveHistory(I18n i18n, IntConsumer onSelectPly) {
        super(new BorderLayout(0, 4));
        this.i18n = i18n;
        this.onSelectPly = onSelectPly;
        this.emptyMessage = i18n.get("emptyMoves");
        setOpaque(false);
        setBorder(new EmptyBorder(10, 12, 10, 10));

        // Reference-styled Move History header with icon + title + move count
        JPanel topBar = transparent(new BorderLayout(8, 0));
        JPanel titleGroup = transparent(new FlowLayout(FlowLayout.LEFT, 7, 0));
        titleGroup.add(new JLabel(RoyalIcons.icon(RoyalIcons.Glyph.DOC_LIST, 16, PALE)));
        JLabel title = new JLabel(i18n.get("moveHistory"));
        title.setForeground(IVORY);
        title.setFont(sans(Font.BOLD, 14f));
        titleGroup.add(title);

        countLabel = new JLabel(i18n.get("moveCount", 0));
        countLabel.setForeground(MUTED);
        countLabel.setFont(sans(Font.PLAIN, 12f));
        countLabel.setBorder(new EmptyBorder(0, 0, 0, 4));

        topBar.add(titleGroup, BorderLayout.WEST);
        topBar.add(countLabel, BorderLayout.EAST);

        JPanel headWrap = transparent(new BorderLayout(0, 7));
        headWrap.add(topBar, BorderLayout.NORTH);
        headWrap.add(divider(), BorderLayout.SOUTH);
        add(headWrap, BorderLayout.NORTH);

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        listPanel.setBorder(new EmptyBorder(4, 2, 4, 2));

        scrollPane = scroll(listPanel);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);
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
        int fullMoves = (limit + 1) / 2;
        countLabel.setText(i18n.get("moveCount", fullMoves));
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
            empty.setBorder(new EmptyBorder(24, 12, 16, 12));
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(empty);
        } else {
            JComponent activeRow = null;
            for (Row r : rows) {
                boolean isRowActive = r.whitePly() == activePly || r.blackPly() == activePly;
                JPanel rowComp = createRow(r, isRowActive);
                rowComp.setAlignmentX(Component.LEFT_ALIGNMENT);
                listPanel.add(rowComp);
                if (isRowActive) activeRow = rowComp;
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

    private JPanel createRow(Row row, boolean highlightRow) {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                if (highlightRow) {
                    g.setPaint(new GradientPaint(0, 0, new Color(172, 126, 36, 128), w, 0, new Color(76, 54, 16, 44)));
                    g.fillRoundRect(0, 1, w - 2, h - 2, 7, 7);
                    g.setColor(new Color(235, 192, 82, 105));
                    g.drawRoundRect(0, 1, w - 3, h - 3, 7, 7);
                } else {
                    g.setColor(new Color(255, 245, 220, 14));
                    g.drawLine(4, h - 1, w - 6, h - 1);
                }
                g.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(2, 8, 2, 8));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));
        panel.setPreferredSize(new Dimension(240, 31));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.fill = GridBagConstraints.HORIZONTAL;
        JLabel num = new JLabel(row.number() + ".");
        num.setForeground(highlightRow ? PALE : new Color(0x9C9282));
        num.setFont(mono(Font.PLAIN, 12.5f));
        c.gridx = 0; c.weightx = 0.16; panel.add(num, c);

        c.gridx = 1; c.weightx = 0.42; panel.add(moveCell(row.white(), row.whitePly(), highlightRow), c);
        c.gridx = 2; c.weightx = 0.42; panel.add(moveCell(row.black(), row.blackPly(), highlightRow), c);
        return panel;
    }

    private JComponent moveCell(String san, int ply, boolean inActiveRow) {
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
                if (hover && !isCurrent) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(255, 245, 220, 18));
                    g.fillRoundRect(0, 1, getWidth() - 4, getHeight() - 2, 6, 6);
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        cell.setOpaque(false);
        cell.setBorder(new EmptyBorder(3, 6, 3, 6));
        cell.setFont(sans(isCurrent ? Font.BOLD : Font.PLAIN, 13.5f));
        cell.setForeground(isCurrent ? new Color(0xFFF0BE) : inActiveRow ? IVORY : san.equals("…") ? MUTED : new Color(0xE6E0D5));
        return cell;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, new Color(22, 20, 24, 224), 0, h, new Color(14, 13, 16, 234)));
        g.fillRoundRect(0, 0, w, h, 14, 14);
        RoyalGlowEffect.paintMetallicGoldBorder(g, 0, 0, w, h, 14, 1.2f, false);
        g.dispose();
    }
}
