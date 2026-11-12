# Configuration

## Files

- `config/biome_based_crops-server.toml`: gameplay rules, controlled by the server and synchronized to joining players.
- `<world>/serverconfig/biome_based_crops-server.toml`: optional world-specific gameplay override.
- `config/biome_based_crops-client.toml`: local tooltip preference.
- `config/biome_based_crops-greenhouse.toml`: greenhouse construction, scanning, and material rules, controlled by the server.

Stop the world/server before editing gameplay files, then restart to apply them and synchronize players. Singleplayer also supports the NeoForge config screen through **Mods > Biome Based Crops > Config** while a world is loaded. Multiplayer players use the server's rules.

## Choose biomes for each crop

`cropBiomeRules` is an optional list of overrides. Each entry uses `crop=biome,biome,#biome_tag`:

```toml
cropBiomeRules = [
    "wheat=minecraft:plains,minecraft:forest",
    "rice=#minecraft:is_jungle",
    "farmersdelight:tomato=minecraft:savanna,biomesoplenty:tropics",
    "oak_sapling=minecraft:forest",
    "carrot=*"
]
```

- Biome IDs and biome tags need their full namespace. Prefix a tag with `#`.
- A plant can grow in any listed biome or tag. `*` allows every biome.
- An override replaces that plant's climate requirements for natural growth, bonemeal, and supported fertilizers. Climate alone cannot bypass an explicit biome override; a valid greenhouse registering as a listed biome can satisfy it.
- The crop token is a case-insensitive substring of the block or item path. A token containing `:` matches the full ID. `wheat` therefore covers wheat plants and seeds; `farmersdelight:tomato` targets that mod's tomatoes.
- The longest matching crop token wins. The first entry wins equal-length ties.
- Only detected crops and growable plants are affected. Adding a rule does not make decorative blocks grow.
- Missing biomes or tags do not match anything. Check spelling and install any mod supplying the referenced biome. Invalid syntax is rejected by config validation.

The default is `cropBiomeRules = []`, preserving all climate defaults. Remove an override to restore the matching plant's climate rule. Plant item tooltips remain concise and show climate categories; explicit biome rules still govern growth when configured.

## Greenhouses

A plant can grow in a matching natural biome. Outside that biome, it needs **sky-exposed glass above it**, a **matching greenhouse biome**, and the **climate required by that biome**. Climate is one of **Icy, Cold, Neutral, Warm, Hot**. A controller is needed only when the natural room climate does not match.

Greenhouses identify their biome from a sealed enclosure, native floors, habitat materials, matching climate, and breathable air. Glass is not part of biome registration. The field guide contains the construction notes; detection is automatic. The biome overlay is reversible and does not rewrite saved chunk biomes or generate terrain. A wrong climate cannot activate a habitat's biome. Outdoor controllers do not replace the greenhouse requirements.

1. Craft the **Farmer's Field Guide** and select the intended biome.
2. Build a sealed room with a native floor and suitable solid walls. Give each crop or sapling glass above it with an unobstructed path to the sky.
3. Meet the native-floor coverage and every habitat percentage in the guide.
4. Match the biome's climate naturally or with a powered **Electrical Thermoregulator** set to Icy, Cold, Neutral, Warm or Hot.
5. Check local climate and biome using the **Thermometer**. It reads the player's position, even when a block is clicked.

The room's biome applies only where its enclosure, climate, and habitat qualify. Minecraft still controls spawning, including Peaceful mode, difficulty, the `doMobSpawning` gamerule, light, space, structures, dimensions, and mob caps.

### Native floors and habitat percentages

The enclosure requires sealed walls, flooring, and a roof. **Glass is needed only directly above crops and saplings that use greenhouse growth, with a clear path to the sky.** It is not needed for the room's biome to register, and the rest of the roof may be solid. There are no separate window or entrance quotas. AC capacity scales with enclosed interior volume only when changing the natural climate, as described below. Legacy roof and wall glass ratios do not impose biome-registration quotas.

