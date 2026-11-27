import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

class PrepareAssets {
    private static final Path RAW = Path.of("raw-assets");
    private static final Path OUT = Path.of("chess-desktop/src/main/resources/assets/themes/royal");
    private static final Rectangle FRAME = new Rectangle(298, 102, 828, 842);
    private static final Rectangle GRID = new Rectangle(366, 145, 713, 730);
    private static final String[] KINDS = {"rook", "knight", "bishop", "queen", "king", "pawn"};

    public static void main(String[] args) throws Exception {
        Path atlasPath = RAW.resolve("royal-arena-sprites.png");
        Path referencePath = RAW.resolve("royal-interface-2.png");
        BufferedImage atlas = read(atlasPath);
        BufferedImage reference = read(referencePath);
        if (!atlas.getColorModel().hasAlpha() || atlas.getWidth() % 6 != 0 || atlas.getHeight() % 2 != 0) {
            throw new IllegalArgumentException("Expected a transparent 6 × 2 Royal Luxury sprite atlas.");
        }
        if (reference.getWidth() != 1586 || reference.getHeight() != 992) {
            throw new IllegalArgumentException("Expected the original 1586 × 992 Classic reference.");
        }

        var manifest = new StringBuilder("""
                # Royal Arena and Royal Luxury assets

                Target paths are relative to assets/themes/royal. Original assets are preserved locally in
                raw-assets. The current 12 sprites come from a transparent ImageGen atlas based on
                the original Royal Arena Duel design. Each is isolated, proportionally resampled and
                bottom-centered on a transparent 512 × 512 canvas. The grid and pieces are drawn separately
                at runtime. Classic uses the carved frame from the original top-down reference.

                | Source | Target | Source → output | Processing |
                | --- | --- | --- | --- |
                """);
        int cellW = atlas.getWidth() / 6;
        int cellH = atlas.getHeight() / 2;
        for (int row = 0; row < 2; row++) {
            String side = row == 0 ? "black" : "white";
            for (int col = 0; col < 6; col++) {
                var target = "pieces/" + side + "/" + KINDS[col] + ".png";
                var piece = atlas.getSubimage(col * cellW, row * cellH, cellW, cellH);
                write(OUT.resolve(target), cleanSprite(piece));
                row(manifest, atlasPath, target, size(atlas) + " → 512 × 512", "Atlas cell " + (row + 1) + "/" + (col + 1));
            }
        }

        write(OUT.resolve("board/classic-frame.png"), frameTexture(reference));
        row(manifest, referencePath, "board/classic-frame.png", size(reference) + " → 828 × 842",
                "Bezel only. Baked coordinate glyphs removed. Playing area is transparent.");
        write(Path.of("docs/design/royal-classic-reference.png"),
                reference.getSubimage(FRAME.x, FRAME.y, FRAME.width, FRAME.height));
        arenaTiles(manifest);
        for (String name : new String[]{"board", "board-grid"}) {
            Path source = RAW.resolve("royal_" + name.replace("board", "chessboard").replace('-', '_') + ".png");
            String target = "board/" + name + ".png";
            Files.copy(source, OUT.resolve(target), StandardCopyOption.REPLACE_EXISTING);
            row(manifest, source, target, size(read(source)), "Legacy decorative artwork retained");
        }
        manifest.append("""

                The original individual sprites, marble tiles and composite sheets are still in raw-assets.
                """);
        arenaScene(manifest);
        Files.writeString(Path.of("docs/ASSET_MANIFEST.md"), manifest);
    }

