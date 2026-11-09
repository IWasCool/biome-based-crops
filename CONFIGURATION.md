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

Greenhouses identify their biome automatically from the room's native materials and climate. No core, biome sample, or binding step is required. A recognized greenhouse supplies that biome for crop growth, including exact biome and biome-tag rules. World terrain, weather, and mob spawning stay unchanged.

1. Craft a **Greenhouse Probe** with glass, copper ingots, redstone, and a stick. Craft a book with wheat for the **Farmer's Field Guide**.
2. Select your intended biome in the guide. Each block icon has its own required quantity. **Every listed type must meet its count**: extra sand cannot replace missing cactus, logs, leaves, or other materials.
3. Build a closed room with a solid floor, walls, and roof. Include at least four glass blocks or panes, with glass in the roof. Close doors and trapdoors. At the default radius, the interior can be up to 9 by 9 blocks and five blocks tall; the complete floor, walls, and roof fit within 11 by 11 by 7 blocks.
4. Place all required materials inside the connected room or in its enclosing floor and walls. Keep them exposed to the room: buried blocks behind a solid floor or wall do not count.
5. Match the required climate. Your actual biome is the starting climate, so a naturally Cold room already suits a Cold recipe without adding ice.
6. Use the probe inside the room to check the detected biome, enclosure, temperature, and individual material progress.

For example, the default Desert recipe requires **8 sand + 4 sandstone + 2 cactus**, all together. With Biomes O' Plenty installed, Ominous Woods requires **8 umbran logs + 8 umbran leaves + 4 dead leaves + 4 umbran wood**.

The guide keeps each biome's material recipe and required climate on one right-hand summary. Its small **Temperature** and **How to build** links open shared references on that same page. Labels and counts appear directly on the English pages, without hover popups. Vanilla biomes have distinct curated material recipes; installed mod biomes derive several required materials from their native vegetation and a terrain substrate. Custom recipes can replace these choices.

If several recipes fit a room, the most specific completed recipe takes priority. Remove unrelated material types when changing the intended greenhouse. Changes normally refresh within one second; the probe requests a fresh reading. Existing greenhouse cores remain optional inspection stations.

### Heating and cooling

The strongest nearby heat source and strongest cooling source determine the temperature change. Repeated sources do not stack: two campfires have the same effect as one. Opposing strengths cancel.

| Source | Strength | Climate change by itself |
| --- | --- | --- |
| Fire, soul fire, lit campfire, lit soul campfire | 8 heat | +1 step |
| Magma block | 16 heat | +2 steps |
| Lava | 32 heat | +4 steps |
| Snow block or snow layer | 8 cooling | -1 step |
| Ice | 16 cooling | -2 steps |
| Packed ice | 24 cooling | -3 steps |
| Blue ice | 32 cooling | -4 steps |

The climate order is `icy -> cold -> neutral -> warm -> hot`. Subtract the strongest cooling strength from the strongest heat strength, then divide by eight to find the shift. For example, lava and packed ice give `32 - 24 = 8`, or one warming step. Outdoor plants are limited to two steps; enclosed greenhouses can span all four. Unlit campfires provide no heat.

The default source radius is four blocks horizontally from the room's centre, from its physical floor blocks to three blocks above them. Outdoors, the radius is centred on the plant and reaches one block below to two above it. Greenhouse sources must be inside its connected room or in its enclosing shell. Outside sources cannot heat a greenhouse through its walls. A suitable active Tough As Nails thermoregulator remains an alternative climate source; the room and all materials are still required.

Datapacks can extend `biome_based_crops:heat_sources`, `biome_based_crops:cooling_sources`, and `biome_based_crops:greenhouse_glass`. Additional tagged temperature sources have strength 8. A heat source with a `lit` state must be lit.

### Greenhouse settings

| Setting | Default | Effect |
| --- | --- | --- |
| `enableGreenhouses` | `true` | Recognize suitable enclosed greenhouse biomes. |
| `enableAmbientTemperature` | `true` | Apply nearby heat and cooling sources. |
| `radius` | `4` | Interior and temperature search radius, from 1 to 6 blocks. |
| `requiredSpecimens` | `4` | Default count for **each** material in generated mod recipes and bare-ID overrides. |
| `cacheTicks` | `20` | Maximum refresh delay in ticks, from 1 to 20. |
| `biomeMaterialRules` | `[]` | Replace complete material recipes for individual biomes. |

Custom recipes use `biome=block*count,block*count`:

```toml
biomeMaterialRules = [
    "minecraft:desert=minecraft:sand*8,minecraft:sandstone*4,minecraft:cactus*2",
    "example:orchard=example:fruit_log*6,example:fruit_leaves*8,minecraft:grass_block*8"
]
```

Every listed material is mandatory; each count is a minimum. Counts run from 1 to 64 for up to 32 distinct block IDs; duplicate IDs are rejected. The last entry for a biome wins. Use installed block IDs. Stop and restart the world/server after editing these settings.

Older bare-ID rules still load, but now require `requiredSpecimens` blocks of **each** type rather than a combined total. The legacy setting does not change explicit `*count` entries or the curated vanilla recipes.

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
