# Immersive Paintings — Forge 1.12.2 Backport (alpha)

An independent **experimental** Forge 1.12.2 reimplementation of the basic custom-image painting workflow. Other version branches in this repository are unaffected.

Original mod by [Luke100000](https://github.com/Luke100000/ImmersivePaintings), GPL-3.0-only. This backport is also GPL-3.0-only.

## Features

- Craft an **Immersive Painting** using a vanilla painting and paper.
- Right-click the item **in the air** to open the editor.
- Enter a local PNG/JPEG path, and choose width and height (1–4 blocks each).
- Click **Upload to held painting**. The client resizes the image and sends it to the server; the server validates it and saves it in the held item's NBT.
- Place the configured item on a vertical wall; other modded clients see the painting through entity spawn data, and it saves with the world.
- Breaking a painting drops its configured item, except in creative mode.

Paths may be absolute, or relative to the Minecraft game directory.

## Requirements

- Minecraft **1.12.2**, Forge **14.23.5.2860**, **Java 8**.
- Both client and server need this mod in multiplayer.
- Image upload cap: 384 KiB after processing, max 512×512 pixels; each painting is up to 4×4 blocks.

## Building

With JDK 8 installed, run: ./gradlew build

The reobfuscated JAR should appear under build/libs. See GitHub Actions for an automated Java 8 build and artifact.

## Known limitations

**Alpha / unverified in-game.** This is not a complete or data-compatible port. Image galleries, frames, glow variants, graffiti, GIFs, datapack imports, in-world editing and migration of painting data from other MC versions are not implemented. Image blobs are stored per painting/item in NBT (not deduplicated); back up worlds before testing. A file picker is not included.

Original assets and copyright remain with their authors. GPL-3.0-only; see LICENSE.
