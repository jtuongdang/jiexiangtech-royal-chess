# Royal Focus Mode 2.5D — Strict Design & Geometry Specification

Reference image: [royal-focus-reference.png](royal-focus-reference.png) (`1024 × 576`, exact `16:9` aspect ratio).

## 1. Measured Normalized Layout Coordinates

All coordinates below were measured directly from `royal-focus-reference.png` (`W = 1024`, `H = 576`) so the Java Swing presentation layer scales proportionally across `1280×800`, `1440×900`, `1920×1080`, `2560×1600`, and minimum window sizes.

| Region | Reference Pixels (`x, y, w, h`) | Normalized Ratio (`W`, `H`) | Notes |
| --- | --- | --- | --- |
| Left Sidebar | `0, 0, 133, 576` | `0.000, 0.000, 0.130, 1.000` | Dark obsidian panel, luminous gold right edge at `x = 0.130W`, ChessMaster logo top, 9 gold-icon nav items, decorative black-gold Knight bottom, auto-dims during active focus and brightens on mouse hover |
| Gameplay Stage | `133, 0, 891, 576` | `0.130, 0.000, 0.870, 1.000` | Centered spotlighted chess room (`royal-focus-background.png`), deep corner vignette |
| Top Corporate Brand | Centered above stage (`y = 8..42`) | `y = 0.014..0.073` | Horizontal `JIEXIANGTECH 杰祥科技` lockup (`company-horizontal.png`) |
| Top Game Wordmark | Centered below corporate mark (`y = 48..112`) | `y = 0.083..0.194` | Crown + `CHESSMASTER / ROYAL EDITION` emblem |
| Black Player Card | Stage top-left (`y = 58..110`) | `x_stage = 0.02..0.33, y = 0.10..0.19` | Circular royal portrait ring, name, `AI`/`Local` badge, real Elo, dark recessed clock |
| White Player Card | Stage top-right (`y = 58..110`) | `x_stage = 0.67..0.98, y = 0.10..0.19` | Active player gets metallic gold glow border, warm horizontal gradient, left crown indicator, green turn dot, illuminated gold clock |
| 2.5D Outer Frame (Top Edge) | `x = 315..781, y = 178` | `width = 0.524 × W_stage, y = 0.309H` | Far edge of beveled metallic gold frame |
| 2.5D Outer Frame (Front Lip) | `x = 174..921, y = 476` | `width = 0.840 × W_stage, y = 0.826H` | Near edge of tabletop surface (`taper = 0.623`) |
| 2.5D Board Front Slab | `y = 476..508` | `thickness = 0.056H` | Dark marble vertical front face with metallic gold rails and sculpted central royal crown & laurel emblem |
| Bottom Horizontal Toolbar | Centered below board (`y = 524..562`) | `y = 0.910..0.976` | Single horizontal row of dark-obsidian gold-bordered pills (`Save`, `Undo`, `Hint`, `Draw`, `Resign`, `Flip Board`, `Sound`, `Full Screen`, plus toggleable Move History/Captured drawer & 2D/2.5D mode switch) |

## 2. Perspective Projection & Homography Math

- The playing grid occupies canonical board coordinates `(u, v) ∈ [0, 1] × [0, 1]`, where `u = col / 8` (left to right) and `v = row / 8` (far rank to near rank).
- The outer ornate frame extends to `[-0.078, 1.078]` horizontally and `[-0.048, 1.110]` vertically (giving clean foreshortened side rank channels `1–8` and front file channel `A–H`).
- `PerspectiveProjection` solves the exact `3×3` projective homography matrix `H` mapping the 4 canonical corners `(0,0), (1,0), (1,1), (0,1)` onto the 4 screen-space trapezoid corners `(TL, TR, BR, BL)` via Gaussian elimination, and precomputes `H⁻¹` for exact inverse mouse hit-testing (`PerspectiveCoordinateMapper`).
- **True Depth Foreshortening**: Because `H` is projective (`x' = (h00 u + h01 v + h02)/w`, `y' = (h10 u + h11 v + h12)/w`), near ranks are naturally ~1.6× deeper vertically and ~1.6× wider horizontally than far ranks, converging smoothly toward the camera vanishing point.
- **Texture Warping**: `PerspectiveTextureRenderer` caches the warped 64-tile marble board surface into an offscreen Retina-scaled `BufferedImage` whenever geometry, theme, or quality changes, using per-pixel inverse homography lookup into `tile-light.png` and `tile-dark.png` (zero per-frame allocation, 60 FPS rendering!).
- **2.5D Billboard Pieces**: `PieceDepthSorter` sorts all pieces back-to-front (`row = 0 → 7`) so near pieces naturally overlap far pieces. Each piece stands upright on its square's projected anchor `(cx, baseY)`, scaled proportionally by local square width `localScale(row)` with contact shadows, directional light shadows, and subtle surface reflections (`ShadowRenderer`, `ReflectionRenderer`).

## 3. Lighting, Palette & Focus Behavior

- **Palette**: Obsidian `#0B0B0D`, surface `#141316`, metallic gold `#F6DE98 / #D4AF37 / #8A651D`, warm spotlight `rgba(242, 198, 86, 0.25)`, luminous selected square `#FFF4BE`, sage-gold legal target disc `#A8C958`.
- **Cinematic Lighting Pipeline** (`CinematicLightingEngine`):
  1. Darkened moody chess hall backdrop (`royal-focus-background.png`).
  2. Warm golden radial spotlight (`SpotlightRenderer`) centered over the 2.5D board.
  3. Subtle board-surface piece reflections (`ReflectionRenderer`, clipped to the 8×8 trapezoid!).
  4. Grounded contact + directional cast shadows (`ShadowRenderer`).
  5. Soft corner vignette (`VignetteRenderer`) focusing visual attention squarely on the board without obscuring clocks or buttons.
- **Focus Mode Auto-Dim**: During Royal Focus 2.5D, the Sidebar and Bottom Toolbar gently dim (`alpha = 0.68`) when the pointer is on the board and smoothly restore to full brightness (`alpha = 1.0`) when hovered!
- **Live Mode Switching**: Players can toggle seamlessly between **Classic 2D** and **Royal Focus 2.5D** mid-game without losing state, clocks, or move history; preference and `GraphicsQuality` (`LOW`, `MEDIUM`, `HIGH`, `ULTRA`) persist to SQLite.
