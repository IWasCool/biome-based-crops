# Biome Based Crops

Configurable biome-based growth for crops, trees, and other plants.

Version **1.0.0** (in development) for **Minecraft 1.21.1**, **NeoForge 21.1.251+**, and **Java 21**.

[Development builds](https://github.com/IWasCool/biome-based-crops/actions/workflows/build.yml) | [Source](https://github.com/IWasCool/biome-based-crops) | [Issues](https://github.com/IWasCool/biome-based-crops/issues)

## Installation

Place `biome_based_crops-1.0.0.jar` in the client and server `mods` folders. No other mods are required.

## Features

- Assign crops and saplings to climates, specific biomes, or biome tags.
- Build greenhouses that automatically identify their biome from required native materials, actual temperature, and breathable air.
- Condition room temperature and oxygen gradually with an FE-powered Thermoregulator and a Celsius target dial.
- Build a native biome floor and match habitat percentages that scale with room size.
- Apply the detected biome inside the room, including its natural mob spawn lists, while the surrounding world keeps its native biome.
- Check conditions with a Greenhouse Probe and Thermometer, and find material recipes in the Farmer's Field Guide.
- Control natural growth and bonemeal restrictions independently.
- Disable restrictions separately for crops and trees with config settings or operator commands.
- Show growth requirements in item tooltips.
- Open JEI, EMI, or REI recipes and uses from the field guide item icons.
- Optional support for Farmer's Delight crops, Biomes O' Plenty, Tough As Nails thermoregulators, Create tree fertilizer, Slice & Dice sprinklers, and snow-covered plants.

## Greenhouses

Craft a **Greenhouse Probe** and **Farmer's Field Guide**. The greenhouse identifies its biome automatically; no core, sample, or binding step is required.

1. Seal a room with a native floor and glass walls and roof. Close its entrance.
2. Place every biome material inside the room and meet the percentages shown in the guide.
3. Match its required temperature. If needed, power a **Thermoregulator**, set its dial from **-20 to 50 C**, and wait for the room to reach its temperature and air requirements.
4. Scan with the probe to check the detected biome and material progress.

Desert needs 100% sand ground; Forest needs 90% native soil. Habitat percentages apply to the room's interior volume, so the same proportions work for small and large greenhouses.

The guide keeps the selected biome's floor, habitat percentages, and combined temperature/Thermoregulator instructions together. Item icons support standard tooltips and optional JEI, EMI, or REI recipes and uses.

The Thermoregulator is a full block with original 16-pixel air-conditioner textures. Its screen uses a vanilla container background, a centered Celsius dial, and a vertical red FE gauge. Hover the top-right Thermometer for current temperature, or the energy gauge for exact FE and power use. Resource packs can replace the book, container, and custom artwork.

Craft the **Thermometer** with glass, redstone, and a copper ingot in a vertical column, in that order. Use it to read actual temperature and oxygen. Large rooms scan and condition gradually; there are no fixed side-length or height limits within the configured volume cap. See [CONFIGURATION.md](CONFIGURATION.md#greenhouses) for material scaling, power costs, and optional machine support.

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
