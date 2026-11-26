package com.jiexiangtech.chess.desktop;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import com.jiexiangtech.chess.application.Preferences;
import com.jiexiangtech.chess.domain.*;

/**
 * Royal Focus Mode 2.5D renderer:
 * - Bakes the entire static 2.5D board stage (spotlight, drop shadow, 3D front slab, ornate gold frame,
 *   warped 64 marble tiles, coordinates, and corner vignette) into a single offscreen Retina BufferedImage
 *   rebuilt only on resize/flip/theme/quality change.
 * - Dynamic rendering reuses the board surface and Retina sprites while projecting each piece in depth order.
 */
final class RoyalFocusBoardRenderer implements BoardRenderer {
    private record StageKey(int width, int height, boolean flipped, String theme,
                            Preferences.GraphicsQuality quality, double dpiScale) {}

    private final AssetManager assets;
    private final RoyalPieceRenderer pieceRenderer;
    private final PerspectiveTextureRenderer textureRenderer;
    private final ShadowRenderer shadowRenderer;
    private final ReflectionRenderer reflectionRenderer;
    private final CinematicLightingEngine lighting;
    private StageKey cachedStageKey;
    private BufferedImage cachedStaticStage;

    RoyalFocusBoardRenderer(AssetManager assets, RoyalPieceRenderer pieceRenderer) {
        this.assets = assets;
        this.pieceRenderer = pieceRenderer;
        this.textureRenderer = new PerspectiveTextureRenderer(assets);
        this.shadowRenderer = new ShadowRenderer(assets);
        this.reflectionRenderer = new ReflectionRenderer(assets);
        this.lighting = new CinematicLightingEngine(assets);
    }

    @Override
    public int squareAt(int width, int height, boolean flipped, double px, double py,
                        Position position, int pieceScale, java.util.List<Move> candidates, boolean[] movableSquares) {
        return PerspectiveCoordinateMapper.fit(width, height, flipped)
                .squareAt(px, py, position, pieceScale, candidates, movableSquares);
    }

    @Override
    public void paint(Graphics2D g, RenderState s) {
        int w = s.width(), h = s.height();
        if (w <= 2 || h <= 2) return;
        PerspectiveCoordinateMapper mapper = PerspectiveCoordinateMapper.fit(w, h, s.flipped());
        PerspectiveProjection proj = mapper.projection();
        Preferences.GraphicsQuality quality = s.preferences().graphicsQuality();

        AffineTransform tx = g.getTransform();
        double dpi = Math.max(1.0, Math.min(2.0, Math.hypot(tx.getScaleX(), tx.getShearY())));
        StageKey key = new StageKey(w, h, s.flipped(), s.theme(), quality, dpi);
        if (!key.equals(cachedStageKey) || cachedStaticStage == null) {
            cachedStaticStage = buildStaticStage(w, h, mapper, s.theme(), quality, dpi);
            cachedStageKey = key;
        }

        // Reuse the complete static tabletop stage during movement.
        g.drawImage(cachedStaticStage, 0, 0, w, h, null);

        // 2. Dynamic perspective square highlights (last move, hover, selected bloom, check, legal targets, hint)
        paintPerspectiveHighlights(g, mapper, s);

        boolean directionalShadows = quality != Preferences.GraphicsQuality.LOW;
        MoveAnimation motion = s.motion();
        if (motion != null && motion.captured() != null) {
            int square = motion.capturedSquare();
            Point2D.Double anchor = mapper.squareAnchor(square);
            g.setComposite(AlphaComposite.SrcOver.derive(MoveAnimation.captureAlpha(s.moveProgress())));
            drawPose(g, s, new Pose(motion.captured(), anchor.x, anchor.y, mapper.squareWidth(square),
                    mapper.squareWidth(square), 0), directionalShadows);
            g.setComposite(AlphaComposite.SrcOver);
        }

        java.util.List<Pose> travel = motion == null ? java.util.List.of() : movingPieces(mapper, s, motion);
        int movingIndex = 0;
        for (var entry : PieceDepthSorter.backToFront(s.position(), s.flipped())) {
            int square = entry.square();
            if (s.hidesPiece(square)) continue;
            Point2D.Double anchor = mapper.squareAnchor(square);
            while (movingIndex < travel.size() && travel.get(movingIndex).y() < anchor.y) {
                drawPose(g, s, travel.get(movingIndex++), directionalShadows);
            }
            double tile = mapper.squareWidth(square);
            drawPose(g, s, new Pose(entry.piece(), anchor.x, anchor.y, tile, tile,
                    square == s.selected() ? tile * 0.055 : 0), directionalShadows);
        }
        while (movingIndex < travel.size()) drawPose(g, s, travel.get(movingIndex++), directionalShadows);

        // 6. Hint directional arrow
        if (s.hint() != null) {
            Point2D.Double a = mapper.squareCenter(s.hint().from());
            Point2D.Double b = mapper.squareCenter(s.hint().to());
            Classic2DBoardRenderer.drawArrow(g, a.x, a.y, b.x, b.y, mapper.squareWidth(s.hint().to()));
        }

        if (s.heldPoint() != null && s.heldSquare() >= 0) {
            drawPose(g, s, heldPose(mapper, s.position().piece(s.heldSquare()), s.heldPoint()), directionalShadows);
        }

        // 8. Checkmate golden sparks & outcome overlay
        if (s.celebrating()) {
            Point2D.Double center = proj.project(0.5, 0.5);
            Classic2DBoardRenderer.paintSparks(g, center.x, center.y, mapper.outerBR().x - mapper.outerBL().x, s.celebrationProgress());
        }
        if (s.overlayTitle() != null) {
            Rectangle box = mapper.boardBoundingBox();
            Classic2DBoardRenderer.paintOutcomeOverlay(g, box.x, box.y, box.width, box.height,
                    s.overlayTitle(), s.overlaySubtitle(), s.overlayAccent(), s.overlayAlpha());
        }
    }

