# Biome Based Crops

Configurable climate and biome requirements for crops, saplings, and other plants. Grow plants in their native environment or build greenhouses with matching climates.

Version **1.0.1** for **Minecraft 1.21.1**, **NeoForge 21.1.251+**, and **Java 21**.

- **Biomes Notebook:** browse biome habitats and greenhouse requirements.
- **Electrical Thermoregulator:** five climate settings, directional airflow, and an on/off switch.
- **Thermometer:** read the local climate and biome.

Place `biome_based_crops-neoforge-1.21.1-1.0.1.jar` in the client and server `mods` folders. Config files are generated on first launch.

1.0.1 fixes the creative inventory tab name to **Biome Based Crops**. The **Biomes Notebook** item keeps its name. Language support matches Create New World Core?s **18 locales**: `de_de`, `en_us`, `es_es`, `es_mx`, `fr_fr`, `id_id`, `it_it`, `ja_jp`, `ko_kr`, `pl_pl`, `pt_br`, `pt_pt`, `ru_ru`, `tr_tr`, `uk_ua`, `vi_vn`, `zh_cn`, `zh_tw`. Other languages use the English fallback.

Build with Java 21:

```powershell
.\gradlew.bat build
```

On Linux/macOS, use `bash ./gradlew build`. The JAR is written to `build/libs/`.

[MIT license](LICENSE). [NeoForge template notice](TEMPLATE_LICENSE.txt).
