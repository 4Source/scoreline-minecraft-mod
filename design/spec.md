# Scoreline – Design Spec

Nov 11, 2026 · @Dennis

## Overview

Scoreline is a Minecraft mod that shows player statistics as extra columns in the TAB player list and, from v0.11, as graphs of each stat over time. It is a public mod for CurseForge and Modrinth, targeting NeoForge 26.3 and servers with up to 50 players first.

**Goals**

- Pressing TAB shows a clean, readable list of online players with stats each player chooses.
- The whole server lifetime can be explored in a graph that feels instant, for all 50 players at once.
- The server never lags because of the mod.
- Other loaders and larger servers can be supported later without redesign.

**Roadmap**

| Step | Theme | Content |
| --- | --- | --- |
| v0.1 | Skeleton | Empty mod that loads on NeoForge 26.3; the request budget as first tested logic; build workflow with unit tests, smoke test and coverage gate; release flow up to GitHub Releases |
| v0.2 | Protocol | Hello payload, request and response for playtime and deaths; game tests; stored example messages |
| v0.3 | TAB overlay | Two fixed columns in the TAB list; client startup check; first CurseForge upload (alpha); docs site with install and player page |
| v0.4 | Settings | `ClientSettings` and `ServerSettings`, YACL settings screen, refresh interval, configuration reference |
| v0.5 | Stat picker | Native stat picker, all stats selectable, four column slots |
| v0.6 | Stats API | Stat providers, vanilla stats on the same interface, format presets, API jar, developer guide and Javadoc |
| v0.7 | Snapshot | Shared snapshot, change tracking, debug command |
| v0.8 | Stat modes | A mode per stat, hide-me and opt-in, join notice, admin docs |
| v0.9 | Public launch | Fabric build, Modrinth, daily version check, project pages; beta from here on |
| v0.10 | Recording | History engine with all levels and retention; no screen yet |
| v0.11 | Graph basics | Tile protocol; graph screen with lines, ranges and overview strip |
| v0.12 | Graph interaction | Selecting players, hover box, prefetching, remembered state |
| v0.13 | History privacy | Detail limits for others, deletion, graph access, docs |
| v1.0 | Stable | Stabilization after real-server testing; API declared stable |
| Later |  | A view that includes offline players, support for larger servers |

Each step gets its own implementation plan and ends with something that can be tried out.

## Scope

v0.1 to v0.9 build the TAB leaderboard for online players in small steps; v0.10 to v0.13 add recorded history and graphs; v1.0 stabilizes.

**v0.1 to v0.9: the TAB leaderboard**

- **v0.1 Skeleton:** an empty mod that loads on NeoForge 26.3, with the build-and-test workflow and the release flow up to GitHub Releases. The request budget is built here as the first tested logic, so the tests and the coverage gate have something to measure.
- **v0.2 Protocol:** hello payload, request and response. The server answers with playtime and deaths for online players. Each side falls back to vanilla behaviour if the other lacks the mod.
- **v0.3 TAB overlay:** the TAB list shows playtime and deaths, in vanilla order. First alpha upload to CurseForge. Docs site.
- **v0.4 Settings:** settings layers, YACL settings screen, server setting for the refresh interval.
- **v0.5 Stat picker:** up to four columns per player, chosen from all statistics (general, per-item, per-block, per-mob).
- **v0.6 Stats API:** other mods can add stats; these are selectable like vanilla stats. Format presets with options. Separate API jar.
- **v0.7 Snapshot:** shared snapshot, change tracking, debug command.
- **v0.8 Stat modes:** a mode per stat, hide-me and opt-in for players, join notice.
- **v0.9 Public launch:** Fabric build, publishing to Modrinth as well, daily version check, project pages.

**v0.10 to v1.0: history and graphs**

- **v0.10 Recording:** all stats except those set to Off are recorded at five levels of detail. No screen yet, so data exists when the graphs arrive.
- **v0.11 Graph basics:** tile protocol and the graph screen: one stat at a time, one line per player, lifetime overview, time ranges.
- **v0.12 Graph interaction:** selecting players, hover details, prefetching, remembered state.
- **v0.13 History privacy:** detail limits for other players' history, deletion of history, graph access.
- **v1.0 Stable:** fixes from real-server testing; the API is declared stable.

**Out of scope for now**

- Offline players in the TAB list.
- Sorting the TAB list by a stat.
- Web dashboard or database storage.
- Forge support (YACL has no Forge build for 26.3).

## Project structure

The project uses the MultiLoader Template: almost all code lives in a shared module, and each loader gets a thin module with only the glue.

| Module | Content |
| --- | --- |
| `common` | Stat providers, payload definitions, TAB overlay (mixin), settings, history engine, graph view |
| `api` | The small public API for other mods, from v0.6. Published as its own jar for other mod developers to compile against; players install only the one Scoreline jar, which contains the API too |
| `neoforge` | Payload registration, mod start, config screen hook. Built and tested from v0.1 |
| `fabric` | Same glue for Fabric. Removed in v0.1 and re-created from the then-current template for the public launch in v0.9 |

**Rules**

- Loader modules contain no logic, only registration.
- Components that may be replaced later sit behind an interface: `ClientSettings` and `ServerSettings` (config), `HistoryStore` (storage), stat providers (data sources).
- Code that needs no Minecraft classes (encoding, levels, rate limiting, reset detection) is kept free of them so it can be unit tested.
- Code that decides what to draw is kept apart from the code that draws (see Testing).

