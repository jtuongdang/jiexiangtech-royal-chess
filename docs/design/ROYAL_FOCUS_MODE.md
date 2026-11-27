# Royal Arena · Royal Focus 2.5D

[Arena Duel reference](royal-focus-reference.png) · [Live Swing capture](../screenshots/royal-focus-actual.png) · [Side-by-side comparison](../screenshots/royal-focus-comparison.png)

The player portraits, live clocks and VS mark sit above a perspective board. The castle scenery
is a separate background, with the toolbar below the board and a collapsible move history and
captured pieces drawer. Classic 2D retains its square grid and carved frame. Both modes use the
same ivory and onyx pieces with gold trim, and the same eight marble samples.

## Board geometry and rendering

| Measurement | Relative to board viewport |
| --- | --- |
| Far-to-near frame width | 0.74 |
| Near frame width | Up to 87.5% of viewport width |
| Tabletop height | Up to 76% of viewport height |
| Front slab | 9% of tabletop height, with a 16 px minimum |
| Playing grid | 8 × 8, with projection from a square board plane |

`PerspectiveCoordinateMapper` fits the geometry and shares the homography with mouse
hit testing. It uses the current board orientation when mapping ranks and files.
`PerspectiveTextureRenderer` caches the warped 64-square surface. `RoyalPieceRenderer`
draws the 12 Retina-ready sprites upright with depth-dependent scale. Board highlights,
shadows, reflections and glows are rendered from match state. Players can switch views
during a match, and SQLite stores the view mode and graphics quality per desktop profile.

Each move eases between square anchors. A dragged piece settles from its release point,
captured pieces fade near arrival, castling animates both pieces, and promotion changes the
sprite on landing. Ultra requests an 8 ms repaint interval while a piece is moving.

## Portraits and source artwork

Settings offers six portraits and the Royal emblem. The desktop stores the selection per
profile and sends its preset ID when creating or joining an online room. The server stores
that ID on the seat. Each portrait is rendered from a packaged asset.

[Arena avatar picker](../screenshots/royal-arena-avatars.png) · [Asset manifest](../ASSET_MANIFEST.md)

The supplied Arena Duel and Battle Map PNGs are preserved locally in `raw-assets`. The Arena
Duel supplied the camera, palace balcony and material references. Battle Map remains the
reference for a future campaign. ImageGen created four separate source images for this phase.

- Palace balcony with a distant castle at dusk, warm gold firelight and a clear floor at center for the live board.
- A transparent 6 × 2 atlas of complete orthodox pieces, with black onyx on the top row and warm ivory below, using gold trim.
- An overhead texture sheet with four cream marble samples and four polished onyx samples with fine gold veins.
- A 3 × 2 portrait sheet with Knight, Queen, Sage, Archer, Sovereign and Ranger, framed as head-and-shoulders portraits.
