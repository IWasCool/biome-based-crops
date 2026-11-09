# Configuration

## Files

- `config/biome_based_crops-server.toml`: gameplay rules, controlled by the server and synchronized to joining players.
- `<world>/serverconfig/biome_based_crops-server.toml`: optional world-specific gameplay override.
- `config/biome_based_crops-client.toml`: local tooltip preference.
- `config/biome_based_crops-greenhouse.toml`: greenhouse and nearby temperature rules; the server controls these settings too.

Stop the world/server before editing gameplay files, then restart to apply them and synchronize players. Singleplayer also supports the NeoForge config screen through **Mods → Biome Based Crops → Config** while a world is loaded. Multiplayer players use the server's rules.

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
- An override replaces that plant's climate requirements for natural growth, bonemeal, and supported fertilizers. Thermoregulators cannot bypass explicit biome overrides.
- The crop token is a case-insensitive substring of the block or item path. A token containing `:` matches the full ID. `wheat` therefore covers wheat plants and seeds; `farmersdelight:tomato` targets that mod's tomatoes.
- The longest matching crop token wins. The first entry wins equal-length ties.
- Only detected crops and growable plants are affected. Adding a rule does not make decorative blocks grow.
- Missing biomes or tags do not match anything. Check spelling and install any mod supplying the referenced biome. Invalid syntax is rejected by config validation.

The default is `cropBiomeRules = []`, preserving all climate defaults. Remove an override to restore the matching plant's climate rule. Tooltips show biome names or tag IDs when an override applies.

## Greenhouses

A valid greenhouse reproduces its selected biome for crops inside it, including explicit biome and biome-tag rules. It does not change world biome data, weather, mob spawning, or terrain. A greenhouse needs a sampled target biome, an enclosed room, glass, native specimen blocks, and a suitable temperature.

1. Craft a **Greenhouse Core** with glass, copper ingots, bricks, a compass, and planks. Place it inside the intended growing room.
2. Craft a **Greenhouse Probe** with glass, copper ingots, redstone, and a stick. Visit the biome you want, then sneak-use the probe on its ground to record a sample.
3. Use the sampled probe on the core to set its target biome. The sample selects the biome; it does not replace the room's requirements.
4. Enclose the room with a solid floor, walls, and roof. Keep the growing area within four blocks horizontally of the core. At the default radius, the surrounding shell fits within an 11 by 11 footprint; the floor can be one block below the core and the roof at most five blocks above it. Open doorways and holes invalidate the room. Close doors before checking it.
5. Include at least four glass blocks or panes in the enclosing shell, with glass in the roof.
6. Place at least four native specimen blocks in the room or its enclosing floor and walls. Any combination of blocks listed for the target biome counts toward the total; you do not need four of every type.
7. Match the target biome's climate with nearby heat or cooling sources. Use the probe on a crop or core to see the current climate, chosen biome, and missing requirements.

Craft a book with wheat to obtain the **Farmer's Field Guide**. It opens to a searchable biome index. Select a biome for visual setup pages: labeled native materials, climate and heat/cooling counts, a greenhouse cutaway, and crafting grids. Turn the book's pages to move between diagrams. Labels and counts appear directly on the page without hover popups. The guide uses English text and the server's actual specimen requirements.

Every registered biome has an entry. Vanilla biomes have familiar terrain materials; additional choices come from biome vegetation features. Mod biomes also receive recipes, with a stone/moss fallback when no native plant material can be read.

Only crops connected to the core's interior space receive its emulated biome. A core outside the room cannot support crops through a wall. Changes can take up to one second to appear while greenhouse checks refresh. Without a valid greenhouse, crops use their actual biome and local temperature.

### Heating and cooling

Nearby temperature sources work outdoors too. The default search extends four blocks horizontally, from one block below the crop to two above it. A greenhouse uses its core as the temperature search centre, so crops in one room share its conditions.

