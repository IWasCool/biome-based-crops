# Changelog

## 1.0.0 (in development)

- Added automatic greenhouse biome recognition from a sealed room, native floor coverage, interior habitat materials, matching climate, and breathable air. Room detection no longer needs a core, probe, or manual survey.
- Applied a reversible biome overlay inside qualifying rooms, including the biome's natural mob spawn lists, without rewriting saved chunk biomes. Normal Minecraft spawning rules still apply.
- Separated biome registration from plant lighting: crops and saplings using greenhouse growth need sky-exposed glass directly above them; other roof cells and walls may be solid.
- Added scalable native-floor and habitat recipes, distinct vanilla and Biomes O' Plenty habitat plans, generated plans for other installed biomes, and configurable overrides. A room provides only one complete biome habitat at a time.
- Added an FE-powered Electrical Thermoregulator with exactly five modes: Icy, Cold, Neutral, Warm, and Hot. Retained the `air_conditioner` registry ID; it stores 100,000 FE, accepts 10,000 FE per transfer, and uses 360 FE/t while running.
- Made controller outlets directional. Each eligible AC supports 256 enclosed interior blocks when changing the natural climate, and installed units combine their capacity. Naturally matching greenhouses need no AC; outdoor output stays in the short breeze in front of the outlet.
- Added a one-minute leak grace period with faint air wisps at openings, followed by gradual climate drift toward natural conditions if the room stays open.
- Updated the thermoregulator with 16-pixel machine artwork, an animated fan, subtle airflow and compressor sound, a red FE gauge, native Minecraft climate controls, and a top-right room-status indicator.
- Added a searchable Farmer's Field Guide with Required Climate, Floor Blocks, Interior Requirements, and Greenhouse Setup sections. Climate icons and labels have a matching separator, and smaller screens can scroll long pages.
- Gave the guide a single 16-by-16 leather-book item texture that preserves the vanilla outline, a matching leather border around the open-book UI, and Minecraft's book sound when opened. Craft it with a book and a crop, seed, or sapling.
- Added standard ingredient tooltips and optional JEI, EMI, and REI recipe/use links directly in the guide.
- Added a Thermometer that reads the climate and biome at the player's position, including when clicking a block. Its solid mercury readout and separate glass layer show the five climate categories. Craft it with glass, redstone, and an iron ingot in a vertical column.
- Shared greenhouse climate readings with Tough As Nails instruments and player temperature, and integrated room oxygen and climate with Northstar while preserving native conditions outside the controlled room. Optional external climate controllers remain supported.
- Added per-crop and per-sapling biome overrides using biome IDs, biome tags, or `*`, applied to natural growth, bonemeal, and supported fertilizers. Plant item tooltips show concise climate requirements; blocked-growth messages stay short and point to the guide where useful.
- Preserved category switches, operator commands, configurable climate defaults, and optional support for Farmer's Delight, Create tree fertilizer, Slice & Dice sprinklers, and snow-covered plants.
- Added guide, climate-control, instrument, and greenhouse text entries for all 137 Minecraft 1.21.1 locales. The guide follows the selected language; regional inheritance and English fallbacks cover unsupported rare languages while preserving existing translations.
- Added incremental room scanning with a configurable interior-volume cap and no fixed side-length or height limit, along with configuration migrations, unit and game tests, documentation, and GitHub development builds.