Floor coverage is a percentage of ground area. Listed ground blocks are alternatives and may be mixed. Forest needs **90%** grass blocks, dirt, rooted dirt, or coarse dirt; Desert needs **100% sand**. Basalt Deltas needs a **90% basalt/blackstone floor**, interior magma blocks equal to **3% of enclosed interior volume**, and **Hot** climate. Magma in the foundation, walls, or roof does not count toward its habitat materials.

The guide shows each habitat material's whole-number percentage. Habitat percentages use enclosed interior volume, including the space occupied by interior scenery. Every material must be inside the room; foundation, wall, and roof blocks do not count. The same proportions scale down for small greenhouses and up for large ones. The server rounds fractional block requirements up. Logs and plants count above ground, and leaf canopies count at least two blocks above the floor. Explicit count-based overrides retain block-count labels.

The localized guide combines the selected biome's floor, habitat percentages, climate requirements, and **Greenhouse Setup** instructions. The guide and climate controls follow the selected game language, with entries for all 137 Minecraft 1.21.1 locales and fallback text for unsupported rare languages. Hover item icons for standard item tooltips; left-click for recipes or right-click for uses with JEI, EMI, or REI installed.

The **Greenhouse Setup** section explains plant lighting, sealed enclosure, leaks, native floor, habitat materials, and climate control. Select a biome on the left to plan its requirements. Room detection and habitat checks run automatically; scanner blocks, probes, and manual surveys have been removed.

Vanilla and Biomes O' Plenty biomes have distinct curated habitats, using characteristic flowers, trees, and rocks. Other installed biomes derive materials from native vegetation and terrain; the server adds distinguishing native materials or obtainable garden/rock accents when two generated plans would otherwise be identical. The guide shows the final server requirements. Custom recipes can replace them without automatic changes.

Default plans use collectible placement items: short grass and ferns, and chorus flowers instead of chorus-plant stem items. Shears or Silk Touch may be needed to collect foliage, webs, crystals, and other delicate materials. Unplaceable or known unobtainable material overrides are ignored in favor of the biome's normal plan; unavailable floor alternatives are omitted. Survival tests check placement-item mappings, real harvest drops, and crafting outputs for the loaded recipes.

A greenhouse provides **one biome only**. Extra identifying materials can refine a simpler recipe into its more specific variant. If two separate complete recipes fit, or custom overrides define identical plans, the room supplies no biome overlay or biome-specific crop support. Remove the extra identifying materials to leave a single complete habitat plan.

### Electrical Thermoregulator

The **Electrical Thermoregulator** retains registry ID `biome_based_crops:air_conditioner`. Supply Forge Energy from any side. It stores **100,000 FE**, accepts **10,000 FE per transfer**, and uses **360 FE/t** while running. A redstone signal stops it.

Its climate selector has exactly five settings, in order: **Icy, Cold, Neutral, Warm, Hot**. Use the vanilla left/right buttons beside the recessed climate readout to select a mode. Clicking the readout also cycles modes; Shift-click cycles backward. The controls support keyboard focus. Opening the menu prioritizes the room directly in front. Hover the small **!** indicator at the top right for **No greenhouse detected** when no room is found, or **Enclosed room detected** with an explicit **greenhouse biome valid/not-valid** result. The sky-exposed-glass instruction appears at the bottom with the capacity guidance. Ceiling-glass counts are informational; glass is not a biome-registration requirement. Put sky-exposed glass directly above each crop or sapling that needs greenhouse growth. Climate, interior capacity, required AC count, and any unmet biome requirement are also shown. Hover the FE gauge for energy, power use, and room volume. There are no numerical temperature controls or readings.

The front outlet chooses which room an AC serves. Install it inside the room, or in a wall with the outlet facing inward. Its selected climate spreads through that connected, sealed room. Outdoors, only the short breeze directly in front of the outlet changes local climate; standing nearby at the sides or rear does not. The actual climate at the plant must match its greenhouse biome; a controller never bypasses plant lighting, habitat, or biome checks. A naturally matching climate works without a controller.

When changing the natural climate, each eligible installed AC supports **256 enclosed interior blocks** in its room. Count the entire interior volume, including its height: an 8×8×4 interior uses one unit's 256-block capacity; 8×8×8 uses two units' 512-block capacity. Capacities add across units facing into the same sealed room, including units placed inside it. **Naturally matching rooms need no AC at any interior size.** Rooms may have any shape within the separate maximum interior-volume limit.

