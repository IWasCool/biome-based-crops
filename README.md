# Biome Based Crops

Configurable biome-based growth for crops, trees, and other plants.

Version **1.0.0** (in development) for **Minecraft 1.21.1**, **NeoForge 21.1.251+**, and **Java 21**.

[Development builds](https://github.com/IWasCool/biome-based-crops/actions/workflows/build.yml) | [Source](https://github.com/IWasCool/biome-based-crops) | [Issues](https://github.com/IWasCool/biome-based-crops/issues)

## Installation

Place `biome_based_crops-1.0.0.jar` in the client and server `mods` folders. No other mods are required.

## Features

- Find every mod block and item in the dedicated **Biomes Notebook** creative tab after the vanilla tabs. Use the creative inventory's page arrows if the tab is on another page.
- Assign crops and saplings to climates, specific biomes, or biome tags.
- Build greenhouses that identify their biome from required native materials, matching climate, and breathable air, with an installed Electrical or Tough As Nails thermoregulator to mark intentional registration.
- Choose Icy, Cold, Neutral, Warm or Hot with an FE-powered Electrical Thermoregulator. Each AC supports 256 enclosed interior blocks when changing climate; registering a naturally matching greenhouse biome still needs an installed thermoregulator, without fuel or FE use for registration.
- Build a native biome floor and match habitat percentages that scale with room size.
- Distinguish greenhouse biomes with characteristic flowers, trees, and rocks; mixed completed layouts never grant multiple biome identities.
- Apply the detected biome inside the room, including its natural mob spawn lists, while the surrounding world keeps its native biome.
- Grow greenhouse plants under sky-exposed glass.
- Grow climate-based crops in the four-block circular front core of an active Electrical or Tough As Nails thermoregulator, with a gradual transition to the native climate outside it, without a greenhouse or glass.
- On Sable ships, crop biome requirements follow the ship's actual world location; local thermoregulators move with the crops.
- Open doors and holes release subtle air wisps; reseal the room before its climate drifts back toward the natural climate.
- Read local conditions with a Thermometer and plan greenhouses using the Biomes Notebook.
- Control natural growth and bonemeal restrictions independently.
- Disable restrictions separately for crops and trees with config settings or operator commands.
- Show concise climate requirements in plant item tooltips.
- Open JEI, EMI, or REI recipes and uses from the field guide item icons.
- Keep JEI's item list and bookmark controls below the open notebook; other menus retain their normal layout.
- Optional support for Farmer's Delight crops, Biomes O' Plenty, Create tree fertilizer, Slice & Dice sprinklers, and snow-covered plants.

## Greenhouses

Plants grow normally in a natural biome that represents their allowed climate or explicit biome rule. Active Electrical and Tough As Nails thermoregulators can also support climate-based crops and saplings directly: aim the front toward the plants, within a four-block horizontal radius and one block above or below. Outside this core, each one-block ring steps one climate category toward the position's natural biome climate. For example, cooling a Hot biome to Icy gives four blocks of Icy, then one block each of Cold, Neutral, and Warm before returning to Hot at eight blocks. Heating follows the same rule in reverse, and smaller climate differences need fewer rings. This rounded front half-circle needs no enclosure, habitat materials, or overhead glass. Solid obstructions block the airflow; fuel, FE, and redstone shutdowns still apply. Explicit biome rules require a matching biome.

For plants outside both their natural biome and direct thermoregulator coverage, greenhouses provide another option with all three conditions:

1. **Sky-exposed glass above the plant.** Keep an unobstructed path from the glass to the sky.
2. **A matching greenhouse habitat.** Build its native floor and every habitat material shown in the Biomes Notebook, inside a sealed room.
3. **The biome's required climate.** The actual room must be Icy, Cold, Neutral, Warm or Hot as required. Matching natural climate is enough; use a powered controller when it does not match.

A sealed room registers its actual biome and spawn lists only when an Electrical or Tough As Nails thermoregulator faces into the room and its native floor, habitat, and climate qualify. An idle or unfueled unit is enough when the natural climate matches. Removing the last qualifying thermoregulator revokes biome registration. Native and greenhouse-assisted crop and sapling growth keep their existing rules independently of registration. **Glass is only a crop and sapling requirement; it is not needed to register the biome.** Put sky-exposed glass directly above each plant that needs greenhouse growth. The rest of the roof may be solid. The guide's **Greenhouse Setup** section explains these checks. Room detection is automatic, with no scanner block, probe, or manual survey.

Desert needs 100% sand ground; Forest needs 90% native soil. Basalt Deltas needs a 90% basalt/blackstone floor, interior magma blocks equal to 3% of the enclosed interior volume, and Hot climate. Habitat materials must be inside the room; foundation, wall, and roof blocks do not count. The guide shows whole-number habitat percentages and keeps floor choices, climate requirements, and construction notes together. Item icons support JEI, EMI, or REI recipes and uses when installed.

Craft the **Biomes Notebook** with a book and any item in the `c:crops` tag. Its updated 16-by-16 item texture depicts a leather-bound notebook with pale pages. The open-book interface keeps the vanilla page layout with a matching leather border and plays Minecraft's book sound when opened.

The **Electrical Thermoregulator** has exactly five settings: **Icy, Cold, Neutral, Warm, Hot**. Use the left and right buttons beside the climate readout to change the selected mode. Its front outlet must face the plants or room it conditions. A powered unit shares its climate through a sealed room and directly supports climate-based crops in its four-block front half-circle. Nearby players at its sides or rear keep their natural climate. Supply FE from any side: it stores **100,000 FE**, accepts **10,000 FE per transfer**, and uses **360 FE/t** while running. Use the **ON/OFF** button to pause the unit; its switch state is saved, and its selected climate and stored FE are kept. Redstone or power loss also stops it. Direct crop support also works when the greenhouse feature is disabled; Minecraft's ordinary plant placement and lighting rules still apply.

**Sable ships:** crop biome checks sample each plant's current world location, including after translation or rotation. Controller coverage stays local to the ship. This integration covers crop growth and local thermoregulator climate; moving greenhouse biomes and shipboard natural mob spawning are not included.

When changing the natural climate, each installed AC supports **256 enclosed interior blocks** in its front room. Capacity counts the room's width, length, and height: an 8×8×4 interior uses 256 blocks, while 8×8×8 uses 512. Additional units combine their capacity: two support 512 interior blocks, four support 1,024. **A naturally matching greenhouse needs no AC, regardless of its interior volume.** Put units inside the room or in its wall with the outlets facing inward; units facing away do not count toward that room. The configured maximum interior volume still applies.

A previously sealed room keeps its existing climate briefly when a door opens or a block is removed. Faint air particles flow from the opening. Close the door or repair the hole within about **one minute** to retain the room climate. If the opening remains, the room gradually steps through the five climate categories toward the natural climate; an AC cannot hold an open room at its selected setting.

The AC retains its 16-pixel Power Grid-inspired green-gray and charcoal artwork, large animated fan, subtle airflow particles, and blue/orange front indicator. Its screen uses a single recessed climate readout with vanilla left/right buttons, a red FE gauge, and a small hoverable **!** indicator at the top right. Opening the unit prioritizes the room in front. The indicator reports **No greenhouse detected** when no room is found. An enclosed room shows its **greenhouse biome valid/not-valid** result, followed by room statistics. Plant-lighting guidance for greenhouse growth appears at the bottom of the tooltip. Climate and AC interior capacity are included below those checks. Resource packs can replace the artwork.

Craft the **Thermometer** with glass, redstone, and an iron ingot in a vertical column. It reports **climate and biome at the player's position**, including when clicking blocks. The biome is the registered greenhouse biome where one is active, otherwise the natural biome. Its gray casing, small red bulb, tinted glass, and scale remain; the mercury readout shows the five climate categories. The glass renders separately from the solid readout to keep the bulb stable.

No numerical temperature settings or readings are used. See [CONFIGURATION.md](CONFIGURATION.md#greenhouses) for habitat rules, coverage, and configuration.

## Configuration

Launch the game once to generate `config/biome_based_crops-server.toml`. An optional `<world>/serverconfig/biome_based_crops-server.toml` overrides it for that world. Edit the active file while the world/server is stopped, then restart.

For example:

```toml
cropBiomeRules = [
    "wheat=minecraft:plains,minecraft:forest",
    "rice=#minecraft:is_jungle",
    "carrot=*"
]
```

These rules replace the matching plant's climate requirements. Unmatched plants keep the existing climate rules. See [CONFIGURATION.md](CONFIGURATION.md) for rule matching, climate settings, and commands.

## Building

Use a Java 21 JDK:

```powershell
.\gradlew.bat build -PincludeLocalMods=false
.\gradlew.bat runGameTestServer -PenableGameTests=true -PincludeLocalMods=true
```

On Linux and macOS use `bash ./gradlew`. The built JAR is `build/libs/biome_based_crops-1.0.0.jar`. Optional local test mods belong in `libs/` and are never bundled. Test code is excluded from the release JAR.

For local electricity testing, place Create **6.0.10** and Create Crafts & Additions **1.6.0** in `libs/`, then run `./gradlew.bat runClient` to load them. Connect a Crafts & Additions power source or accumulator to the Electrical Thermoregulator using its FE connections. The game tests check that a real Crafts & Additions energy source charges and runs it when those optional mods are installed.

After adding or restoring JARs in `libs/`, reload the Gradle project in IntelliJ so its Client launch includes them. The game-test command above includes local mods; use `-PincludeLocalMods=false` only for a standalone run, which skips optional-mod compatibility checks. `-PcompatModsDir=...` uses that directory instead of `libs/`.

The field guide, climate controls, greenhouse feedback, and instrument text have entries for all 137 Minecraft 1.21.1 locales and follow the selected game language. Regional variants may inherit a related language; unsupported rare and constructed languages retain English fallback text for newly added messages. Existing authored translations are preserved.

New-message English fallbacks currently apply to Ido, Interslavic, Lojban, Nahuatl, Popoloca, Quenya, Klingon, Toki Pona, Tzotzil, and Viossa. New Jawi messages use Malay in Latin script. These fallback entries can be replaced by community translations without changing the code.

Available under the [MIT license](LICENSE). The NeoForge template notice is retained in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