    private record Pose(Piece piece, double x, double y, double tile, double spriteTile, double lift) {}

    private void drawPose(Graphics2D g, RenderState s, Pose pose, boolean directionalShadows) {
        pieceRenderer.drawPerspectivePiece(g, pose.piece(), pose.x(), pose.y(), pose.tile(), pose.spriteTile(),
                s.preferences().pieceScale(), s.preferences().highQuality(), pose.lift(),
                shadowRenderer, directionalShadows);
    }

    private static java.util.List<Pose> movingPieces(PerspectiveCoordinateMapper mapper, RenderState state,
                                                      MoveAnimation motion) {
        double progress = MoveAnimation.ease(state.moveProgress());
        Pose main = travelPose(mapper, motion.piece(), motion.move(), motion.release(), progress);
        if (motion.rook() == null) return java.util.List.of(main);
        Pose rook = travelPose(mapper, state.position().piece(motion.rook().to()), motion.rook(), null, progress);
        return rook.y() <= main.y() ? java.util.List.of(rook, main) : java.util.List.of(main, rook);
    }

    private static Pose travelPose(PerspectiveCoordinateMapper mapper, Piece piece, Move move,
                                   Point release, double progress) {
        double targetTile = mapper.squareWidth(move.to());
        if (release != null) {
            Pose start = heldPose(mapper, piece, release);
            Point2D.Double target = mapper.squareAnchor(move.to());
            return new Pose(piece, MoveAnimation.lerp(start.x(), target.x, progress),
                    MoveAnimation.lerp(start.y(), target.y, progress),
                    MoveAnimation.lerp(start.tile(), targetTile, progress), Math.max(start.tile(), targetTile),
                    start.lift() * (1 - progress));
        }

        double u = MoveAnimation.lerp(mapper.centerU(move.from()), mapper.centerU(move.to()), progress);
        double v = MoveAnimation.lerp(mapper.anchorV(move.from()), mapper.anchorV(move.to()), progress);
        Point2D.Double anchor = mapper.projection().project(u, v);
        double tile = mapper.squareWidthAt(v);
        double lift = tile * (piece.kind() == Kind.KNIGHT ? 0.18 : 0.07) * 4 * progress * (1 - progress);
        return new Pose(piece, anchor.x, anchor.y, tile, Math.max(mapper.squareWidth(move.from()), targetTile), lift);
    }

