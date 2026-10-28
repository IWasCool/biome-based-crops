# Biome Based Crops

A Minecraft 1.21.1 / NeoForge mod that restricts crop and sapling growth to configured biome climates. Farmer's Delight, Biomes O' Plenty, and Tough As Nails are optional.

## Climate rules

Each biome resolves to exactly one climate. The thermometer, tooltips, natural growth, and bonemeal checks use the same resolver.

1. Explicit `toughasnails:{icy,cold,neutral,warm,hot}_biomes` tags take precedence. Biomes O' Plenty supplies these tags for its own biomes even without Tough As Nails. Conflicting datapack tags resolve in the stable order icy, cold, hot, warm, neutral.
2. Without those tags, snowy/icy biomes and temperatures below 0.15 are icy; cold-tagged biomes and temperatures below 0.5 are cold. Remaining desert-tagged biomes are hot.
3. Other biomes use base temperature: neutral through 0.8, warm through 1.0, hot above 1.0. Dryness alone does not imply heat. Altitude, time of day, and seasons do not change this biome classification.

Crop rules use `token=climate,climate`. The longest matching token wins; the first entry wins ties. Bare tokens match block/item paths. Namespace-qualified tokens match full IDs, for example `biomesoplenty:hellbark_sapling=warm,hot`. These remain substring rules, so use a longer token when a broad rule would match an unrelated name.

The defaults explicitly cover all 20 sapling types in the bundled Biomes O' Plenty version. Rules for glow berries, mushrooms, sugar cane, and jungle plants include their native climates. Other crop preferences are gameplay defaults, rather than an attempt to reproduce every naturally generated plant location. Unmatched detected plants use `defaultCropClimates`.

## Growth and compatibility

Natural growth is checked before the plant's random tick. This includes sapling stages, mushroom spreading, nether wart, both cave-vine parts, vanilla crops, and supported custom plants. Bonemeal uses a separate NeoForge event, so its toggle is independent of natural growth. Plants can still be placed outside their allowed climates.

Vanilla crop/sapling classes and subclasses are recognized by behavior. Additional custom growable plants and raw produce use `supportedCropNamespaces`; namespaces containing `delight` are also recognized. Prepared foods and building blocks do not receive crop climate tooltips. Farmer's Delight rice and mushroom colonies are covered.

An active Tough As Nails thermoregulator can provide artificial climates:

| State | Supported climates |
| --- | --- |
| Cooling | Icy, cold, neutral |
| Heating | Neutral, warm, hot |
| Both | Neutral |
| Empty, inactive, or redstone disabled | None |

The crop support area is a square with configurable horizontal radius (default 4), from one block below the plant to three blocks above it. This is separate from Tough As Nails' player temperature area and does not simulate walls, facing, or an enclosed greenhouse. Only loaded chunks are inspected, and every nearby candidate is considered. Stored fuel alone does not count as an active mode.

Right-click a Tough As Nails thermometer to display biome climate and whether an active regulator is nearby. This indicator does not guarantee that its mode suits every plant.

Other mods that bypass both random ticks and the normal bonemeal API may need their own integration.

### Create New World 4.1.1 development compatibility

The pack integration targets the installed Create New World 4.1.0 gameplay versions used while developing 4.1.1: Create 6.0.10, Integrated Farming 1.4.3, Central Kitchen 2.6.2b, Slice & Dice 4.3.4, Snow! Real Magic! 12.2.3, Northstar 0.6.6, Spawn 4.0.8, Supplementaries 4.0.2 on the client / 4.0.1 on the server, Quark 4.1-487, and Core 0.0.3. See the [4.1.1 audit](create-new-world-4.1.1-audit.md) for inspected inventories, verified runtime results, and release follow-up.

- Create's tree fertilizer checks the real sapling's climate before generating its temporary tree or consuming the item. Deployers using ordinary bonemeal retain the standard guard.
- Slice & Dice fertilizer checks each sprinkler target before growth. The sprinkler retains its normal fluid use, including when a spray has no eligible crops.
- Snow-covered crops and saplings use their contained plant for fertilizer and soil-boost checks. The snow wrapper keeps its own melting behavior; natural ticks of the contained plant still use the normal growth guard.
- Northstar's unusual growing plant names, Spawn's sunflower/date plants, Supplementaries flax, and Quark saplings receive appropriate detection/default rules. Decorative blocks and prepared seeds remain excluded.
- The core mod's climate tags retain priority. In this pack, ordinary deserts and Nether biomes are warm; Scorched Earth is explicitly hot. Existing hot-only cactus, nether wart, crimson fungus, and warped fungus preferences still require a hot biome or a suitable active thermoregulator. BOP pine, dead, and hellbark saplings now also accept warm climates so their native Core-remapped biomes remain usable.
- Disabling Tough As Nails player temperature does not disable crop restrictions or thermoregulator fuel ticking. Planetary HUD temperatures, shade, oxygen, altitude, and weather are separate systems; crop climates follow the actual biome tags/base temperature.

