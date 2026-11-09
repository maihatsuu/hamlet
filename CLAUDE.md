# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

Hamlet is a Fabric mod for Minecraft 1.21.1 that overhauls villagers. `Hamlet.md` (repo root) is the design spec: read it before implementing any feature, and check its "Open questions" section, since those decisions are unmade and should be raised with the user rather than assumed.

The code in `hamlet-1.21.1/` is still the unmodified Fabric example template. `ExampleMixin`, `ExampleClientMixin`, and the description, authors and contact fields in `fabric.mod.json` are placeholders.

`Hamlet.md` also mentions Archive, Rebalanced Core, Carry On and "NOTES". These are other mods or documents from the wider modpack and are not in this repository.

## Commands

Run from `hamlet-1.21.1/`:

```sh
./gradlew build       # compile and produce jars in build/libs/
./gradlew runClient   # launch a dev client
./gradlew runServer   # launch a dev dedicated server
```

There is no linter or automated test suite configured, so no way to run a single test. Verification is manual and in-game; the "Testing" section of `Hamlet.md` lists the expected behaviours.

CI (`.github/workflows/build.yml`) runs `./gradlew build` on JDK 25. The mod itself compiles to Java 21 (`options.release = 21`).

Minecraft, Fabric Loader, Loom and Fabric API versions are set in `gradle.properties`.

## Architecture

- **Mojang mappings:** the project uses `loom.officialMojangMappings()`, so use Mojang names (`ResourceLocation`, `Villager`), not Yarn names, and take care with examples from Yarn-based sources.
- **Split source sets:** `splitEnvironmentSourceSets()` separates `src/main` (common and server) from `src/client`. Code in `main` cannot reference `client` classes. The custom trading screen belongs in `client`; villager logic, inventory and networking registration belong in `main`, with client-side handlers in `client`.
- **Client and server both required:** the custom trading screen means the mod runs on both sides, so `fabric.mod.json` uses `"environment": "*"`.
- **Entrypoints:** `maihatsuu.hamlet.Hamlet` (main) and `maihatsuu.hamlet.client.HamletClient` (client), declared in `fabric.mod.json`. Use `Hamlet.id(path)` for namespaced `ResourceLocation`s.
- **Mixins:** each mixin must be listed in its config: `src/main/resources/hamlet.mixins.json` (package `maihatsuu.hamlet.mixin`) or `src/client/resources/hamlet.client.mixins.json` (package `maihatsuu.hamlet.client.mixin`). Both set `defaultRequire: 1`, so an injector that matches nothing crashes at startup, and `requireAnnotations` means overwriting methods must be explicitly annotated with `@Overwrite`.
