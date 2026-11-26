# Design system

Royal Luxury combines obsidian surfaces, ivory and onyx marble, metallic gold accents, and the
supplied Staunton chess artwork. The approved Splash and Login screens establish the visual
baseline for the entire desktop client.

JIEXIANGTECH (杰祥科技) branding is centralized in `BrandConstants` and `BrandingManager`.
`AssetManager` loads images once from the classpath for both IDE and bundled launchers, and
computes visible alpha bounds (`alpha > 16`) for all 12 piece sprites so pieces render at full
proportional scale without transparent canvas shrinkage. `ThemeManager` applies the Royal Slate
treatment to the original tile textures.

## Color tokens

| Role | Color |
| --- | --- |
| Window canvas | `#0B0B0D` |
| Surface | `#141316` |
| Elevated surface | `#1B191E` |
| Raised controls | `#232026` |
| Metallic gold | `#D4AF37` / `#9E7823` |
| Champagne gold | `#F2D78B` |
| Ivory | `#F5F0E4` |
| Primary text | `#F5F5F5` |
| Muted text | `#9E988E` |
| Hairline borders | `#362E22` / `#6E552B` |
| Destructive surface / text | `#3B171B` / `#FFA0A0` |
| Error / success text | `#FF7777` / `#70E39C` |

## Reusable component system

- `RoyalStyle`: centralized palette, typography (`Georgia` serif display, system sans UI, tabular monospaced clocks), pills, dividers, and FlatLaf overrides (including metallic gold slider and scrollbar theming).
- `RoyalIcons`: antialiased Java2D vector icon library for navigation, gameplay controls, replay transport, and status medallions.
- `RoyalButton` & `RoyalIconButton`: hierarchical button system (`PRIMARY`, `SECONDARY`, `GHOST`, `DANGER`, `SEGMENT`) with metallic gold gradients, specular borders, hover states, and focus rings.
- `RoyalPanel`, `RoyalCard`, & `RoyalBackgroundPanel`: elevated dark obsidian containers with subtle layered shadows, gold accent bars, interactive hover/selection states, and cinematic vignette backdrops.
- `RoyalNavigationPanel`: collapsible left sidebar with active gold indicator bar, full mode on lobby screens (`220px`) and auto-minimized icon rail (`66px`) during live Gameplay, Puzzles, and Replay so the chessboard dominates the viewport.
- `RoyalPlayerCard` & `RoyalGameClock`: live player banners featuring piece avatars, side/role pills, active-turn gold illumination, check badges, and large tabular clocks with low-time warning styling.
- `RoyalMoveHistory` & `RoyalCapturedTray`: custom-rendered move notation list with alternating dark bands, active ply pill, and click-to-seek support; captured pieces tray displaying content-trimmed piece sprites sorted by material value with material advantage delta.
- `RoyalTextField`, `RoyalToggle`, & `RoyalDialog`: rounded search/input fields, custom gold pill switches, and undecorated obsidian-gold modal dialogs (including visual piece-card promotion picker and branded About dialog).

## Board craftsmanship and responsiveness

- `BoardCoordinateMapper` fits the 8×8 board tightly with a `20px` obsidian-gold bezel and centered rank/file coordinates.
- `ChessBoardPanel` renders subtle ambient gold board glow, visible-bounds-scaled pieces (`King > Queen > Bishop > Knight > Rook > Pawn`) aligned on a shared baseline, layered contact shadows, warm rim lift behind black pieces on dark squares, hover/selection/legal-move/last-move/check/hint-arrow visual feedback, cubic ease-out move animation, and elevated drag rendering.
- Minimum supported window size remains `1020 × 690` pixels, verified alongside standard desktop and fullscreen layouts on macOS Retina displays. English and Vietnamese resource bundles maintain full key parity.
