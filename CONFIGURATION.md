# Configuration

## Files

- `config/biome_based_crops-server.toml`: gameplay rules, controlled by the server and synchronized to joining players.
- `<world>/serverconfig/biome_based_crops-server.toml`: optional world-specific gameplay override.
- `config/biome_based_crops-client.toml`: local tooltip preference.
- `config/biome_based_crops-greenhouse.toml`: greenhouse and nearby temperature rules; the server controls these settings too.

Stop the world/server before editing gameplay files, then restart to apply them and synchronize players. Singleplayer also supports the NeoForge config screen through **Mods Ã¢â€ â€™ Biome Based Crops Ã¢â€ â€™ Config** while a world is loaded. Multiplayer players use the server's rules.

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

Greenhouses identify their biome automatically from the room's native materials and climate. No core, biome sample, or binding step is required. The detected biome applies inside its connected interior for crop growth, biome queries, and natural mob spawn lists. Floor, wall, roof, and outdoor positions keep their native biome.

The room's biome is a reversible overlay: it does not rewrite saved chunk biomes or generate new terrain. Opening the enclosure or disabling greenhouses restores the native biome after the normal refresh. Natural spawning still follows normal light, space, structure, dimension, and mob-cap rules.

1. Craft a **Greenhouse Probe** with glass, copper ingots, redstone, and a stick. Craft a book with wheat for the **Farmer's Field Guide**.
2. Select your intended biome in the guide. Each block icon has its own required quantity. **Every listed type must meet its count**: extra sand cannot replace missing cactus, logs, leaves, or other materials.
3. Build a closed room of any shape with a solid floor, walls, and roof. By default, at least 50% of its exposed ceiling area and 10% of its wall area must be glass, with at least four glass wall blocks. Glass blocks and panes count in every orientation, including coloured and tagged mod glass. Close doors and trapdoors.
4. Place all required materials inside the connected room or in its enclosing floor and walls. Keep them exposed to the room: buried blocks behind a solid floor or wall do not count.
5. Match the required climate. A naturally matching biome needs no machine. Otherwise, place a powered **Air Conditioner** inside the connected room and choose the target climate in its settings.
6. Use the probe inside the room to check the detected biome, enclosure, temperature, and individual material progress.

For example, the default Desert recipe requires **8 sand + 4 sandstone + 2 cactus**, all together. With Biomes O' Plenty installed, Ominous Woods requires **8 umbran logs + 8 umbran leaves + 4 dead leaves + 4 umbran wood**.

The guide keeps each biome's material recipe, required climate, and Air Conditioner note on one right-hand summary. Its **How to build** link opens a shared enclosure reference on that same page. Hover an item for its standard tooltip; left-click for recipes or right-click for uses in an installed JEI, EMI, or REI viewer. Vanilla biomes have distinct curated material recipes; installed mod biomes derive several required materials from their native vegetation and a terrain substrate. Custom recipes can replace these choices.

If several recipes fit a room, the most specific completed recipe takes priority. Remove unrelated material types when changing the intended greenhouse. Climate machines refresh within one second. Large rooms are scanned gradually; the probe shows a scanning status until the room is ready. Room geometry stays cached until relevant blocks change, so ordinary energy updates do not repeat a large flood fill. Existing greenhouse cores remain optional inspection stations.

### Air Conditioner

Supply `biome_based_crops:air_conditioner` with Forge Energy (FE) from any side. Its buffer holds 20,000 FE and accepts up to 1,000 FE per transfer. Right-click it to open its settings and choose Icy, Cold, Neutral, Warm, or Hot. A redstone signal disables it.

Power use is **20 FE/t per climate step** between the connected room's native climate and the unit's selected target, from 0 to 80 FE/t. For example, Neutral to Hot costs 40 FE/t, while Icy to Hot costs 80 FE/t. A target already matching the room's natural climate costs nothing and needs no artificial conditioning. Before a room scan finishes, the unit uses its own position's native climate. Power loss or redstone shutdown removes the machine's climate support; the room must then match naturally or have another working machine.

The Air Conditioner serves only its connected enclosed room. Fire, lava, magma, ice, and snow are scenery and no longer change crop climates. Placing more of those blocks cannot replace a powered machine.

Tough As Nails thermoregulators remain compatible when enabled and actively heating or cooling. Heating supplies Neutral/Warm/Hot; cooling supplies Icy/Cold/Neutral; both active modes supply Neutral. Empty, inactive, and redstone-disabled units do not provide a climate. Northstar's Temperature Regulator must be kinetically powered, unstressed, and actively sealing the greenhouse position; unrelated fans, ice boxes, basin heaters, and reactor coolers are not treated as room conditioners.

Northstar targets map to Icy at or below 0 degrees, Cold through 10, Neutral through 25, Warm through 35, and Hot above 35.

### Large rooms and glass

There are no fixed side-length or height limits. The default maximum is **262,144 connected interior blocks**, configurable up to **1,048,576**. All room chunks must already be loaded. Scans use a limited background budget instead of freezing a tick to inspect a huge room at once.

Glass requirements scale with the actual room boundary: the default is 50% roof glass and 10% wall glass, with at least four glass wall blocks. A handful of windows cannot qualify a huge opaque barn. Supported glass is recognized through the common glass block/pane tags and `biome_based_crops:greenhouse_glass`; colour and orientation do not change whether it counts.

### Greenhouse settings

| Setting | Default | Effect |
| --- | --- | --- |
| `enableGreenhouses` | `true` | Recognize suitable enclosed greenhouse biomes. |
| `maxRoomBlocks` | `262144` | Maximum interior volume, from 64 to 1,048,576 blocks. |
| `scanBlocksPerTick` | `1024` | Background scan work per level tick, from 256 to 16,384. |
| `minimumRoofGlassRatio` | `0.5` | Minimum fraction of exposed ceiling faces made from glass. |
| `minimumWallGlassRatio` | `0.1` | Minimum fraction of exposed wall faces made from glass; at least four glass wall blocks are required. |
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
