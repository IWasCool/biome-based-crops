# Biome Based Crops

Configurable biome-based growth for crops, trees, and other plants.

Version **1.0.0** (in development) for **Minecraft 1.21.1**, **NeoForge 21.1.251+**, and **Java 21**.

[Development builds](https://github.com/IWasCool/biome-based-crops/actions/workflows/build.yml) | [Source](https://github.com/IWasCool/biome-based-crops) | [Issues](https://github.com/IWasCool/biome-based-crops/issues)

## Installation

Place `biome_based_crops-1.0.0.jar` in the client and server `mods` folders. No other mods are required.

## Features

- Assign crops and saplings to climates, specific biomes, or biome tags.
- Set greenhouse climates with an FE-powered Air Conditioner, with optional Tough As Nails and Northstar machine support.
- Build greenhouses that automatically identify their biome from native materials and local temperature.
- Apply the detected biome inside the room, including its natural mob spawn lists, while the surrounding world keeps its native biome.
- Check conditions with a Greenhouse Probe and follow each biome's visual material recipe in the Farmer's Field Guide.
- Control natural growth and bonemeal restrictions independently.
- Disable restrictions separately for crops and trees with config settings or operator commands.
- Show growth requirements in item tooltips.
- Open JEI, EMI, or REI recipes and uses from the field guide item icons.
- Optional support for Farmer's Delight crops, Biomes O' Plenty, Tough As Nails thermoregulators, Create tree fertilizer, Slice & Dice sprinklers, and snow-covered plants.

## Greenhouses

Craft a **Greenhouse Probe** and **Farmer's Field Guide**. Build an enclosed room with enough glass for its size, place every required material and quantity, and match its climate using the natural biome or a powered climate machine. The greenhouse identifies its biome automatically; no core or sample is required.

Use the probe inside the room to check its detected biome, climate, enclosure, and each material count. Select a biome in the guide's searchable index for its complete material recipe and climate. The shared **How to build** reference explains the enclosure; the biome summary shows its climate and Air Conditioner requirement. The guide covers installed mod biomes and uses English text. Large rooms scan gradually; there are no fixed width or height limits within the configured volume cap. See [the greenhouse configuration guide](CONFIGURATION.md#greenhouses) for building limits and climate control.

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

On Linux and macOS use `bash ./gradlew`. The built JAR is `build/libs/biome_based_crops-1.0.0.jar`. Optional local test mods belong in `libs/` and are never bundled. Test code is excluded from the release JAR.

Language resources cover all 137 Minecraft 1.21.1 locales. New biome-rule and greenhouse text uses English fallbacks; translation corrections are welcome.

Available under the [MIT license](LICENSE). The NeoForge template notice is retained in [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt).