**Dependencies**

| Dependency | Why | Notes |
| --- | --- | --- |
| YetAnotherConfigLib (YACL) 3.9.7+ | Config files and settings screen | From v0.4, required on client and server. NeoForge and Fabric builds exist for 26.3. v0.1 to v0.3 have no dependencies |
| Mod Menu (Fabric only) | Config button in the mod list | Optional, from v0.9 |

**Names and places**

| Item | Value |
| --- | --- |
| Repository | `4Source/scoreline-minecraft-mod` on GitHub |
| Mod ID | `scoreline` |
| Java package | `io.github.foursource.scoreline` (a package part cannot start with a digit, so the name is written out) |
| Group for the published API jar | `io.github.foursource` |
| Target | Minecraft 26.3, NeoForge first |
| Design documents | `design/spec.md` (this spec) and `design/plans/` (one plan per step). Once in the repository, that copy is the one that counts |
| User documentation | `docs/` (the MkDocs pages) |

## TAB overlay (v0.3, extended in v0.5 and v0.6)

The TAB list keeps its vanilla look and order and gains up to four stat columns between each player's name and the ping icon, which stays rightmost.

**v0.3**

- **Columns:** two fixed columns, playtime and deaths.
- **Implementation:** a mixin into the vanilla tab-list renderer in `common`, so it works on every loader.
- **Kept from vanilla:** player heads, names, ping icons, server header and footer, sorting.
- **Header row:** the stat name above each column, using Minecraft's translated stat names.
- **Formatting:** values look exactly as on the vanilla statistics screen.
- **Missing data:** a column shows "–" until the first response arrives.
- **Fallback:** if the server did not send a hello payload, the vanilla TAB list is shown untouched.

**v0.5**

- **Columns:** up to four, chosen by the player from all statistics.
- **Names:** a column header always shows the stat's translated name; there are no custom labels. Minecraft's stat names are fully translated, and mods using the API must supply translatable names. Names that do not fit are shortened with "…".

**v0.6**

- **API stats:** columns can show stats from other mods, formatted with their preset (see Stats API).

## Networking (v0.2, extended in v0.4 to v0.8)

The client asks for data only while TAB is held, and the server answers only that client.

1. **Hello (server → client, on join):** protocol version, refresh interval in seconds, the stat modes, and the definitions of API stats (ID, translatable name, kind, format preset with options). The table below shows which part arrives in which step.
2. **Request (client → server, on TAB press):** the stat IDs of the player's configured columns.
3. **Response (server → that client):** for each online player, the UUID and the requested values.
4. **Refresh:** while TAB stays held, the client repeats the request at the announced interval.

**Server rules**

- Unknown stat IDs and stats whose mode hides them from the requesting player are ignored.
- Each player has a request budget that refills over time.
- **Shared snapshot (from v0.7):** once per refresh interval, and only if someone requested data recently, the main thread copies the requested stats of all online players into a fixed snapshot. Requests are answered from that snapshot off the main thread, so the main thread's work does not grow with the number of players holding TAB.
- Until v0.7, values are read directly on the main thread. Nothing ever touches the disk.

**Compatibility**

- No hello received means the server lacks the mod: the client sends nothing.
- A vanilla client on a modded server never sends requests, so nothing breaks.
- Payloads are vanilla `CustomPacketPayload` classes with `StreamCodec`, defined once in `common`. Only their registration differs per loader.

**By version**

| Step | Hello payload carries | Request carries |
| --- | --- | --- |
| v0.2 | Protocol version, refresh interval (fixed at 2 seconds) | The stat IDs wanted; the client always sends playtime and deaths |
| v0.4 | Refresh interval from the server config | Unchanged |
| v0.5 | Unchanged | The player's chosen stat IDs |
| v0.6 | Definitions of API stats | Unchanged |
| v0.7 | Unchanged | Unchanged; answers come from the shared snapshot |
| v0.8 | Stat modes, so the client offers only visible stats | Unchanged |

The request format is the same from v0.2 on, and the hello payload starts with a protocol version, so later versions extend the protocol without breaking older clients.

## Configuration (v0.4, extended in later steps)

YACL stores the config files and draws the settings screen; all other code reaches the settings only through Scoreline's own `ClientSettings` or `ServerSettings` classes.

**Client settings**

| Setting | Since | Default | Notes |
| --- | --- | --- | --- |
| Show stat columns | v0.4 | On | Switches the extra TAB columns on or off for this player |
| Column slots 1 to 4 | v0.5 | Playtime, deaths, none, none | Each slot is a button that opens the stat picker (described under Graph screen); only stats the server allows are offered |
| Visibility (hide me, opt-in) | v0.8 | Visible | Chosen by the player, stored on the server |
| Tile cache size | v0.11 | 128 MB | 32 to 512 MB |
| Remembered graph state | v0.12 | none | Last stat, range and selected players per server; not shown in the settings screen |

Saved to `config/scoreline-client.json`.

**Server settings**

| Setting | Since | Default | Notes |
| --- | --- | --- | --- |
| Refresh interval | v0.4 | 2 seconds | How often clients may refresh while TAB is held |
| Default stat mode | v0.8 | Coarse for others | Applies to every stat without an override |
| Mode overrides | v0.8 | none | A mode for a single stat or a whole category, for example everything under "picked up" |
| History retention per level | v0.10 | 48 hours, 90 days, forever | Minutes, hours, days; ranges under History recording |
| Graph access | v0.13 | Everyone | Everyone, operators only, or nobody |

