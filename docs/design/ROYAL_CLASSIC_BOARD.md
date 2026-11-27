# Classic 2D board · Interface-2

The supplied 1586 × 992 reference measures 828 × 842 px at the outside of the frame and
713 × 730 px across the visible grid. Classic 2D uses 64 square cells, with the reference
bezel widths and heights normalized independently to that grid. The reference itself has
approximately 89.1 × 91.3 px per cell, so the live grid regularizes this small difference.

| Geometry | Reference | Live ratio |
| --- | ---: | ---: |
| Left and right bezel | 68 and 47 px | 9.54% and 6.59% of grid width |
| Top and bottom bezel | 43 and 69 px | 5.89% and 9.45% of grid height |
| Legal target diameter | about 21 px | 23% of a cell |
| White knight | 67 × 77 px | 75.2% of cell width × 84.4% of cell height |

The Classic renderer fits those ratios to the available window space, rounding to the nearest
screen pixel. Each piece uses a rectangle measured by side and kind in the reference.
File and rank labels are painted dynamically and follow the current orientation. The frame is
an eight-slice bezel with a transparent center. The eight marble samples and the 12 piece
sprites use the Royal Arena artwork. Positions, move highlights and animations are rendered independently.

The comparison uses a live six-ply position with a selected knight on e4. The position
and sidebar content can differ from the supplied mockup. Geometry matches the measured
ratios, while the 12 redrawn pieces retain small differences in reflections and ornament.

[Original board crop](royal-classic-reference.png) · [Reference and live board](../screenshots/royal-classic-comparison.png)

## Original Interface-2 sprite generation

The built-in ImageGen tool produced one 2172 × 724 RGBA atlas. The unmodified local atlas
is stored at `raw-assets/royal-classic-sprites.png`. The current source sheets are documented
in the [asset manifest](../ASSET_MANIFEST.md). Run `java scripts/PrepareAssets.java` and
then `java scripts/PrepareBranding.java` with Java 17 to rebuild the derivatives and manifest.

<details>
<summary>ImageGen prompt</summary>

Create ONE transparent game sprite atlas from the user's provided ChessMaster Royal Edition Interface-2.png. The input image is ONLY the visual reference for the isolated chess pieces on the central board; ignore the interface and text. Extract/redraw the 12 visible orthodox chess piece designs with excellent fidelity: polished solid black onyx or warm ivory bodies with crisp metallic gold trim, rounded pawns, tall crest of each crenellated rook, clearly left-facing knight horse head with thick gold ribbed mane and flared oval ivory/onyx pedestal, bishop with crisp slotted miter, queen with point crown and five gold finials, and king with prominent gold cross and gold crown arches. Carefully preserve the broad, squat silhouettes, side profile, gold banding, and low frontal camera angle in the reference. Single atlas, 6 equal columns by exactly 2 rows, strictly left to right each row: rook, knight, bishop, queen, king, pawn. Top row BLACK ONYX plus gold. Bottom row WARM WHITE IVORY plus gold. Each of the 12 complete individual pieces is centered in its cell with a substantial invisible gutter between neighbors; pieces must never touch nor be clipped. All bases aligned consistently within each row and viewed at the same height, no detached ornaments or shadows. Same exact style and sculpted design across the two colors. Leave genuinely transparent RGBA background, transparency between and underneath all pieces. No game board, no marbling background, no tile, no shadows below pieces, no labels, no letters, no framing, no grid lines. Fit the twelve sprites into a wide high-resolution rectangular atlas.

</details>