`tools/prepare-pack-tests.ps1` creates isolated fixtures from the installed client/server gameplay sets and copies gameplay configs without copying saves. It records hashes, omitted presentation-only client mods, the test-only removal of bundled optional-integration GameTests from the isolated Central Kitchen copy, and omission of Neruina's development-only deliberate farmland crash registration. All other retained ZIP bytes are verified; original installed JARs are preserved.

For a prepared server fixture, run:

```sh
./gradlew runGameTestServer -PenableGameTests=true -Pneo_version=21.1.259 -PcompatBuildDir=build/compat-build/server -PcompatModsDir=build/compat/server/mods -PcompatGameDir=build/compat/server/run
```

Use the corresponding client fixture paths and NeoForge 21.1.256 for the client gameplay version set. These are headless gameplay checks; they do not certify every rendering, physics, network-version, or long-running performance interaction in the pack.

Core 0.0.3's installed survival guide still describes fuel-free neutral crop support. The corrected detector requires an enabled regulator with an active heating/cooling timer; inventory slots can be empty while consumed fuel is still burning. The guide text needs updating for the release.

The bundled Biomes O' Plenty 21.1.0.14 jar also emits upstream mob-spawn diagnostics: ocelots use the wrong spawn category in rainforest/rocky rainforest, and allays lack a registered spawn placement. This mod does not register those biomes or spawns; its crop fixes do not alter the dependency jar.

## Configuration and upgrading

Gameplay settings are now **SERVER** configs, synchronized to connected clients:

- Default gameplay rules: `config/biome_based_crops-server.toml`.
- Optional per-world override: `<world>/serverconfig/biome_based_crops-server.toml`.
- Local tooltip preference: `config/biome_based_crops-client.toml`.

To disable biome restrictions for crops and trees, set both gameplay options to `false`:

```toml
enableCropBiomeDependencies = false
enableSaplingBiomeDependencies = false
```

Each option works independently. `false` restores ordinary growth for that category, including natural growth, bonemeal, and supported modded fertilizers, and hides its climate requirement tooltips. Set an option back to `true` to restore its biome restrictions. The tree option uses the existing sapling category, which also includes bamboo, cactus, sugar cane, mushrooms, fungi, and azaleas.

You can also change these settings in game:

| Command | Effect |
| --- | --- |
| `/biomebasedcrops disable` | Disable biome restrictions for both crops and trees. |
| `/biomebasedcrops enable` | Enable biome restrictions for both categories. |
| `/biomebasedcrops crops disable` | Disable only crop biome restrictions. |
| `/biomebasedcrops trees disable` | Disable only tree/sapling biome restrictions. |
| `/biomebasedcrops crops enable` or `/biomebasedcrops trees enable` | Re-enable that category. |
| `/biomebasedcrops status` | Show both settings. |
| `/biomebasedcrops crops status` or `/biomebasedcrops trees status` | Show that category's setting. |

These settings affect the entire world/server. Commands that change them require operator permission level 2, single-player cheats, or the server console. Status commands are available to everyone. Changes take effect immediately, are saved to the active server config for future sessions, and update connected players' tooltips without reconnecting. Clients running an older build must reconnect to refresh their tooltips.

Earlier builds used `config/biome_based_crops-common.toml`. To retain custom settings, copy the gameplay keys into the new server config and move `enableTooltips` to the client config. To adopt the corrected biome and sapling defaults, let the new config generate instead of copying the old `cropClimateRules` list. Old configs and saved worlds are not modified automatically. See the [NeoForge configuration documentation](https://docs.neoforged.net/docs/1.21.1/misc/config/#configuration-types) for config locations and syncing.

Rules and plant detection are cached and invalidated when the server config loads, reloads, or unloads. Invalid climate names and empty climate lists are rejected during config validation. NeoForge synchronizes server configs when players join; remote players should reconnect after manually reloading other server config settings to refresh their tooltips.

## Build and verification

Use Java 21. On Windows use `gradlew.bat` in place of `./gradlew`.

```sh
./gradlew build
./gradlew runGameTestServer -PenableGameTests=true
```

Jars in `libs/` are optional local test dependencies and are not bundled into the mod. To verify operation without them:

```sh
./gradlew build -PincludeLocalMods=false
./gradlew runGameTestServer -PenableGameTests=true -PincludeLocalMods=false
```

JUnit regression tests cover exclusive climate classification, config validation, rule precedence, immutable cached profiles, plant detection, independent category settings, command permissions and persistence, thermoregulator modes, and biome resource coverage. Optional biome resource checks use the jars in `libs/`; they report skipped when those jars are absent. GameTests run actual natural ticks and bonemeal handling in a separate test world under `build/gametest/`. Test code is excluded from the release jar.

The output jar is `build/libs/biome_based_crops-0.0.1.jar`.