Saved to `config/scoreline-server.json`.

v0.1 to v0.3 have no settings: the columns and the refresh interval are fixed. The stat modes below arrive in v0.8.

| Stat mode | Behaviour |
| --- | --- |
| Off | Not sent; from v0.10 not recorded |
| Private | Each player sees only their own values |
| Opt-in | Visible to others only for players who switched it on |
| Coarse for others | Visible; in graphs others get at most hourly detail. Same as Open in the TAB list |
| Open | Fully visible |

**The settings layers**

- Holds every client setting: the column slots, the tile cache size, and what the graph screen remembers per server.
- Offers getters, setters and a change listener for each of them.
- The YACL screen, the TAB overlay, the graph screen and any future view use only this class.
- A change to the columns triggers a fresh request, so TAB shows new columns immediately.
- If YACL is ever replaced, only this class and the screen code change.
- The server side has the same kind of layer, `ServerSettings`, for the refresh interval, stat modes and retention.

Editing the JSON file by hand while the game runs is not picked up until restart.

## Stats API (v0.6)

Other mods can add their own stats, which then appear in the stat picker, the TAB columns and later the graphs like any vanilla stat.

Stats that a mod registers in Minecraft's normal statistics system appear automatically. The API is for values kept elsewhere, such as "quests completed" or a money balance.

**A stat definition**

| Field | Meaning |
| --- | --- |
| ID | For example `questmod:quests_completed` |
| Name, description | Translatable text |
| Kind | Counter (only rises) or gauge (goes up and down) |
| Format | A preset plus options, see below |
| Value function | Returns the stat's value for an online player, called on the server |

**Formats**

- Presets: plain number, time, distance, number with decimals.
- Options: unit text before or after the number (translatable), decimal places, time unit (ticks, seconds, milliseconds).
- If the providing mod is also installed on the client, it may register its own formatting code there. The preset remains the fallback for everyone else.

**Rules**

- Values are whole numbers (`long`). Decimal values are stored scaled.
- The server sends all API stat definitions in the hello payload, so server-only mods can add stats.
- Vanilla statistics are wrapped by a built-in provider and use the same interface.
- Scoreline asks providers for values; providers do not push changes.
- A provider that throws an error is switched off with a log warning naming the mod. Slow providers are measured and reported.
- Registration uses Java's `ServiceLoader`; a plain `register(...)` method is the fallback if that proves unreliable across loaders.
- The API is marked experimental from v0.6 until v1.0 and follows semantic versioning after that.

## History recording and storage (v0.10)

The server records every stat's value over time at five levels of detail, so a graph of any time range needs only a few hundred points per player.

**The problem**

- Minecraft stores only running totals ("played 4,312,800 ticks"), not when anything happened. History must be recorded by the mod and cannot be rebuilt afterwards.
- Data grows with players × recorded stats × snapshots. All estimates assume the worst case: all 50 players always online and active, for years.
- "All stats" means about 75 general stats plus one stat per item, block or mob in each category (mined, crafted, used, broken, picked up, dropped, killed, killed by): over 10,000 in vanilla, tens of thousands in a large modpack.
- Storing full values hourly for only the general stats would already be about 280 MB per year (50 × 80 × 24 × 365 values of 8 bytes).
- The graph must show the whole server lifetime for all players at once and still feel instant. A graph about 500 pixels wide can show about 500 points per line, however much data exists.

**Design ideas and where they come from**

