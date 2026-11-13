# Biome Based Crops

Configurable climate and biome requirements for crops, saplings, and other plants. Grow plants in their native environment, use directional thermoregulators, or build greenhouses with native materials and a matching climate.

Version **1.0.0**, with separate **NeoForge** builds for **Minecraft 1.21 through 1.21.11**. Requires **Java 21**. Each JAR accepts exactly its Minecraft version; there is no universal JAR.

- **Biomes Notebook:** browse biome habitats and greenhouse requirements.
- **Electrical Thermoregulator:** five climate settings, directional airflow, and an on/off switch.
- **Thermometer:** read the local climate and biome.
- Optional integrations include Tough As Nails, Sable ships, Farmer's Delight, Biomes O' Plenty, JEI, EMI, and REI where those mods have matching releases. Sable Companion is bundled only with 1.21.1; other builds do not install a dependency for the wrong Minecraft version.

No additional mods need installing. Put only the matching runtime JAR, such as `biome_based_crops-1.0.0+mc1.21.8.jar`, in the client and server `mods` folders. Do not install the `-sources.jar` or multiple versions together. Config files are generated on first launch.

The 1.21.2, 1.21.6, 1.21.7, and 1.21.9 builds use NeoForge's published beta releases. Server smoke tests cover mod registration, crafting recipes, controller save/load, and energy transfer. Client visuals and optional-mod combinations still need manual testing.

Build and smoke-test one target with Java 21:

```powershell
.\gradlew.bat build "-PmcVersion=1.21.8" --project-cache-dir .gradle/versions/1.21.8 -PincludeLocalMods=false -PfastBuild=true -PcompatSmoke=true
```

Replace `1.21.8` in both arguments with the desired version (`1.21`, or `1.21.1` through `1.21.11`). JARs go to `build/versions/<version>/libs/`. Each target needs its own Gradle history so builds cannot remove another target's outputs.

Build and test every target with `powershell -ExecutionPolicy Bypass -File .\build-all.ps1`, or `bash ./build-all.sh` on Linux/macOS. The scripts collect runtime and sources JARs in `build/releases/`. Omit `-PfastBuild=true` when you need decompiled Minecraft sources for development.

The original default command, `.\gradlew.bat build -PincludeLocalMods=false`, still produces the 1.21.1 JAR in `build/libs/`.

[MIT license](LICENSE). [NeoForge template notice](TEMPLATE_LICENSE.txt).