Opening a door or removing a boundary block from a previously sealed room starts a leak. Faint air wisps leave the opening. Repair or close it within about **one minute** to keep the room climate. If left open, the climate gradually steps toward the natural climate; paid AC output cannot indefinitely preserve the room setting through an opening. Repeated inspections or additional holes do not restart this grace period.

Power loss, redstone shutdown, or removal ends the unit's active climate output. The fan, faint airflow particles, and compressor sound run only while the unit is active. In hostile atmospheres, powered air maintenance still supplies room oxygen; a sealed room must be breathable for plants to grow.

The machine uses original 16-pixel textures in a Power Grid-inspired muted green-gray and charcoal palette. Its static rear casing and animated rotor use files no larger than 16 pixels on either axis. The screen uses native Minecraft button and text-field sprites for its climate selector. Resource packs can replace the artwork.

Craft a **Thermometer** with glass, redstone, and an iron ingot in a vertical column. It displays **Climate:** followed by the player's local **Icy, Cold, Neutral, Warm or Hot** climate, then **Biome:** followed by the active greenhouse biome or natural biome. Clicking a block does not measure that block's position. Its mercury illustration has five category readings beneath tinted glass, with the solid readout in a separate render pass.

### Optional integrations

The mod does not require another temperature mod. Optional external climate controllers and native atmosphere integrations remain supported when installed and active. They participate in the same greenhouse climate checks; they cannot let crops grow in an unrelated outdoor biome or remove the plant-lighting requirement. Crop requirements and normal messages do not depend on those integrations.

### Large rooms and glass

There are no fixed side-length or height limits. The default maximum is **1,048,576 connected interior blocks**. AC interior capacity matters when changing climate; a naturally matching greenhouse does not need machines. All room chunks must already be loaded. Large rooms are detected gradually over several ticks without a manual scan. Ordinary energy updates do not repeat a large flood fill. Small air snapshots preserve the room state and oxygen when geometry is evicted from the bounded scan cache; unloaded time never produces free controller output.

Build a sealed enclosure with the required habitat materials and climate to register its biome. Then place sky-exposed glass directly above any crops and saplings that need greenhouse growth. Other roof cells and walls may use solid decorative blocks. Supported glass is recognized through the common glass block/pane tags and `biome_based_crops:greenhouse_glass`; colour and orientation do not change whether it counts for plant lighting.

### Greenhouse settings

| Setting | Default | Effect |
| --- | --- | --- |
| `enableGreenhouses` | `true` | Recognize suitable enclosed greenhouse biomes. |
| `maxRoomBlocks` | `1048576` | Maximum interior volume, from 64 to 1,048,576 blocks. |
| `scanBlocksPerTick` | `1024` | Background scan work per level tick, from 256 to 16,384. |
| `materialReferenceVolume` | `512` | Reference for default habitat percentages and legacy custom-count scaling, from 64 to 1,048,576 blocks. |
| `minimumRoofGlassRatio` | `1.0` | Legacy roof-glass survey ratio; it does not control biome registration or require a fully glazed roof for plant growth. |
| `minimumWallGlassRatio` | `0.0` | Legacy setting retained for existing configs; ignored because wall glass is no longer required. |
| `requiredSpecimens` | `4` | Base value for generated mod percentages and bare-ID custom overrides. |
| `cacheTicks` | `20` | Maximum refresh delay in ticks, from 1 to 20. |
| `biomeMaterialRules` | `[]` | Replace complete material recipes for individual biomes. |
| `biomeFloorRules` | `[]` | Override native-floor coverage and accepted ground blocks for individual biomes. |

Custom recipes use `biome=block*count,block*count`:

```toml
biomeMaterialRules = [
    "minecraft:desert=minecraft:sandstone*16,minecraft:cactus*4,minecraft:dead_bush*8"
]
biomeFloorRules = [
    "minecraft:desert=100:minecraft:sand"
]
```