    private static Pose heldPose(PerspectiveCoordinateMapper mapper, Piece piece, Point point) {
        double v = mapper.projection().unproject(point.x, point.y).y;
        double tile = mapper.squareWidthAt(Math.max(0, Math.min(1, v)));
        return new Pose(piece, point.x, point.y + tile * 0.20, tile * 1.04, tile * 1.04, tile * 0.24);
    }

    private BufferedImage buildStaticStage(int w, int h, PerspectiveCoordinateMapper mapper,
                                           String theme, Preferences.GraphicsQuality quality, double dpi) {
        int pw = Math.max(4, (int) Math.round(w * dpi));
        int ph = Math.max(4, (int) Math.round(h * dpi));
        BufferedImage img = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.scale(dpi, dpi);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        PerspectiveProjection proj = mapper.projection();
        lighting.paintBackdropLighting(g, w, h, quality, 1.0f);
        shadowRenderer.paintBoardDropShadow(g, mapper);
        paint3DFrontSlab(g, mapper);
        paintPerspectiveFrame(g, mapper);
        textureRenderer.paintWarpedBoard(g, mapper, theme, quality);

        Path2D.Double boardOutline = proj.quadPath(0, 0, 1, 1);
        g.setColor(new Color(0, 0, 0, 105));
        g.setStroke(new BasicStroke(2.2f));
        g.draw(boardOutline);
        g.setPaint(new LinearGradientPaint(
                (float) mapper.outerTL().x, (float) mapper.outerTL().y,
                (float) mapper.outerBR().x, (float) mapper.outerBR().y,
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0xF6DC93), new Color(0xA47A26), new Color(0xF0D17B)}));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(proj.quadPath(-0.005, -0.005, 1.005, 1.005));
        g.setStroke(new BasicStroke(1f));

        paintCoordinates(g, mapper);
        lighting.paintForegroundVignette(g, w, h, quality, 0.65f);
        g.dispose();
        return img;
    }

    private void paint3DFrontSlab(Graphics2D g, PerspectiveCoordinateMapper mapper) {
        Point2D.Double bl = mapper.outerBL(), br = mapper.outerBR();
        double slab = mapper.slabThickness();

        Path2D.Double front = new Path2D.Double();
        front.moveTo(bl.x, bl.y);
        front.lineTo(br.x, br.y);
        front.lineTo(br.x + 2, br.y + slab);
        front.lineTo(bl.x - 2, bl.y + slab);
        front.closePath();

        g.setPaint(new LinearGradientPaint((float) bl.x, (float) bl.y, (float) br.x, (float) (br.y + slab),
                new float[]{0f, 0.30f, 0.50f, 0.70f, 1f},
                new Color[]{new Color(0x16120D), new Color(0x281F15), new Color(0x36291A),
                        new Color(0x221B13), new Color(0x120F0C)}));
        g.fill(front);

        g.setColor(new Color(212, 175, 55, 38));
        g.setStroke(new BasicStroke(1.1f));
        double w = br.x - bl.x;
        g.drawLine((int) (bl.x + w * 0.14), (int) (bl.y + slab * 0.25), (int) (bl.x + w * 0.22), (int) (bl.y + slab * 0.85));
        g.drawLine((int) (bl.x + w * 0.76), (int) (bl.y + slab * 0.20), (int) (bl.x + w * 0.84), (int) (bl.y + slab * 0.82));

        g.setPaint(new LinearGradientPaint((float) bl.x, (float) bl.y, (float) br.x, (float) br.y,
                new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
                new Color[]{new Color(0xD9B04A), new Color(0xFFF0BC), new Color(0xD4AF37),
                        new Color(0xFBE5A2), new Color(0xB88B2B)}));
        g.setStroke(new BasicStroke(3.2f));
        g.drawLine((int) Math.round(bl.x), (int) Math.round(bl.y), (int) Math.round(br.x), (int) Math.round(br.y));

        g.setPaint(new LinearGradientPaint((float) bl.x, (float) (bl.y + slab), (float) br.x, (float) (br.y + slab),
                new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(0x8A6620), new Color(0xE8C462), new Color(0x7B591B)}));
        g.setStroke(new BasicStroke(2.2f));
        g.drawLine((int) Math.round(bl.x - 2), (int) Math.round(bl.y + slab), (int) Math.round(br.x + 2), (int) Math.round(br.y + slab));

        g.drawLine((int) Math.round(bl.x), (int) Math.round(bl.y), (int) Math.round(bl.x - 2), (int) Math.round(bl.y + slab));
        g.drawLine((int) Math.round(br.x), (int) Math.round(br.y), (int) Math.round(br.x + 2), (int) Math.round(br.y + slab));
        g.setStroke(new BasicStroke(1f));

        int midX = (int) Math.round((bl.x + br.x) * 0.5);
        int midY = (int) Math.round(bl.y + slab * 0.5);
        int crownSz = Math.max(15, (int) Math.round(slab * 0.78));
        RoyalIcons.draw(g, RoyalIcons.Glyph.CROWN, midX - crownSz / 2, midY - crownSz / 2, crownSz, new Color(0xF6D986));

        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int dir : new int[]{-1, 1}) {
            Path2D.Double stem = new Path2D.Double();
            stem.moveTo(midX + dir * (crownSz * 0.58), midY + crownSz * 0.34);
            stem.quadTo(midX + dir * (crownSz * 1.15), midY + crownSz * 0.14,
                    midX + dir * (crownSz * 1.48), midY - crownSz * 0.28);
            g.setColor(new Color(0xD8AE48));
            g.draw(stem);
            for (int k = 0; k < 3; k++) {
                double t = 0.25 + k * 0.28;
                double lx = midX + dir * (crownSz * (0.68 + t * 0.74));
                double ly = midY + crownSz * (0.28 - t * 0.52);
                g.setColor(new Color(0xF5D886));
                g.fill(new Ellipse2D.Double(lx - 2.6, ly - 1.4, 5.2, 2.8));
            }
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintPerspectiveFrame(Graphics2D g, PerspectiveCoordinateMapper mapper) {
        PerspectiveProjection proj = mapper.projection();
        double fl = PerspectiveCoordinateMapper.FRAME_LEFT;
        double fr = PerspectiveCoordinateMapper.FRAME_RIGHT;
        double ft = PerspectiveCoordinateMapper.FRAME_TOP;
        double fb = PerspectiveCoordinateMapper.FRAME_BOTTOM;

        Path2D.Double outerPath = proj.quadPath(-fl, -ft, 1.0 + fr, 1.0 + fb);
        g.setColor(new Color(235, 190, 74, 34));
        g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outerPath);
        g.setColor(new Color(246, 212, 114, 52));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outerPath);

        g.setPaint(new GradientPaint(
                (float) mapper.outerTL().x, (float) mapper.outerTL().y, new Color(0x231B13),
                (float) mapper.outerBR().x, (float) mapper.outerBR().y, new Color(0x110E0B)));
        g.fill(outerPath);

        g.setPaint(new LinearGradientPaint(
                (float) mapper.outerTL().x, (float) mapper.outerTL().y,
                (float) mapper.outerBR().x, (float) mapper.outerBR().y,
                new float[]{0f, 0.28f, 0.50f, 0.76f, 1f},
                new Color[]{new Color(0xF9E3A0), new Color(0xC69834), new Color(0x7C5A1C),
                        new Color(0xD8AE48), new Color(0xFAE5A4)}));
        g.setStroke(new BasicStroke(3.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outerPath);

        g.setColor(new Color(214, 174, 72, 115));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(proj.quadPath(-fl * 0.72, -ft * 0.68, 1.0 + fr * 0.72, 1.0 + fb * 0.76));

        Point2D.Double leftMid = proj.project(-fl, 0.56);
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) leftMid.x, (float) leftMid.y, 48f, new Color(255, 224, 128, 92));
        Point2D.Double rightMid = proj.project(1.0 + fr, 0.42);
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) rightMid.x, (float) rightMid.y, 42f, new Color(255, 224, 128, 76));
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) mapper.outerBL().x + 14f, (float) mapper.outerBL().y - 4f,
                52f, new Color(255, 232, 142, 110));
        RoyalGlowEffect.paintAmbientSpotlight(g, (float) mapper.outerBR().x - 14f, (float) mapper.outerBR().y - 4f,
                52f, new Color(255, 232, 142, 96));

        paintPerspectiveCorner(g, proj, -fl * 0.88, -ft * 0.84, 0.085, 0.068);
        paintPerspectiveCorner(g, proj, 1.0 + fr * 0.88, -ft * 0.84, -0.085, 0.068);
        paintPerspectiveCorner(g, proj, -fl * 0.88, 1.0 + fb * 0.86, 0.092, -0.075);
        paintPerspectiveCorner(g, proj, 1.0 + fr * 0.88, 1.0 + fb * 0.86, -0.092, -0.075);
        g.setStroke(new BasicStroke(1f));
    }

    private static void paintPerspectiveCorner(Graphics2D g, PerspectiveProjection proj,
                                               double cu, double cv, double du, double dv) {
        Point2D.Double pCorner = proj.project(cu, cv);
        Point2D.Double pArmU = proj.project(cu + du, cv);
        Point2D.Double pArmV = proj.project(cu, cv + dv);
        Point2D.Double pInner = proj.project(cu + du * 0.45, cv + dv * 0.45);
        Point2D.Double pScrollU = proj.project(cu + du * 0.72, cv + dv * 0.18);
        Point2D.Double pScrollV = proj.project(cu + du * 0.18, cv + dv * 0.72);

        g.setColor(new Color(0xF9E39E));
        g.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double bracket = new Path2D.Double();
        bracket.moveTo(pArmU.x, pArmU.y);
        bracket.lineTo(pCorner.x, pCorner.y);
        bracket.lineTo(pArmV.x, pArmV.y);
        g.draw(bracket);

        g.setColor(new Color(0xD8AE48));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double scroll = new Path2D.Double();
        scroll.moveTo(pScrollU.x, pScrollU.y);
        scroll.quadTo(pInner.x, pInner.y, pScrollV.x, pScrollV.y);
        g.draw(scroll);

        g.setColor(new Color(0xFFF3C6));
        g.fill(new Ellipse2D.Double(pInner.x - 2.2, pInner.y - 2.2, 4.4, 4.4));
    }

    private void paintPerspectiveHighlights(Graphics2D g, PerspectiveCoordinateMapper mapper, RenderState s) {
        PerspectiveProjection proj = mapper.projection();

        if (s.last() != null) {
            fillSquare(g, mapper, s.last().from(), new Color(240, 200, 88, 68));
            fillSquare(g, mapper, s.last().to(), new Color(246, 210, 98, 92));
        }

        if (s.hovered() >= 0 && s.hovered() != s.selected() && s.playable()
                && (isOwn(s.position(), s.hovered()) || isCandidate(s.candidates(), s.hovered()))) {
            Path2D.Double hp = proj.squarePath(mapper.visualCol(s.hovered()), mapper.visualRow(s.hovered()));
            g.setColor(new Color(248, 220, 134, 38));
            g.fill(hp);
            g.setColor(new Color(248, 222, 138, 165));
            g.setStroke(new BasicStroke(1.8f));
            g.draw(hp);
        }

        if (s.selected() >= 0) {
            Path2D.Double sp = proj.squarePath(mapper.visualCol(s.selected()), mapper.visualRow(s.selected()));
            g.setColor(new Color(255, 226, 114, 135));
            g.fill(sp);
            g.setColor(new Color(255, 246, 195, 235));
            g.setStroke(new BasicStroke(2.8f));
            g.draw(sp);
        }

        if (s.check()) {
            int ksq = s.position().king(s.position().turn());
            Path2D.Double kp = proj.squarePath(mapper.visualCol(ksq), mapper.visualRow(ksq));
            g.setColor(new Color(225, 44, 36, 168));
            g.fill(kp);
            g.setColor(new Color(255, 122, 108, 225));
            g.setStroke(new BasicStroke(2.4f));
            g.draw(kp);
        }

        for (Move candidate : s.candidates()) {
            int to = candidate.to();
            boolean isCapture = s.position().captured(candidate) != null;
            Path2D.Double tp = proj.squarePath(mapper.visualCol(to), mapper.visualRow(to));
            Point2D.Double tc = mapper.squareCenter(to);
            double tw = mapper.squareWidth(to);
            double td = proj.localSquareDepth((mapper.visualRow(to) + 0.5) / 8.0);
            if (isCapture) {
                g.setColor(new Color(235, 185, 72, 68));
                g.fill(tp);
                g.setColor(new Color(248, 212, 102, 225));
                g.setStroke(new BasicStroke(2.6f));
                g.draw(new Ellipse2D.Double(tc.x - tw * 0.36, tc.y - td * 0.34, tw * 0.72, td * 0.68));
            } else {
                g.setColor(new Color(98, 146, 48, 98));
                g.fill(tp);
                g.setColor(new Color(142, 198, 72, 165));
                g.setStroke(new BasicStroke(1.5f));
                g.draw(tp);
                double rx = Math.max(6.0, tw * 0.22);
                double ry = Math.max(4.5, td * 0.24);
                g.setColor(new Color(118, 178, 52, 205));
                g.fill(new Ellipse2D.Double(tc.x - rx, tc.y - ry, rx * 2, ry * 2));
                g.setColor(new Color(210, 240, 140, 185));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(new Ellipse2D.Double(tc.x - rx, tc.y - ry, rx * 2, ry * 2));
            }
        }

        if (s.hint() != null) {
            fillSquare(g, mapper, s.hint().from(), new Color(112, 227, 156, 62));
            fillSquare(g, mapper, s.hint().to(), new Color(112, 227, 156, 86));
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void paintCoordinates(Graphics2D g, PerspectiveCoordinateMapper mapper) {
        PerspectiveProjection proj = mapper.projection();
        Font fileFont = RoyalStyle.sans(Font.BOLD, Math.max(11.5f, (float) (mapper.squareWidthAt(0.95) * 0.165)));
        g.setFont(fileFont);
        FontMetrics ffm = g.getFontMetrics(fileFont);
        g.setColor(new Color(0xE8CE88));
        for (int col = 0; col < 8; col++) {
            String file = String.valueOf((char) (mapper.flipped() ? 'H' - col : 'A' + col));
            Point2D.Double p = proj.project((col + 0.5) / 8.0, 1.0 + PerspectiveCoordinateMapper.FRAME_BOTTOM * 0.54);
            g.drawString(file, (float) (p.x - ffm.stringWidth(file) / 2.0), (float) (p.y + ffm.getAscent() * 0.36));
        }

        for (int row = 0; row < 8; row++) {
            double v = (row + 0.5) / 8.0;
            String rank = String.valueOf((char) (mapper.flipped() ? '1' + row : '8' - row));
            float sz = (float) Math.max(9.5, mapper.squareWidthAt(v) * 0.155);
            Font rf = RoyalStyle.sans(Font.BOLD, sz);
            g.setFont(rf);
            FontMetrics rfm = g.getFontMetrics(rf);
            Point2D.Double p = proj.project(-PerspectiveCoordinateMapper.FRAME_LEFT * 0.48, v);
            g.drawString(rank, (float) (p.x - rfm.stringWidth(rank) / 2.0), (float) (p.y + rfm.getAscent() * 0.35));
        }
    }

    private void fillSquare(Graphics2D g, PerspectiveCoordinateMapper mapper, int sq, Color color) {
        g.setColor(color);
        g.fill(mapper.projection().squarePath(mapper.visualCol(sq), mapper.visualRow(sq)));
    }

    private static boolean isDark(int sq) { return (Square.file(sq) + Square.rank(sq)) % 2 == 0; }
    private static boolean isOwn(Position pos, int sq) { Piece p = pos.piece(sq); return p != null && p.side() == pos.turn(); }
    private static boolean isCandidate(java.util.List<Move> list, int sq) {
        for (Move m : list) if (m.to() == sq) return true;
        return false;
    }
}
