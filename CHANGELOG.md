# Changelog

## 1.0.0 (in development)

- Added an FE-powered Thermoregulator with a -20 to 50 C dial, actual temperature and oxygen readouts, a 100,000,000 FE buffer, and room-size-scaled power use. Preserved its `air_conditioner` registry ID.
- Added shared room air that heats, cools, and fills with oxygen gradually, plus a craftable Thermometer for current temperature and air readings.
- Added automatic greenhouse biome recognition from enclosure, glass, mandatory native materials, actual temperature, and breathable air; no core or sampling is required.
- Applied a reversible biome overlay inside valid rooms, including natural spawn lists, without rewriting saved chunk biome data.
- Added a Greenhouse Probe for checking detected biomes and each material requirement.
- Added an English Farmer's Field Guide with a searchable biome index and one-page material, temperature, and building summaries, using resource-pack-compatible Minecraft artwork.
- Added distinct required materials per biome, scaled to room volume from a configurable 512-block reference, with compact icons and exact progress tooltips.
- Added standard ingredient tooltips and optional JEI, EMI, and REI recipe/use links directly in the guide.
- Added survival crafting recipes, adjustable material recipes, and proportional roof/window requirements for large greenhouses.
- Added incremental room scanning with a configurable volume cap and no fixed side-length or height limit.
- Integrated actual room temperature and stored oxygen with Northstar's atmosphere checks while preserving native conditions outside the exact room. Retained optional Tough As Nails and Northstar machine support.
- Removed ambient fire and ice climate effects; room conditioning uses powered machines.
- Added per-crop and per-sapling biome overrides using biome IDs, biome tags, or `*` for every biome.
- Applied biome overrides to natural growth, bonemeal, supported fertilizers, and item tooltips.
- Preserved configurable climate defaults, category switches, operator commands, and optional integrations.
- Added biome-rule regression tests, configuration examples, and GitHub development builds.