Every material in `biomeMaterialRules` is mandatory. Custom recipes deliberately replace the automatic habitat plan, including its floor; add a matching `biomeFloorRules` entry to require native ground. Existing operator overrides retain their legacy base amounts and placement flexibility, increasing for rooms larger than the reference:

```text
custom required = max(configured count, ceil(room volume * configured count / materialReferenceVolume))
```

Custom base amounts run from 1 to 64 for up to 32 distinct block IDs. Duplicate IDs are rejected. Default biome plans use proportional percentages without this legacy minimum.

Floor rules use `biome=percentage:block,block`. The percentage runs from 0 to 100; up to 16 listed block IDs are alternatives for that coverage. The last matching entry in each setting wins. Use installed IDs, then restart the world/server after editing.

Older bare-ID rules still load and use `requiredSpecimens` as the base amount for **each** type. The legacy setting does not change explicit `*count` entries or curated vanilla percentages.

## Choose climates

Plants without a biome override use `cropClimateRules`:

```toml
cropClimateRules = [
    "wheat=neutral,warm",
    "carrot=cold,neutral",
    "rice=warm,hot",
    "spruce_sapling=icy,cold,neutral"
]
defaultCropClimates = "neutral,warm"
```

The generated config contains the full default list; this short example replaces that list if copied verbatim. Edit individual entries to retain the other defaults. Matching and precedence work like biome rules. Valid climates are `icy`, `cold`, `neutral`, `warm`, and `hot`. A rule listing all five permits every climate. `defaultCropClimates` applies to detected plants without a matching climate token.

Biomes resolve to one of the five climate categories using climate tags and their native biome classification. Snowy and icy biomes classify as Icy, cold biomes as Cold, temperate biomes as Neutral, and warmer biomes as Warm or Hot. Seasons and altitude do not change the crop category. Compatibility biome tags can refine the classification when another biome mod supplies them.

## Growth switches

| Setting | Default | Effect |
| --- | --- | --- |
| `enableCropBiomeDependencies` | `true` | Apply climate and biome rules to crops. |
| `enableSaplingBiomeDependencies` | `true` | Apply rules to trees, bamboo, cactus, sugar cane, mushrooms, fungi, and azaleas. |
| `blockNaturalGrowth` | `true` | Enforce rules for natural growth. |
| `blockBonemealGrowth` | `true` | Enforce rules for bonemeal and supported fertilizers. |
| `enableThermoregulatorCompatibility` | `true` | Allow active optional external controllers to condition greenhouse climate. They do not bypass growth requirements. |
| `thermoregulatorRadius` | `16` | External climate-controller coverage radius, from 1 to 64 blocks. |
| `climateRulesVersion` | `0` | Migration marker for the controller-radius default; earlier factory radii upgrade to 16. |
| `supportedCropNamespaces` | Generated list | Recognize additional custom crop-like plants and raw produce from these mods. |

Vanilla crop and sapling classes are recognized automatically. Namespaces containing `delight` are also supported. Plants can still be placed outside their growth requirements. Disabling either category restores ordinary growth for it and hides its requirement tooltips. `enableTooltips` is in the client config.

An external controller must be active and cover the queried position. Empty, inactive, and redstone-disabled controllers supply no climate. Their output is considered only as climate conditioning, never as a shortcut around a greenhouse or biome rule.

## Commands

| Command | Effect |
| --- | --- |
| `/biomebasedcrops status` | Show both category settings. |
| `/biomebasedcrops enable` or `disable` | Enable or disable restrictions for both categories. |
| `/biomebasedcrops crops enable` or `disable` | Change crop restrictions. |
| `/biomebasedcrops trees enable` or `disable` | Change tree and other sapling restrictions. |
| `/biomebasedcrops crops status` or `/biomebasedcrops trees status` | Show one category's setting. |

Changes require operator permission level 2, singleplayer cheats, or the server console. Status is available to everyone. Command changes apply immediately, save to the active config, and update connected clients running this version.

## Older configs

Earlier builds used `config/biome_based_crops-common.toml`. Copy intentional gameplay changes into the server config and move `enableTooltips` into the client config. Existing climate rules remain valid; add `cropBiomeRules` only when you want explicit biome overrides.
