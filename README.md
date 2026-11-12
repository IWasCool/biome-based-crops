# Biome Based Crops

Configurable climate and biome requirements for crops, saplings, and other plants. Grow plants in their native environment, use directional thermoregulators, or build greenhouses with native materials and a matching climate.

Version **1.0.0** for **Minecraft 1.21.1**, **NeoForge 21.1.251+**, and **Java 21**.

- **Biomes Notebook:** browse biome habitats and greenhouse requirements.
- **Electrical Thermoregulator:** five climate settings, directional airflow, and an on/off switch.
- **Thermometer:** read the local climate and biome.
- Optional integrations include Tough As Nails, Sable ships, Farmer's Delight, Biomes O' Plenty, JEI, EMI, and REI.

No additional mods need installing. Place `biome_based_crops-1.0.0.jar` in the client and server `mods` folders. Config files are generated on first launch.

Build with Java 21:

```powershell
.\gradlew.bat build -PincludeLocalMods=false
```

On Linux/macOS, use `bash ./gradlew build -PincludeLocalMods=false`. The JAR is written to `build/libs/`.

[MIT license](LICENSE). [NeoForge template notice](TEMPLATE_LICENSE.txt).
