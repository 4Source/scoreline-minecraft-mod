# Scoreline

**A leaderboard in your TAB list.** Scoreline adds statistic columns to the player list, so everyone on a server can see playtime, deaths or any other stat at a glance. Later versions add history graphs that compare players over the whole lifetime of a server.

## Features

**TAB list leaderboard.** Stat columns appear next to each online player's name. The vanilla sort order is kept, and the ping column stays on the far right.

**Pick your own stats.** Every vanilla statistic can be shown, including per-item, per-block and per-mob stats. Stats are chosen in a picker that looks and feels like the vanilla Statistics screen.

**Server-controlled.** The server decides how often stats refresh (default: every 2 seconds) and, per stat, whether and how it may be shown to players.

**Stats API for mod developers.** Other mods can register their own stats, which then show up alongside the vanilla ones. The server sends stat definitions to clients, with preset formats and an optional custom format when the mod is installed on the client too.

**History graphs** *(planned for 1.0)*. Every stat is recorded over time, so players can compare each other over hours, days or years, with selectable time ranges, hover details and per-player detail views. Retention is configurable per resolution, and recording backs off automatically if the server is under load.

**Privacy by default.** Out of the box, other players only see coarse history data. Server admins can switch to fully open or to opt-in.

**Built for real servers.** Designed for up to 50 players online around the clock, with stat collection and graph data kept off the main thread wherever possible.

## Compatibility

| Mod   | Minecraft | Loader   | Java |
|-------|-----------|----------|------|
| 1.0.0 | 26.3      | NeoForge | 25   |

## Installation

Scoreline will be available on CurseForge and Modrinth once the first public build (v0.3) is out. Install it on the server; installing it on clients as well enables the TAB columns, the stat picker and the settings screen.

## Documentation

Guides for players and server admins, plus versioned Stats API documentation for mod developers, will be published alongside the public launch.

## Contributing

Bug reports and feature requests are welcome in the [issue tracker](https://github.com/4Source/scoreline-minecraft-mod/issues). If you want to work on something larger, please open an issue first so we can talk about the approach.
