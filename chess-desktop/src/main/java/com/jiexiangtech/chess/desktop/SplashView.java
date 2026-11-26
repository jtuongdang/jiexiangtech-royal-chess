package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import static com.jiexiangtech.chess.desktop.RoyalStyle.*;

final class SplashView {
    private final JWindow window = new JWindow();
    private final JProgressBar progress = new JProgressBar(0, 6);
    private final JLabel status;
    private final I18n text;
    private final Timer light;
    private final JComponent artwork;
    private Timer fade;
    private double visible = 0;

    SplashView(I18n text, BufferedImage background) {
        this.text = text;
        status = label(text.get("boot.0"), IVORY);
        var body = new JPanel(new BorderLayout()) {
            @Override public void paint(Graphics graphics) {
                super.paint(graphics);
                if (visible < .999) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setColor(new Color(0, 0, 0, (int) (255 * (1 - visible))));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.dispose();
                }
            }
        };
        artwork = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                if (background == null) {
                    g.setFont(new Font("Georgia", Font.BOLD, 27));
                    g.setColor(PALE);
                    g.drawString(BrandConstants.PRODUCT, 25, getHeight() / 2);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                    g.drawString(BrandConstants.COMPANY + "  " + BrandConstants.COMPANY_CHINESE, 25, getHeight() / 2 + 35);
                } else {
                    BrandingManager.drawContain(g, background, getWidth(), getHeight(), getWidth(), getHeight());
                }
                float x = (float) (getWidth() * (.5 + .37 * Math.sin(System.nanoTime() / 2.4e9)));
                float y = getHeight() * .82f;
                g.setPaint(new RadialGradientPaint(x, y, Math.max(1, getWidth() * .37f), new float[]{0, 1},
                        new Color[]{new Color(255, 190, 83, 39), new Color(255, 190, 83, 0)}));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        JPanel footer = new JPanel(new BorderLayout(6, 7));
        footer.setBackground(SURFACE);
        footer.setBorder(new EmptyBorder(13, 21, 16, 21));
        var version = label("v" + BrandConstants.version(), PALE);
        JPanel info = transparent(new BorderLayout(9, 0));
        info.add(status, BorderLayout.WEST);
        info.add(version, BorderLayout.EAST);
        footer.add(info, BorderLayout.NORTH);
        progress.setForeground(GOLD);
        progress.setPreferredSize(new Dimension(100, 6));
        footer.add(progress, BorderLayout.CENTER);
        if (background == null) footer.add(label(BrandConstants.COPYRIGHT, MUTED), BorderLayout.SOUTH);
        body.add(artwork, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(920, Math.min(screen.width * 83 / 100, (screen.height - 84) * 16 / 9));
        window.setSize(width, width * 9 / 16 + 63);
        window.setContentPane(body);
        window.setLocationRelativeTo(null);
        light = new Timer(32, event -> artwork.repaint());
        light.start();
        window.setVisible(true);
        animate(0, 1, 175, () -> {});
    }

    void step(int stage) {
        status.setText(text.get("boot." + Math.min(5, stage)));
        progress.setValue(stage);
    }

    void exit(Runnable ready) {
        step(6);
        animate(visible, 0, 150, () -> { dispose(); ready.run(); });
    }

    private void animate(double from, double to, int durationMillis, Runnable end) {
        if (fade != null) fade.stop();
        long start = System.nanoTime();
        fade = new Timer(16, event -> {
            double t = Math.min(1, (System.nanoTime() - start) / (durationMillis * 1_000_000d));
            visible = from + (to - from) * t;
            window.repaint();
            if (t >= 1) {
                ((Timer) event.getSource()).stop();
                end.run();
            }
        });
        fade.start();
    }

    BufferedImage snapshot() {
        Container content = window.getContentPane();
        var image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        double alpha = visible;
        visible = 1;
        Graphics2D g = image.createGraphics();
        content.printAll(g);
        g.dispose();
        visible = alpha;
        return image;
    }

    void dispose() {
        light.stop();
        if (fade != null) fade.stop();
        window.dispose();
    }
}