| Idea | Source | How Scoreline uses it |
| --- | --- | --- |
| Compress each value using the values before it | [Gorilla](http://www.vldb.org/pvldb/vol8/p1816-teller.pdf) reports a 10x smaller footprint from delta-of-delta timestamps and XOR'd values | Stats are whole numbers that only count up, the easiest case: store the difference to the previous value. Regular snapshot times cost almost nothing |
| Recent data matters more than old data; aggregates matter more than single points | Gorilla | Fine levels have a limited retention; coarse levels are kept |
| Send and store only what the pixels can show | [M4](http://www.vldb.org/pvldb/vol7/p797-jugel.pdf): per pixel column keep minimum, maximum, first and last value; up to 100x less data with an identical line chart | Levels of detail. For counters, first = minimum and last = maximum, so one value per time span is enough. Gauges need minimum, maximum and last |
| Several resolutions of the same data, each with its own retention | RRDtool | Minutes, hours, days, weeks, months, written side by side |
| Store names once, values by column, and only append | *Designing Data-Intensive Applications* (Kleppmann) | Stat ID dictionary, files organized by stat, append-only log |
| Buffer writes, append sequentially, restructure in the background | [LSM-tree](https://link.springer.com/article/10.1007/s002360050048) | Memory buffer, log file, background packing into tiles |

**Levels and retention**

| Level | Default retention | Allowed range |
| --- | --- | --- |
| Minutes | 48 hours | 0 (off) to 720 hours (30 days) |
| Hours | 90 days | Up to 1,825 days (5 years); never shorter than the minutes retention |
| Days | Forever | Optional limit |
| Weeks | Forever | Fixed |
| Months | Forever | Fixed |

**How the levels are filled: all at once, never recomputed**

1. Each minute, a snapshot delivers the stats that changed for the online players.
2. That one snapshot updates all five levels at the same moment: it is appended to the minute level, and it updates the value of the current, still open hour, day, week and month for each changed stat.
3. When a period ends, its entry is closed and written in final form. It is never touched again.

What follows from this:

- No level is ever calculated from another one. There is no job that turns minutes into hours or hours into days.
- Expired minute or hour data is removed by deleting whole files. The coarser levels already hold that period, so nothing is lost and nothing is rewritten.
- The levels are independent. With the minute level switched off, the other four fill exactly the same way.
- If minute snapshots are skipped under overload, the next snapshot carries the full totals, so the hour, day, week and month values stay correct.
- Lowering a retention deletes older data for good; the config comment says so.

**What is recorded**

- All stats except those whose mode is Off (single stats or whole categories).
- Only changes: a stat that did not change since the last snapshot is not written.
- Snapshots every minute for online players, plus a final one at logout.

**Encoding**

- Values are stored as the difference to the previous value, in a variable-length number format. Playtime rising by the same amount each hour becomes the same small number repeated.
- A stat that did not change costs nothing. In one hour a player changes a few dozen to a few hundred stats, in one minute about 20 to 50.
- Each stored change takes about 2 to 3 bytes instead of 8 or more.
- Stat IDs such as `minecraft:mined/minecraft:diamond_ore` are stored once in a dictionary and referenced by a small number.
- Counters store one value per time span. Gauges store minimum, maximum and last value.
- This is Scoreline's own compact binary format. It replaces the earlier choice between JSON and compressed NBT, which cannot express these encodings.

**File layout**

- One folder per player (by UUID), files split by level and period, organized by stat inside. A graph reads only the players, periods and stat it shows, and deleting a player's history means deleting a folder.
- New snapshots are appended to a log file. Finished periods are packed into their final tile format once and never changed again.
- All storage sits behind the `HistoryStore` interface, so the layout can change later.

| Layout considered | Decision | Reason |
| --- | --- | --- |
| One file per month for all players | Rejected | A graph for 3 players would read everyone's data; slow on large servers |
| One folder per player, files per level and period | Chosen | Reads only what a graph needs; scales; easy deletion |
| A database such as SQLite | Not now | Needs native libraries, which complicates multi-loader builds. Possible later behind `HistoryStore` |

**Size estimate (worst case: 50 players always online and active)**

| Level | At default retention | At maximum retention | Basis |
| --- | --- | --- | --- |
| Minutes | 9 to 22 MB, constant (48 hours) | 135 to 330 MB (30 days) | 50 players × 2,880 minutes × 20 to 50 changes × about 3 bytes |
| Hours | 25 to 100 MB, constant (90 days) | 0.5 to 2 GB (5 years) | 100 to 400 MB per year: 50 players × 8,760 hours × a few dozen to a few hundred changes |
| Days | 30 to 100 MB per year | Same, or bounded by an optional limit | A very active player changes many hundreds of stats per day |
| Weeks and months | Up to about 20 MB per year | Always kept | Small enough to make the lifetime view possible |

With default settings, total storage stays in the low hundreds of MB after years. On larger servers the cost grows linearly with players: the 48-hour minute window for 1,000 always-online players would be about 200 to 450 MB.

These figures are rough estimates and should be checked against real data during development.

## Graph data protocol (v0.11, completed in v0.12)

Graph data travels as fixed tiles that both sides cache, so panning and zooming within loaded data needs no network at all.

**The problem**

- A graph response is far larger than a TAB response, and Minecraft limits the size of a single payload.
- The view must cover the whole server lifetime for all 50 players, allow selecting players for detail, and react instantly to zooming, panning and range changes.

**Design ideas and where they come from**

| Idea | Source | How Scoreline uses it |
| --- | --- | --- |
| Half a second of extra delay already reduces how much people explore; panning is more sensitive to delay than zooming | [Liu and Heer, 2014](https://idl.uw.edu/papers/latency) | Target well under 500 ms; sideways neighbours are prefetched before finer levels |
| Cut data into fixed tiles per zoom level and fetch tiles ahead of the user, predicted from recent movement | [ForeCache](http://dx.doi.org/10.1145/2882903.2882919), which measured large latency gains over systems without prefetching | Fixed tiles, client cache, simple prefetch rules instead of a full prediction engine |
| Overview first, zoom and filter, then details on demand | Shneiderman's visualization rule | The lifetime tile loads first; fine tiles only for selected players |
| A response never needs more points than the graph has pixels | [M4](http://www.vldb.org/pvldb/vol7/p797-jugel.pdf) | The level is chosen so a view holds a few hundred points per line |

| Request style considered | Decision | Reason |
| --- | --- | --- |
| "Give me exactly this range", reduced on the server to about 500 points | Rejected | Smallest response, but every zoom or pan step is a new, unique request; nothing can be reused |
| Fixed tiles | Chosen | Cacheable on both sides, matches the files on disk, hover needs no request |

**Tiles**

| Level | One tile covers |
| --- | --- |
| Minutes | One day |
| Hours | One month |
| Days | One year |
| Weeks, months | The whole server lifetime |

- A request names one stat, one or more tiles, and optionally a list of players.
- A tile for a finished period never changes and is cached for the session.
- Largest expected responses: about 150 KB (a minute tile for 50 players), about 50 KB (lifetime overview).

**Making it feel instant** (target: well under 500 ms for every interaction)

1. **Overview first:** opening the screen sends one request for the lifetime tile together with the tiles of the remembered range, at the level that range needs and for the remembered selected players. The overview strip and the graph both show full detail from the first response. On a first visit with nothing remembered, the default range (30 days) and the viewing player are used.
2. **Coarse, then sharp:** on zoom, the client stretches the data it has and swaps in the finer tile when it arrives.
3. **Fine detail for selected players only:** the overview loads coarse tiles for everyone; hour and minute tiles are requested only for selected players.
4. **Prefetching:** panning loads the next tile in that direction; resting on an area loads the next finer level. Sideways neighbours come first.
5. **Live edge:** for the current, unfinished tile, the client says which point it has and receives only newer points.
6. **Request order:** visible tiles first, then prefetching. Responses for views already left are cached but trigger no redraw.
7. **Debounce:** while dragging, the client waits for a pause of 150 ms before requesting. This is an internal value, tuned during testing.

v0.11 delivers the tiles, the client cache, overview first, coarse-then-sharp and the live edge. v0.12 adds fine detail for selected players, prefetching and the remembered state.

**Limits**

- The server gives each player a request budget that refills over time, so opening the screen is not blocked.
- The client cache has a memory cap of 128 MB by default (adjustable from 32 to 512 MB) and drops the least recently used tiles first. Tiles on screen are never dropped, and the cache is cleared when the player leaves the server. A heavy session needs about 30 MB.
- Hover details need no request; all values are already on the client.

## Graph screen (v0.11 and v0.12)

The graph takes about three quarters of the screen width and nearly all of its height; every other element is kept small around it. The screen uses vanilla-style controls only.

**Layout**

&#91;embedded content: graph screen · rough layout sketch\]

The sketch shows proportions and placement only; the final look follows vanilla Minecraft screens.

| Element | Place | Behaviour |
| --- | --- | --- |
| Stat button | Top row, left | Shows the current stat; opens the stat picker |
| Range buttons | Top row, aligned to the right edge | 12h, 24h, 48h, 7d, 30d, 1y, All, Custom |
| Graph | Centre | One line per player; scroll zooms at the mouse position, drag pans; the value axis uses the stat's format and rescales to what is visible |
| Overview strip | Slim band under the graph | Whole server lifetime; a draggable, resizable window marks the visible range |
| Player list | Narrow panel on the right, collapsible | All players sorted by current value, with a search field |

**Ranges**

- Any manual change (overview strip, zoom, pan) switches to Custom and stores that range.
- Clicking a preset and then Custom restores the stored range.

**Lines**

- All players start as thin grey lines.
- Selected players get a colour and a thicker line, up to about eight at once. The viewing player is selected by default.
- Fine detail (hours, minutes) is loaded only for selected players.

**Selecting players**

- Click a name in the player list to select or deselect; selected players move to the top.
- Click in the graph to select the nearest line; if several are close, a small list of those players appears.
- "Top 5" selects the current leaders; "Clear" deselects everyone except the viewing player.
- At the limit, a hint asks to deselect someone first; nobody is replaced silently.

**Hover**

- A vertical marker follows the mouse. The hover box shows the time span and, for each selected player, the total and the gain within that span.
- For grey lines, the box lists all players whose lines are near the mouse, closest first, and highlights them together ("+12 more" when there are many).
- Hovering a name in the player list highlights exactly that player's line.

**Remembered per server (client side)**

- Last stat, last range including the custom one, and the selected players.

**Opening**

- A key binding (changeable in the controls menu) and the `/scoreline` command.

**Stat picker (built in v0.5, shared with the TAB settings)**

- A separate screen in the style of the vanilla statistics screen: tabs for General, Items and blocks, Mobs, and Other mods (API stats).
- A search box above a scrolling list; items are shown with their icons.
- Recently used stats appear at the top.
- Clicking an entry returns to the previous screen with that stat.

## Server performance (v0.10, change tracking from v0.7)

The game's main thread only copies changed numbers once a minute; all other history work runs on one background thread.

**The problem**

- A Minecraft server runs the whole game on one main thread, 20 ticks per second, which leaves 50 ms per tick for everything. Slow work there is felt by all players as lag.
- Recording touches every online player every minute, and graph requests read from disk.

**Design ideas and where they come from**

| Idea | Source | How Scoreline uses it |
| --- | --- | --- |
| Batch writes, make them sequential, restructure in the background | [LSM-tree](https://link.springer.com/article/10.1007/s002360050048) | Buffer, append-only log, background packing |
| Stay available and accept losing a little data on the write path | [Gorilla](http://www.vldb.org/pvldb/vol8/p1816-teller.pdf) | Under overload, minute snapshots are skipped instead of making the server wait |
| Give data to exactly one thread so no locks are needed | *Java Concurrency in Practice* (Goetz), thread confinement | One history thread owns all history structures and files |
| Record what changed when it changes, instead of comparing everything | Minecraft does the same to decide which stats to send to a client | Change-tracking hook, built in v0.7 |

- **Change tracking:** the mod notes each stat change as it happens. A snapshot touches only changed stats, so its cost depends on activity, not on the number of stats.
- **One history thread:** it alone encodes, updates the levels, and reads and writes files. No locks are needed.
- **Write path:** memory buffer, then append to the log file, then background packing into tiles.
- **Overload:** the hand-over queue has a fixed size. When it is full, minute snapshots are skipped and a warning is logged. The server never waits for the disk. Because stats are running totals, a skipped minute loses only timing detail.
- **Graph requests:** handled off the main thread. Finished tiles are cached in encoded form.
- **Logout and shutdown:** final values are copied before a player object disappears; on server stop the buffer is flushed and the log closed.
- **Measuring:** snapshot and write durations are logged, and a debug command shows queue size, cache size and timings. The spark profiler is used during development; the target is that Scoreline is invisible in tick time.
- **Larger servers later:** snapshot work can be spread over several ticks.

## Privacy (v0.8 for the TAB list, v0.13 for history)

By default every player is visible in graphs, but other players see at most hourly detail; minute detail is shown only for a player's own data.

**The problem**

- In vanilla, a player's statistics are visible only to that player. The TAB columns already show them to everyone online.
- History adds when someone plays. Minute-level data over months shows a person's daily routine, including offline players.
- The mod exists for comparison, so hiding everything is not the answer either.

**Design ideas and where they come from**

| Principle | Source | How Scoreline uses it |
| --- | --- | --- |
| Privacy as the default: people are protected without taking action | [Privacy by Design](https://www.ietf.org/slides/slides-privacyws-privacy-by-design-the-seven-foundational-principles-00.pdf) (Cavoukian), principle 2 | The most revealing detail (minutes) is private by default |
| Positive-sum: serve the legitimate purpose and privacy together | Principle 4 | Comparison works out of the box at coarser detail |
| Visibility and transparency | Principle 6 | Join notice; players can see and change what others see |

| Default considered | Decision | Reason |
| --- | --- | --- |
| Open: everyone visible in full detail | Available to admins | Best for friend groups, least protective |
| Opt-in: nobody visible until they switch it on | Available to admins | Most protective, but graphs look empty on a fresh install |
| Open, coarse for others | Shipped default | Comparison works immediately; minute detail stays private |

**Stat modes**

The server sets one default mode and can override it per stat or category; the five modes are listed under Configuration. Stat modes, hide-me, opt-in and the join notice ship in v0.8, before the public launch, because the TAB columns already show other players' stats. The limits on history detail, deletion and graph access follow in v0.13.

**Admin controls**

- Set any stat or category to Off, or switch history off entirely.
- Set the finest detail others may see.
- Decide who may open graphs: everyone, operators only, or nobody.
- Delete any player's history.

**Player controls**

- Hide me: the player's data disappears from other players' graphs and columns; their own view keeps working.
- Delete my history.

**Other rules**

- A one-time chat notice on join says that statistics are shown to other players (from v0.10 also: recorded over time) and names the command for the controls.
- All privacy rules are enforced on the server before data is sent.
- Data stays on the server. Clients keep tiles in memory for the session only.

Admins of public servers with EU players may have data-protection duties. The delete command and the join notice give them tools for that; this is not legal advice.

## Data correctness (v0.10)

Each situation that could make the history wrong or misleading has a fixed rule.

The reset rule follows [Prometheus](https://prometheus.io/docs/prometheus/latest/querying/functions/), the most widely used monitoring system: any decrease of a counter is treated as a reset, and the value after it counts as new increase. Prometheus is equally strict that a value which can decrease must not be a counter, which is why every stat declares its kind.

Two ways to draw the line after a reset were considered. Showing the true total, which drops at the reset, is the standard behaviour because it matches the TAB column and the vanilla statistics screen. Showing accumulated activity, which never drops, could be offered later as a display option, since raw values and reset markers are both stored.

| Situation | Rule |
| --- | --- |
| A counter's value goes down (stat reset, restored backup) | Store the raw value plus a reset marker. The graph line shows the true total and drops, with a marker. Gains are never negative |
| A stat that legitimately goes up and down | It must be declared a gauge. No reset detection; tiles store minimum, maximum and last value |
| First snapshot of a player or stat | Marked as baseline ("history starts here"); no gain is calculated for it |
| Server clock jumps backwards | No snapshot is written until the clock has passed the last one; a warning is logged |
| Time zones, daylight saving time | Timestamps are stored in UTC and shown in the player's local time |
| Server downtime | No snapshots; the line stays flat |
| Player renames | History is keyed by UUID; names are looked up for display. On offline-mode servers a rename creates a new player (documented) |
| Crash while writing the log | Each entry has a length and checksum; a broken last entry is skipped at startup |
| Crash while packing a tile | Tiles are written to a temporary file and renamed; the log is replayed at startup |
| File format changes | Every file starts with a format version |
| A mod with stats is removed | Its data stays in the files and is not offered while unregistered |
| A stat ID is renamed by an update | Old and new ID appear as separate stats; no automatic merging |

## Testing

Everything that decides something is tested by machine; only the thin layer that draws on screen is checked by hand.

**Splitting drawing from deciding**

- All layout and interaction decisions are computed in plain code that returns what to draw and where: column widths, shortened names, which lines are highlighted, which tiles to request, what the hover box lists.
- That code is unit tested. The layer that actually draws only executes those results and goes on the manual checklist.
- Two alternatives were rejected: testing logic only (layout bugs are found late), and starting a real client in the build to compare screenshots (slow, fails for unrelated reasons, tool support differs between loaders).

**Kinds of tests**

| Kind | What it covers | How |
| --- | --- | --- |
| Unit tests | Encoding, levels, retention, reset and baseline rules, request budget, format presets, config validation, layout decisions | JUnit in `common`, no Minecraft needed |
| Property tests | Rules that must always hold, for example "decoding what was encoded returns the same values" | Thousands of random inputs per rule |
| Simulated time | Levels, retention and file sizes over a long period, checked against the estimates | A fake clock plays a year of server life in seconds |
| Protocol compatibility | Today's code still reads the messages of every earlier protocol version | Stored example messages per version (golden files) |
| Crash safety | Startup recovers everything before a broken log entry | The log file is cut off at every possible position |
| Performance | Snapshot cost, tile serving, encoding speed stay within limits | Timed tests with thresholds |
| Game tests, server side | The mod loads, hello is sent, a request gets the right answer, hidden stats are not sent, the request budget holds | Minecraft's game-test framework on a headless server with mock players |
| Smoke test | A dedicated server starts and stops cleanly with the mod | Build workflow |
| Manual checklist | The drawing layer: TAB overlay, settings screen, stat picker, graph screen | Before each release, documented in the repository |
| Real server | Behaviour with real players over days | The alpha builds on the modpack server, with the debug command for timings |

The individual test cases, including boundaries, invalid input and failure cases, are designed per task when each version's implementation plan is written.

**Gates**

- Every test must pass before a change is merged.
- Coverage gate on the logic code: at least 95% of lines and 90% of branches. Logic code is everything that decides something: encoding, levels, protocol, settings, layout decisions and the API.
- The drawing layer and the loader glue are excluded from the gate, because by design they hold no logic.
- Timing tests use generous limits, several times the expected value, because build machines vary by about 20% between runs. They catch real regressions; precise numbers come from the real server.
- Slow tests (simulated year, exhaustive crash tests) run in the release workflow and nightly, not on every push.

**New Minecraft versions: what the tests can and cannot tell**

| What changed in Minecraft | Caught by | Signal |
| --- | --- | --- |
| Renamed or changed game code that Scoreline calls | The build on the new version | The version-check pull request fails with the compile error |
| Code that a hook (mixin) attaches to | Smoke test and client startup check | The game stops at startup, because all hooks are configured to fail hard instead of being skipped |
| Behaviour on the server | Game tests, including tests that pin down the vanilla behaviour Scoreline relies on, for example "a mock player's death raises the deaths stat by one and the change hook sees it" | A failing game test |
| The look or layout of the TAB list or screens | Nothing automatic | The manual checklist, a few minutes in a test world |

- The unit tests in `common` do not touch Minecraft and say nothing about compatibility.
- The client startup check starts a real client once, just far enough to confirm that all client hooks attach, then quits. Nothing is compared.
- Each mod file declares the Minecraft line it supports, for example 26.3.x. Patch versions need nothing; a new line such as 26.4 always needs a new release.
- Green pull request: run the visual checklist and press release, no code changes. Red pull request: the log shows what broke.

## GitHub setup: tests, releases, version checks

Three GitHub Actions workflows cover the routine work: every change is built and tested, a release is started by hand with a version bump, and a daily job reports new Minecraft versions. The release flow follows the one used in the apihistory project.

**Workflows**

| Workflow | Runs when | What it does |
| --- | --- | --- |
| Build and test | Every push and pull request | Builds all loader modules, runs unit tests and game tests, starts a headless server and a client once with the mod, checks coverage, uploads the jars |
| Release | Started by hand, with a dropdown: patch, minor or major | Prepares and merges a release pull request, tags it, builds once, drafts the GitHub Release; store uploads wait for approval |
| Version check | Daily, plus a manual button | Compares the newest Minecraft release with the supported one and checks whether NeoForge, Fabric and YACL have builds for it |

By step: build and test and the release flow up to GitHub Releases exist from v0.1; game tests from v0.2; the client startup check and the CurseForge upload from v0.3; the Modrinth upload and the version check from v0.9.

**Automated tests**

The kinds of tests are described under Testing.

The build-and-test workflow runs them on every change, so a failing test blocks the merge.

**Release details**

1. **Version:** next version = last `v*` tag plus the chosen bump. Without any tag, the version in `gradle.properties` is used as is (0.1.0).
2. **Prepare:** with the GitHub App token, create `release/vX.Y.Z`, update the version in `gradle.properties`, run git-cliff so the unreleased changelog section becomes this version's, commit `chore(release): vX.Y.Z`, open the pull request and enable auto-merge.
3. **Tag:** tag the merge commit `vX.Y.Z` and verify that it equals the version in `gradle.properties` on that commit.
4. **Build:** build the jars once, after the full test suite has passed.
5. **Draft:** create a draft GitHub Release with this version's `CHANGELOG.md` section as its body and the jars attached.
6. **Publish:** the upload to CurseForge (from v0.3) and to Modrinth (from v0.9) waits for the maintainer's approval.

- The version lives in `gradle.properties`; the build writes it into the mod metadata of each loader.
- A dry-run option shows the next version and changelog without creating anything.
- v0.1 to v0.8 are published as alpha, v0.9 to v0.13 as beta, and 1.0 onwards as release.
- The changelog is generated from commit messages, so commits follow the conventional format (`feat:`, `fix:`, `chore:`).
- Store uploads use an existing action for mod uploads (for example `mc-publish`); its support for 26.x must be checked first. API tokens are stored as repository secrets.
- Jar names follow `scoreline-<mod version>+<Minecraft version>-<loader>.jar`.
- The `master` branch is protected: changes arrive by pull request, and the build workflow must pass.

**New Minecraft versions**

1. The version check finds a new Minecraft release and opens an issue listing which dependencies are ready.
2. Once NeoForge and YACL have builds, the workflow creates a branch with updated version numbers and runs the build.
3. A green build becomes a pull request ready for review; a failed build is linked in the issue with its log.

- `master` always targets the newest supported Minecraft version. Older versions get a maintenance branch only if needed.
- Bot pull requests (release, version check) are opened by the GitHub App.
- Workflow actions are referenced by major-version tag and updated by hand; no Dependabot.

## Documentation

The documentation is an MkDocs site with the Material theme on GitHub Pages, with one docs version per minor release plus `dev` (managed by `mike`), following the apihistory setup in a lighter form.

Until the public launch in v0.9, only the latest docs are published. Kept versions, one per minor release, start with v0.9.

**Structure, by reader**

| Section | Content |
| --- | --- |
| Players | Installing, choosing TAB columns, the settings screen, hiding yourself, later the graph screen |
| Server admins | Installing; a configuration reference with every setting (what it does, allowed values, default, since which version, effect of changing it); stat modes with examples; commands; later storage sizing and retention |
| Mod developers | Getting started (add the dependency, write a provider, register it, see it in the TAB list); concepts (counter and gauge, format presets); a complete example mod; compatibility policy; API reference |
| Release information | Changelog; supported Minecraft and loader versions |

**Rules**

- The API reference is Javadoc generated by Gradle from the `api` module, published next to each docs version and linked from the developer pages.
- The configuration reference is generated from the setting definitions in code, which also feed the settings screen. The build fails if the generated pages are out of date.
- The release workflow deploys the docs for the released version.
- The docs for a version are part of that version being done.

**By version**

| Step | Docs added |
| --- | --- |
| v0.3 | Site skeleton, install page, player page |
| v0.4 | Configuration reference |
| v0.5 | Choosing columns with the stat picker |
| v0.6 | Developer guide and Javadoc |
| v0.8 | Stat modes and commands |
| v0.9 | Polish pass for the public launch |
| v0.10 | Storage and retention |
| v0.11, v0.12 | Graph screen guide |
| v0.13 | Privacy for history |

## Publishing and visibility

The project title carries the search words, and the mod ID stays short.

| Item | Value |
| --- | --- |
| Brand name | Scoreline |
| Project title (until graphs exist) | Scoreline – Player Stats in the Tab List |
| Project title (from v0.11) | Scoreline – Player Stats, Leaderboard & Graphs |
| Mod ID and URL slug | `scoreline` |
| Summary | Shows playtime, deaths and any other statistic for every online player right in the TAB list. Configurable columns, server-friendly, with an API for other mods. |

Licence: MIT for the mod and the API jar.

The CurseForge project is created during v0.3, so the mod can be used in a CurseForge modpack and tested on a real server early. New projects and files pass CurseForge's review before they appear, so a few days should be allowed for the first upload. Until v0.9 the project page states clearly that the mod is in early testing.

- Publish on CurseForge and Modrinth with the same name and summary.
- Choose fitting categories (utility, server utility, multiplayer or social).
- The first gallery image shows the TAB list with stat columns.
- Set loader and Minecraft version tags correctly on every file.
- Update quickly after new Minecraft releases.

The name was checked on both sites on 11 November 2026 and was free.

## Open points

All design questions are decided. These points can only be answered while building.

**To verify technically**

- [ ] Whether the MultiLoader Template and NeoForge's game-test framework are ready for 26.3 (v0.1).
- [ ] Minecraft's size limit for a single payload, against the 150 KB tile estimate (v0.2).
- [ ] Tooling for the client startup check on NeoForge and Fabric (v0.3).
- [ ] Whether the publishing action supports 26.x versions, and how long CurseForge's review takes (v0.3).
- [ ] YACL 3.9.7 class and method names, and how YACL is referenced from the `common` module (v0.4).
- [ ] YACL button options that open the stat picker screen (v0.5).
- [ ] `ServiceLoader` registration across separate mods on NeoForge and Fabric, with a small test mod (v0.6).
- [ ] The hook for stat changes in 26.3 and its mapping names (v0.7).
- [ ] Storage estimates against real data (v0.10).

## References

The design borrows from published work on time-series storage and interactive visualization.

| Source | Used for |
| --- | --- |
| [Gorilla: A Fast, Scalable, In-Memory Time Series Database](http://www.vldb.org/pvldb/vol8/p1816-teller.pdf) (Pelkonen et al., VLDB 2015) | Difference encoding; preferring availability over small data loss |
| [M4: A Visualization-Oriented Time Series Data Aggregation](http://www.vldb.org/pvldb/vol7/p797-jugel.pdf) (Jugel et al., VLDB 2014) | Reducing data to what the graph's pixels can show; minimum, maximum, first, last per span |
| [The Log-Structured Merge-Tree](https://link.springer.com/article/10.1007/s002360050048) (O'Neil et al., 1996) | Buffer, append, pack in the background |
| [Dynamic Prefetching of Data Tiles for Interactive Visualization](http://dx.doi.org/10.1145/2882903.2882919) (Battle et al., SIGMOD 2016) | Tiles and prefetching |
| [The Effects of Interactive Latency on Exploratory Visual Analysis](https://idl.uw.edu/papers/latency) (Liu and Heer, 2014) | The 500 ms target; panning before zooming |
| [Privacy by Design: The 7 Foundational Principles](https://www.ietf.org/slides/slides-privacyws-privacy-by-design-the-seven-foundational-principles-00.pdf) (Cavoukian) | Privacy defaults and transparency |
| [Prometheus query functions](https://prometheus.io/docs/prometheus/latest/querying/functions/) | Counter reset handling; counter and gauge |
| *Designing Data-Intensive Applications* (Kleppmann) | Dictionary encoding, column layout, append-only logs |
| *Java Concurrency in Practice* (Goetz) | One owner thread for the history data |
| RRDtool | Several resolutions of the same data with retention |
