# Immersive Paintings — Forge 1.12.2 Backport (alpha)

An independent **experimental** Forge 1.12.2 reimplementation of the basic custom-image painting workflow. Other version branches in this repository are unaffected.

Original mod by [Luke100000](https://github.com/Luke100000/ImmersivePaintings), GPL-3.0-only. This backport is also GPL-3.0-only.

## Features

- Craft an **Immersive Painting** using a vanilla painting and paper.
- Right-click the item **in the air**, or **right-click an already-placed painting**, to open the editor.
- Enter a **public HTTPS URL to a PNG/JPEG image** (for example `https://example.com/picture.png`) or a local PNG/JPEG file path, and choose width and height (1–4 blocks each).
- Click **Upload to held painting**, or **Update painting on wall** when editing a placed painting. All image transfers are now **chunked to 12 KiB per Forge custom packet**, avoiding the 32,767-byte 1.12.2 custom-payload limit. The server validates uploads, checks interaction distance and build permissions for placed paintings, then syncs changes to nearby clients.
- Place the configured item on a vertical wall; other modded clients see the painting through entity spawn data, and it saves with the world.
- Breaking a painting drops its configured item, except in creative mode.

Local paths may be absolute, or relative to the Minecraft game directory. Remote images are downloaded **on the client**, resized locally and sent to the server through the existing upload packet. Only HTTPS links are accepted, up to 3 HTTPS redirects and 8 MiB downloaded. Server image uploads remain capped at 384 KiB after conversion. URLs must return actual PNG/JPEG image data, not HTML image-preview pages. Image hosts can see your client IP address when downloading.

## Requirements

- Minecraft **1.12.2**, Forge **14.23.5.2860**, **Java 8**.
- Both client and server need this mod in multiplayer.
- Image upload cap: 384 KiB after processing, max 512×512 pixels; each painting is up to 4×4 blocks. Image bytes are transferred in 12 KiB packets (uploads, updates, and initial entity tracking); the spawn packet contains only painting geometry.

## Building

With JDK 8 installed, run: ./gradlew build

The reobfuscated JAR should appear under build/libs. See GitHub Actions for an automated Java 8 build and artifact.

## Known limitations

**Alpha / unverified in-game.** This is not a complete or data-compatible port. Image galleries, frames, glow variants, graffiti, GIFs, datapack imports, migration of painting data from other MC versions are not implemented. Image blobs are stored per painting/item in NBT (not deduplicated); back up worlds before testing. A file picker is not included.

Original assets and copyright remain with their authors. GPL-3.0-only; see LICENSE.
