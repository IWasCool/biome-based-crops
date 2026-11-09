# Changelog

## 1.0.0 (in development)

- Added a configurable FE-powered Air Conditioner with a 20,000 FE buffer and power use based on climate difference. Removed ambient fire and ice climate effects.
- Added automatic greenhouse biome recognition from enclosure, glass, native material recipes, and climate; no core or sampling is required.
- Applied a reversible biome overlay inside valid rooms, including natural spawn lists, without rewriting saved chunk biome data.
- Added a Greenhouse Probe for checking detected biomes and each material requirement.
- Added an English Farmer's Field Guide with Minecraft book artwork, ruled pages, a searchable biome index, compact material summaries, and a shared building reference.
- Added mandatory counts for several distinct native materials per biome, with item icons and bold ink headings.
- Added standard ingredient tooltips and optional JEI, EMI, and REI recipe/use links directly in the guide.
- Added survival crafting recipes, adjustable material recipes, and proportional roof/window requirements for large greenhouses.
- Added incremental room scanning with a configurable volume cap and no fixed side-length or height limit.
- Added per-crop and per-sapling biome overrides using biome IDs, biome tags, or `*` for every biome.
- Applied biome overrides to natural growth, bonemeal, supported fertilizers, and item tooltips.
- Preserved configurable climate defaults, category switches, operator commands, and optional integrations.
- Added biome-rule regression tests, configuration examples, and GitHub development builds.
- Removed unused template examples and internal pack audit documents.
