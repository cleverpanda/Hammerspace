# Hammerspace

https://en.wikipedia.org/wiki/Hammerspace

Pockets that hold far more than they should. A NeoForge 1.21.1 rewrite of p455w0rd's /dank/null for 1.12.2, with a new name and new art.

Each pocket stores a few kinds of item in huge stacks. It picks up items it already holds and places blocks straight from the stack. Hoppers and pipes can reach it when it sits in a Pocket Dock.

## Pockets

| Pocket | Kinds of item | Per kind |
|---|---|---|
| Redstone | 9 | 256 |
| Lapis | 18 | 1,024 |
| Iron | 27 | 4,096 |
| Gold | 36 | 16,384 |
| Diamond | 45 | 65,536 |
| Emerald | 54 | 2.1 billion |
| Creative | 54 | never runs out |

The creative pocket comes from the creative tab only. A creative player can lock it so its contents stay fixed.

## Crafting

- **Lining:** the tier's material in the corners, coal blocks on the edges and a stained glass pane of the tier's colour in the middle.
- **Pocket:** five linings in a plus shape.
- **Upgrade:** a pocket in the middle with four linings of the next tier around it. The pocket keeps its contents.
- **Pocket Dock:** emeralds in the corners, redstone on the edges and obsidian in the middle.

## Using a pocket

- Right-click the air to open the pocket. Right-click a block to place the selected item, or empty the selected bucket.
- Items you pick up go into a pocket in your inventory that already holds that item. When a slot is full, the extra items are destroyed, as in /dank/null.
- To pick the selected item, sneak and scroll, use pick-block on a block the pocket holds, or Alt+click it in the pocket.
- The HUD next to the hotbar shows the selected item and how many are stored.

In the pocket screen:

| Input | Does |
|---|---|
| Click | put in or take out |
| Shift+click | move a stack to your inventory |
| Ctrl+click | cycle how many hoppers and pipes leave behind (all, 1, 16, 64, none; the amounts are in the server config) |
| P+click | cycle how many placing leaves behind |
| O+click | turn unify on or off for that slot |
| Alt+click | select the item |

Hold Shift over a slot to see these controls in its tooltip. The Open, Next, Previous and Toggle HUD keys start unbound; set them in Controls under Hammerspace.

**Unify:** a slot with unify on also takes items that share an allowed tag with the stored item. For example, a slot holding one mod's tin ingot also takes every other mod's tin ingot and turns them into the stored one.

## Pocket Dock

Right-click the dock with a pocket to set it in place. Right-click with an empty hand to open the docked pocket, or sneak to take it back. Hoppers, pipes and comparators work on the dock as if it were the pocket.

## Optional mods

- **JEI:** info pages for the pockets, linings and dock. The crafting and upgrade recipes show as normal crafting recipes.
- **Jade:** looking at a dock shows which pocket sits on it.

## For pack makers

Pocket sizes, unify rules and what pockets refuse are all datapack data:

- **Sizes:** `data/<namespace>/data_maps/item/pocket_tier.json`:
  ```json
  { "values": { "hammerspace:iron_pocket": { "rows": 3, "max_per_slot": 4096 } } }
  ```
  `rows` is 1 to 6, at 9 kinds of item per row. `"infinite": true` gives the creative behaviour.
- **Unify:** `data/<namespace>/hammerspace/unify/*.json` lists the tags unify may match, `{"tags": ["c:ingots/*", "c:ores/*"]}`. A trailing `/*` matches every tag under that path.
- **Tags:**
  - `hammerspace:not_storable`: items pockets refuse. Defaults to shulker boxes, bundles and books, whose contents would make a pocket too big to send to players.
  - `hammerspace:never_unify`: items unify never touches.

Config files:

- `hammerspace-server.toml` (per world, in `serverconfig`):
  - `keepAmounts`: the keep-back amounts, default `[1, 16, 64]` as in /dank/null.
  - `fallbackRows` and `fallbackMaxPerSlot`: the size of a pocket a pack removed from the data map.
- `hammerspace-client.toml`: `showHud`.

## Credits and licence

- Hammerspace is LGPL-2.1, see `LICENSE.md`.
- The design comes from /dank/null by p455w0rd, released under the MIT licence: Copyright (c) 2018 TheRealp455w0rd. The code is a new rewrite.
- The art is recoloured from vanilla Minecraft sprites.