| Source | Heating or cooling strength |
| --- | --- |
| Fire, soul fire, lit campfire, lit soul campfire, lava | +8 heat |
| Magma block | +4 heat |
| Ice or snow block | +4 cooling |
| Packed ice or blue ice | +8 cooling |
| Snow layer | +1 cooling |

Heat and cooling cancel. Every eight points of net strength moves the local climate one step along `icy → cold → neutral → warm → hot`. Outdoor sources can shift up to two steps from the actual biome's climate. An enclosed greenhouse can shift all four steps, allowing even icy and hot biomes to be reproduced far from home. Greenhouse sources must be inside the room or in its enclosing shell; heat cannot pass through a wall from outside. Unlit campfires provide no heat. Adding too much heat or cooling can make a crop's environment unsuitable. A suitable active Tough As Nails thermoregulator remains an alternative climate source.

Blocks in `biome_based_crops:heat_sources` and `biome_based_crops:cooling_sources` also count. Additional tagged sources supply four points each; a heat source with a `lit` state must be lit. `biome_based_crops:greenhouse_glass` accepts common glass blocks and panes, and can be extended by a datapack.

### Greenhouse settings

| Setting | Default | Effect |
| --- | --- | --- |
| `enableGreenhouses` | `true` | Allow valid cores to emulate a sampled biome. |
| `enableAmbientTemperature` | `true` | Let nearby heat and cooling sources change crop temperature. |
| `radius` | `4` | Temperature and greenhouse cultivation radius, from 1 to 6 blocks. |
| `requiredSpecimens` | `4` | Total native specimen blocks, from 1 to 64. |
| `cacheTicks` | `20` | Maximum refresh delay in ticks, from 1 to 20. |
| `biomeMaterialRules` | `[]` | Replace the specimen choices for individual biomes. |

For example:

```toml
biomeMaterialRules = [
    "minecraft:desert=minecraft:sand,minecraft:sandstone,minecraft:cactus",
    "example:orchard=example:fruit_leaves,minecraft:grass_block"
]
```

Each entry replaces the named biome's specimen list. The last entry for a biome wins. Use installed block IDs; missing blocks cannot count as specimens. Stop and restart the world/server after editing these settings.

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

Biomes resolve to one climate. Explicit `toughasnails:*_biomes` tags take priority, including those supplied by Biomes O' Plenty. Conflicting tags resolve in the order icy, cold, hot, warm, neutral. Otherwise snowy/icy biomes or base temperatures below 0.15 are icy; cold-tagged biomes or temperatures below 0.5 are cold; remaining desert-tagged biomes are hot. Other base temperatures resolve to neutral through 0.8, warm through 1.0, and hot above 1.0. Seasons and altitude do not change this classification.

## Growth switches

| Setting | Default | Effect |
| --- | --- | --- |
| `enableCropBiomeDependencies` | `true` | Apply climate and biome rules to crops. |
| `enableSaplingBiomeDependencies` | `true` | Apply rules to trees, bamboo, cactus, sugar cane, mushrooms, fungi, and azaleas. |
| `blockNaturalGrowth` | `true` | Enforce rules for natural growth. |
| `blockBonemealGrowth` | `true` | Enforce rules for bonemeal and supported fertilizers. |
| `enableThermoregulatorCompatibility` | `true` | Let active Tough As Nails thermoregulators satisfy climate requirements. |
| `thermoregulatorRadius` | `4` | Horizontal thermoregulator search radius, from 1 to 12 blocks. |
| `supportedCropNamespaces` | Generated list | Recognize additional custom crop-like plants and raw produce from these mods. |

Vanilla crop and sapling classes are recognized automatically. Namespaces containing `delight` are also supported. Plants can still be placed outside their growth requirements. Disabling either category restores ordinary growth for it and hides its requirement tooltips. `enableTooltips` is in the client config.

Active cooling thermoregulators supply icy/cold/neutral climates; heating supplies neutral/warm/hot; both supply neutral. Empty, inactive, and redstone-disabled regulators supply none. The search covers one block below to three blocks above the plant in loaded chunks. Tough As Nails is optional.

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