    private static void arenaScene(StringBuilder manifest) throws Exception {
        Path assets = OUT.getParent().getParent();
        Path sceneryPath = RAW.resolve("royal-arena-backdrop.png");
        var scenery = read(sceneryPath);
        Path backdrop = assets.resolve("backgrounds/royal-arena.jpg");
        Files.createDirectories(backdrop.getParent());
        ImageIO.write(scenery, "JPEG", backdrop.toFile());

        Path atlasPath = RAW.resolve("royal-arena-portraits.png");
        var atlas = read(atlasPath);
        if (atlas.getWidth() % 3 != 0 || atlas.getHeight() % 2 != 0) {
            throw new IllegalArgumentException("Expected a 3 × 2 portrait atlas.");
        }
        String[] portraits = {"knight", "queen", "sage", "archer", "sovereign", "ranger"};
        int w = atlas.getWidth() / 3, h = atlas.getHeight() / 2;
        int extent = Math.min(w, h) - 4;
        for (int i = 0; i < portraits.length; i++) {
            int x = i % 3 * w + (w - extent) / 2;
            int y = i / 3 * h + (h - extent) / 3;
            var crop = atlas.getSubimage(x, y, extent, extent);
            write(assets.resolve("avatars/" + portraits[i] + ".png"), scale(crop, 256, 256));
        }

        Path reference = RAW.resolve("royal-arena-duel-reference.png");
        write(Path.of("docs/design/royal-focus-reference.png"), scale(read(reference), 1024, 576));
        manifest.append("""

                ## Arena scene and portraits

                Targets in this section are relative to assets, except for the design reference.

                | Source | Target | Source → output | Processing |
                | --- | --- | --- | --- |
                """);
        row(manifest, sceneryPath, "backgrounds/royal-arena.jpg", size(scenery),
                "Backdrop without board, pieces or UI, encoded as JPEG");
        row(manifest, atlasPath, "avatars/{knight,queen,sage,archer,sovereign,ranger}.png",
                size(atlas) + " → 6 × (256 × 256)", "Center-cropped portraits, circular masks drawn by Swing");
        row(manifest, reference, "docs/design/royal-focus-reference.png", "1672 × 941 → 1024 × 576",
                "Owner-provided layout reference, not a runtime image");
    }

    private static void arenaTiles(StringBuilder manifest) throws Exception {
        Path path = RAW.resolve("royal-arena-marble.png");
        var atlas = read(path);
        int w = atlas.getWidth() / 4, h = atlas.getHeight() / 2;
        int side = Math.min(w, h) - 8;
        for (int row = 0; row < 2; row++) {
            String shade = row == 0 ? "light" : "dark";
            for (int col = 0; col < 4; col++) {
                int x = col * w + (w - side) / 2, y = row * h + (h - side) / 2;
                String target = "board/tile-" + shade + (col == 0 ? "" : "-" + col) + ".png";
                write(OUT.resolve(target), scale(atlas.getSubimage(x, y, side, side), 256, 256));
            }
        }
        row(manifest, path, "board/tile-light*.png, board/tile-dark*.png", size(atlas) + " → 8 × (256 × 256)",
                "Four cream marble and four dark onyx samples");
    }

    private static BufferedImage scale(BufferedImage source, int width, int height) {
        var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = graphics(result);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return result;
    }

