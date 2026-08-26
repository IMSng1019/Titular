# Titular

Titular is a Fabric 1.20.4 visual title system for Java 17. The server owns title, group, player, permission, and display-mode state; clients receive a permission-filtered snapshot and never write configuration directly.

## Features

- Prefix, suffix, or both around the original player name.
- Literal rich-text runs with color, bold, italic, underline, and strikethrough.
- One inheritance chain per group, extra groups/titles, cycle protection, stable de-duplication.
- `/titular` selection UI plus administrator and superadministrator management panels.
- OP 2 / `titular.admin`: change your own primary group.
- OP 4 / `titular.superadmin`: manage groups, titles, settings, and any online/offline player's fields.
- Shared formatter for chat display names, entity labels, and Tab entries.
- JSON files under `config/titular/` with atomic writes and damaged-file backups.
- Optional LuckPerms integration; the mod remains usable without LuckPerms.

## Configuration files

The first server start creates empty `titles.json`, `groups.json`, `players.json`, and `settings.json` under `config/titular/`. No sample titles or groups are inserted. A malformed document is moved to a `.broken-*` backup and replaced with an empty/default document. Title text uses Titular's bounded literal styled-run JSON format; registry-backed click/hover/translation components are rejected intentionally.

## Building and testing

```text
./gradlew test
./gradlew build
```

The suite covers model defaults, JSON persistence/recovery, inheritance, permissions, service mutations, packet bounds/projections, formatting equivalence, client screen state, and the rich-text editor model.

## Permission and LuckPerms notes

Permission levels are resolved on the server from the actor's operator level and trusted LuckPerms context. Client packets contain operations and an expected snapshot revision, never a permission enum. LuckPerms is detected at runtime and loaded through an isolated optional bridge; absent or unavailable LuckPerms leaves JSON-managed groups intact.

The wire snapshot caps each definition/reference and online-display list at 512 entries. The server rejects oversized new configuration data and safely limits projections of externally supplied legacy data; this keeps a malformed or unusually large configuration from taking down the network handler.

## Development smoke checks

Run a development server and client to verify first-run empty files, `/titular`, title activation, OP2 self-group changes, OP4 offline-player editing, reload, and synchronized chat/head-name/Tab output. The first `runClient` may spend time downloading Minecraft assets.
