# BlockHud

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A lightweight Paper plugin that shows a **top-center HUD** with information about the block you're looking at: the block name, a digging-progress bar, and the tool required to break it.

---

## Features

| Feature | Description |
|---|---|
| **Top-center HUD** | Uses a boss bar at the top of the screen to show block info. |
| **Block name** | Displays the translated name of the block you're targeting. |
| **Digging bar** | Fills up as you swing at the block, showing approximate mining progress. |
| **Required tool** | Shows whether the block needs a pickaxe, axe, shovel, hoe, shears, sword, or just your hand. |
| **Per-player** | Each player sees their own HUD. |

---

## How It Works

The plugin ray-traces from the player's eyes every tick to find the block they're looking at within 5 blocks. When a block is found:

1. A boss bar appears at the top center.
2. The bar title shows the block name and required tool.
3. The bar progress fills as the player swings at the block.
4. The bar color changes from white → yellow → green → red as progress increases.
5. The bar hides when looking at air or beyond 5 blocks.

---

## Requirements

| Requirement | Version |
|---|---|
| Server software | [Paper](https://papermc.io/downloads/paper) |
| Minecraft / Paper API | 26.1 (Minecraft 1.21.x) |
| Java | 25 |

---

## Installation

1. Download the latest `BlockHud-*.jar` from the [Releases](../../releases) page.
2. Place the JAR in your server's `plugins/` directory.
3. Restart your server.
4. Look at any block — the HUD appears automatically.

---

## Building from Source

```bash
git clone <repo-url>
cd BlockHud
mvn package
```

The compiled JAR will be at `target/BlockHud-1.0.0.jar`.

---

## License

This project is released under the [MIT License](LICENSE).
