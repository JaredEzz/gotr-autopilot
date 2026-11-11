# GOTR Autopilot

GOTR Autopilot aims to make Guardians of the Rift as brain-off as possible, for people who just want to get their needle, lantern and Raiments of the Eye set without having to track timings or figure out where they need to be or what they need to be doing.

## Features

- Automatically adjusts the 'route' based on the current minigame state, your stats, your position, and what you have equipped and in your inventory.
- Simple instruction panel with the current step, and timers the game does not show: game clock, next portal, projected rift close, banked points, Binding necklace charges.
- The current objective outlined, with a path drawn taking you there.
- Item highlights for what the current step uses (essence, cells, pouches, runes).
- A hint arrow and an optional notification when the step changes after a longer AFK stint.
- Tells you when it's safe to exit after a game to avoid losing points.
- Hides The Great Guardian when a cell is to be placed on a tile to avoid mis-clicks.
- Removes the Great Guardian's **Power-up** option while you carry no guardian stones.
- Pushes Craft-rune off the left-click of an elemental altar whenever a combination rune is possible
  (it stays on right-click), so a misclick cannot craft the plain rune.
- Prompts the spell a step needs — Magic Imbue for a combination rune, or NPC Contact for a pouch
  repair (outlining the Dark Mage entry in the contact list) — by highlighting the magic book tab, or
  the spell itself once the book is open. After imbuing, it points back at the inventory tab so the
  base runes can be used on the altar.
- While the plan says to drop an essence, makes **Drop** its left-click option.
- **Always prefer** / **always avoid** altar lists, so e.g. Death and Blood are taken over Mind and Body.
- Outlines the "Can you repair my pouches?" Dark Mage dialogue option, so
  the repair needs no hunting through the contact menu.
- Shows the energy still needed for the XP minimum (300) until it is met, then hides it.
- Skips a portal or an open altar you can't reach before it closes or the altars change, and gives
  the next step instead.
- Over the entrance barrier in the lobby: whether a game is in progress on this world. Optionally
  (off by default) also the Guardians of the Rift worlds with the lowest ping and the most players.

## Strategies

Selectable in the Strategy settings:

- **Mass** (default): Perfect for mass worlds, best for adapting to larger groups of players.
- **General / team**: Uses the Wiki strategy as a base for what you should be doing and when.
- **Solo**: Single-barrier strat that leaves the right-most barrier weak.

**Barrier target** (Strategy settings): **Lowest health** fixes the most damaged/upgradable barrier; **Closest** fixes the nearest barrier that needs work (not Overcharged, or at 70% health or less), preferring the side the next step heads to (west to deposit, east for cells or the remains), and recharges the closest healthy one when nothing needs work.
