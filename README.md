# Biome Based Crops

Configurable biome-based growth for crops, trees, and other plants.

Version **1.0.0** (in development) for **Minecraft 1.21.1**, **NeoForge 21.1.251+**, and **Java 21**.

[Development builds](https://github.com/IWasCool/biome-based-crops/actions/workflows/build.yml) · [Source](https://github.com/IWasCool/biome-based-crops) · [Issues](https://github.com/IWasCool/biome-based-crops/issues)

## Installation

Place `biome_based_crops-1.0.0.jar` in the client and server `mods` folders. No other mods are required.

## Features

- Assign crops and saplings to climates, specific biomes, or biome tags.
- Warm crops with fire, lit campfires, lava, and magma, or cool them with ice and snow.
- Build enclosed greenhouses that emulate sampled biomes using native blocks and suitable temperatures.
- Check conditions with a Greenhouse Probe and follow visual setup pages for each biome in the Farmer's Field Guide.
- Control natural growth and bonemeal restrictions independently.
- Disable restrictions separately for crops and trees with config settings or operator commands.
- Show growth requirements in item tooltips.
- Optional support for Farmer's Delight crops, Biomes O' Plenty, Tough As Nails thermoregulators, Create tree fertilizer, Slice & Dice sprinklers, and snow-covered plants.

## Greenhouses

Craft a **Greenhouse Core**, **Greenhouse Probe**, and **Farmer's Field Guide**. Sneak-use the probe on the ground in the biome you want to reproduce, then use it on the core to select that biome. Build an enclosed room with glass in its roof, add the native specimen blocks listed in the guide, and balance heating or cooling until the probe reports a valid greenhouse.

Use the probe on a crop or the core to check its registered biome, temperature, enclosure, and missing requirements. Select a biome in the guide's searchable index for labeled material icons, a greenhouse cutaway, climate targets, and crafting layouts. The guide covers installed mod biomes and uses English text. See [the greenhouse configuration guide](CONFIGURATION.md#greenhouses) for building limits and temperature sources.

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
.\gradlew.bat runGameTestServer -PenableGameTests=true -PincludeLocalMods=false
```

On Linux and macOS use `bash ./gradlew`. The release JAR is `build/libs/biome_based_crops-1.0.0.jar`. Optional local test mods belong in `libs/` and are never bundled. Test code is excluded from the release JAR.

Language resources cover all 137 Minecraft 1.21.1 locales. New biome-rule and greenhouse text uses English fallbacks; translation corrections are welcome.

Available under the [MIT license](LICENSE). The NeoForge template notice is retained in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
