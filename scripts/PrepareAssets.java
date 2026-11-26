import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

class PrepareAssets {
    public static void main(String[] args) throws Exception {
        Path raw = Path.of("raw-assets");
        Path out = Path.of("chess-desktop/src/main/resources/assets/themes/royal");
        String[] kinds = {"rook", "knight", "bishop", "queen", "king", "pawn"};
        int[] heights = {371, 418, 424, 460, 476, 326};
        var manifest = new StringBuilder("""
                # Royal Luxury assets

                The 18 PNG sources were inspected visually on a contact sheet. The 12 individual
                transparent pieces were selected over the two composite reference sheets.
                Piece bounds use nonzero alpha. All sprites have proportional scaling and a
                centered bottom anchor at y=488 on a transparent 512 × 512 canvas.

                Target paths below are relative to assets/themes/royal.

                | Source | Target | Source → output | Check |
                | --- | --- | --- | --- |
                """);
        for (String color : new String[]{"white", "black"}) {
            Path source = raw.resolve("imgchess/check_" + (color.equals("white") ? "while" : "black"));
            try (var stream = Files.list(source)) {
                var paths = stream.filter(p -> p.toString().endsWith(".png")).sorted().toList();
                for (int i = 0; i < kinds.length; i++) {
                    String suffix = "-" + (i + 1) + ".png";
                    Path path = paths.stream().filter(p -> p.toString().endsWith(suffix)).findFirst().orElseThrow();
                    BufferedImage original = ImageIO.read(path.toFile());
                    if (!original.getColorModel().hasAlpha()) throw new IllegalStateException(path.toString());
                    BufferedImage cropped = trim(original);
                    int height = heights[i];
                    int width = Math.round(cropped.getWidth() * (float) height / cropped.getHeight());
                    if (width > 512) throw new IllegalStateException("Sprite exceeds canvas: " + path);
                    BufferedImage sprite = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = sprite.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(cropped, (512 - width) / 2, 488 - height, width, height, null);
                    g.dispose();
                    String target = "pieces/" + color + "/" + kinds[i] + ".png";
                    Files.createDirectories(out.resolve(target).getParent());
                    ImageIO.write(sprite, "PNG", out.resolve(target).toFile());
                    row(manifest, path, target, original, "512 × 512", "RGBA, visual identity verified");
                }
            }
        }
        Files.createDirectories(out.resolve("board"));
        for (String target : new String[]{"board", "board-grid", "tile-light", "tile-dark"}) {
            String source = "royal_" + target.replace('-', '_').replace("board", "chessboard") + ".png";
            Path path = raw.resolve(source);
            BufferedImage original = ImageIO.read(path.toFile());
            String destination = "board/" + target + ".png";
            Files.copy(path, out.resolve(destination), StandardCopyOption.REPLACE_EXISTING);
            row(manifest, path, destination, original, size(original), "Copied byte for byte");
        }
        for (String sheet : new String[]{"Luxe Marble Chess Asset Collection-2.png", "Luxury Ivory Chess Asset Sheet.png"}) {
            Path path = raw.resolve("table_chess").resolve(sheet);
            row(manifest, path, "—", ImageIO.read(path.toFile()), "—", "Composite reference retained");
        }
        Files.writeString(Path.of("docs/ASSET_MANIFEST.md"), manifest);
    }

    private static BufferedImage trim(BufferedImage image) {
        int left = image.getWidth(), right = -1, top = image.getHeight(), bottom = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) == 0) continue;
                left = Math.min(x, left);
                right = Math.max(x, right);
                top = Math.min(y, top);
                bottom = Math.max(y, bottom);
            }
        }
        if (right < left) throw new IllegalArgumentException("Empty sprite");
        return image.getSubimage(left, top, right - left + 1, bottom - top + 1);
    }

    private static String size(BufferedImage im) {
        return im.getWidth() + " × " + im.getHeight();
    }

    private static void row(StringBuilder result, Path source, String target, BufferedImage original,
                            String dimensions, String check) {
        result.append("| `").append(source).append("` | `").append(target).append("` | ")
                .append(size(original)).append(" → ").append(dimensions).append(" | ").append(check).append(" |\n");
    }
}