    private static BufferedImage cleanSprite(BufferedImage cell) {
        int w = cell.getWidth(), h = cell.getHeight();
        int[] rgb = cell.getRGB(0, 0, w, h, null, 0, w);
        boolean[] core = largestOpaqueComponent(rgb, w, h);
        int left = w, top = h, right = -1, bottom = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int index = y * w + x;
                if ((rgb[index] >>> 24) < 16 || !nearCore(core, x, y, w, h)) {
                    rgb[index] = 0;
                    continue;
                }
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x);
                bottom = Math.max(bottom, y);
            }
        }
        if (right < left) throw new IllegalArgumentException("Sprite cell is empty");
        int cropW = right - left + 1, cropH = bottom - top + 1;
        if (cropW < w * .25 || cropH < h * .45) throw new IllegalArgumentException("Incomplete sprite cell");
        var crop = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_ARGB);
        crop.setRGB(0, 0, cropW, cropH, rgb, top * w + left, w);

        double scale = Math.min(460.0 / cropW, 472.0 / cropH);
        int width = (int) Math.round(cropW * scale), height = (int) Math.round(cropH * scale);
        var sprite = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(sprite);
        g.drawImage(crop, (512 - width) / 2, 488 - height, width, height, null);
        g.dispose();
        return sprite;
    }

    private static boolean[] largestOpaqueComponent(int[] rgb, int w, int h) {
        int[] labels = new int[rgb.length];
        int[] queue = new int[rgb.length];
        int label = 0, largest = 0, winning = -1;
        for (int i = 0; i < rgb.length; i++) {
            if (labels[i] != 0 || (rgb[i] >>> 24) < 160) continue;
            queue[0] = i;
            labels[i] = ++label;
            int tail = 1;
            for (int head = 0; head < tail; head++) {
                int p = queue[head], x = p % w, y = p / w;
                if (x > 0) tail = enqueue(p - 1, label, tail, queue, labels, rgb);
                if (x + 1 < w) tail = enqueue(p + 1, label, tail, queue, labels, rgb);
                if (y > 0) tail = enqueue(p - w, label, tail, queue, labels, rgb);
                if (y + 1 < h) tail = enqueue(p + w, label, tail, queue, labels, rgb);
            }
            if (tail > largest) {
                largest = tail;
                winning = label;
            }
        }
        boolean[] result = new boolean[rgb.length];
        for (int i = 0; i < rgb.length; i++) result[i] = labels[i] == winning;
        return result;
    }

    private static int enqueue(int p, int label, int tail, int[] queue, int[] labels, int[] rgb) {
        if (labels[p] != 0 || (rgb[p] >>> 24) < 160) return tail;
        labels[p] = label;
        queue[tail] = p;
        return tail + 1;
    }

    private static boolean nearCore(boolean[] core, int x, int y, int w, int h) {
        for (int ny = Math.max(0, y - 2); ny <= Math.min(h - 1, y + 2); ny++) {
            for (int nx = Math.max(0, x - 2); nx <= Math.min(w - 1, x + 2); nx++) {
                if (core[ny * w + nx]) return true;
            }
        }
        return false;
    }

    private static BufferedImage frameTexture(BufferedImage reference) {
        var frame = new BufferedImage(FRAME.width, FRAME.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = graphics(frame);
        g.drawImage(reference, -FRAME.x, -FRAME.y, null);
        // Marble from the gaps between coordinates restores the channels without touching the gold rails.
        int[] ranks = {193, 283, 372, 462, 552, 643, 734, 824};
        for (int i = 0; i < ranks.length; i++) {
            replaceMarble(frame, reference, new Rectangle(323, ranks[i] - 17, 29, 34), 0, i == 7 ? -43 : 40);
        }
        for (int file = 0; file < 8; file++) {
            int x = (int) Math.round(GRID.x + (file + .5) * GRID.width / 8);
            replaceMarble(frame, reference, new Rectangle(x - 15, 886, 32, 30), file == 7 ? -44 : 42, 0);
        }
        var mask = new BufferedImage(FRAME.width, FRAME.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = graphics(mask);
        m.setColor(Color.WHITE);
        m.fill(new RoundRectangle2D.Double(0, 0, FRAME.width, FRAME.height, 28, 28));
        m.dispose();
        g.setComposite(AlphaComposite.DstIn);
        g.drawImage(mask, 0, 0, null);
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(GRID.x - FRAME.x, GRID.y - FRAME.y, GRID.width, GRID.height);
        g.dispose();
        return frame;
    }

    private static void replaceMarble(BufferedImage frame, BufferedImage reference, Rectangle area, int dx, int dy) {
        for (int j = 0; j < area.height; j++) {
            for (int i = 0; i < area.width; i++) {
                int edge = Math.min(Math.min(i, area.width - 1 - i), Math.min(j, area.height - 1 - j));
                float blend = Math.min(1f, edge / 4f);
                int x = area.x + i, y = area.y + j;
                int original = reference.getRGB(x, y), marble = reference.getRGB(x + dx, y + dy);
                int result = 0xFF000000;
                for (int shift = 0; shift <= 16; shift += 8) {
                    int a = (original >>> shift) & 255, b = (marble >>> shift) & 255;
                    result |= Math.round(a + (b - a) * blend) << shift;
                }
                frame.setRGB(x - FRAME.x, y - FRAME.y, result);
            }
        }
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        return g;
    }

    private static BufferedImage read(Path path) throws Exception {
        BufferedImage result = ImageIO.read(path.toFile());
        if (result == null) throw new IllegalArgumentException("Invalid PNG: " + path);
        return result;
    }

    private static void write(Path path, BufferedImage image) throws Exception {
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "PNG", path.toFile());
    }

    private static String size(BufferedImage image) {
        return image.getWidth() + " × " + image.getHeight();
    }

    private static void row(StringBuilder result, Path source, String target, String size, String processing) {
        result.append("| `").append(source).append("` | `").append(target).append("` | ")
                .append(size).append(" | ").append(processing).append(" |\n");
    }
}
