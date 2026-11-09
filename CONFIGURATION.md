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
- An override replaces that plant's climate requirements for natural growth, bonemeal, and supported fertilizers. Temperature alone cannot bypass an explicit biome override; a valid greenhouse registering as a listed biome can satisfy it.
- The crop token is a case-insensitive substring of the block or item path. A token containing `:` matches the full ID. `wheat` therefore covers wheat plants and seeds; `farmersdelight:tomato` targets that mod's tomatoes.
- The longest matching crop token wins. The first entry wins equal-length ties.
- Only detected crops and growable plants are affected. Adding a rule does not make decorative blocks grow.
- Missing biomes or tags do not match anything. Check spelling and install any mod supplying the referenced biome. Invalid syntax is rejected by config validation.

The default is `cropBiomeRules = []`, preserving all climate defaults. Remove an override to restore the matching plant's climate rule. Tooltips show biome names or tag IDs when an override applies.

## Greenhouses

Greenhouses identify their biome automatically from required native materials, actual room temperature, and breathable air. No core, biome sample, or binding step is required. The detected biome applies inside its connected interior for crop growth, biome queries, and natural mob spawn lists. Floor, wall, roof, and outdoor positions keep their native biome.

The room's biome is a reversible overlay: it does not rewrite saved chunk biomes or generate new terrain. Opening the enclosure or disabling greenhouses restores the native biome. Natural spawning still follows normal light, space, structure, dimension, and mob-cap rules.

1. Craft a **Greenhouse Probe** with glass, copper ingots, redstone, and a stick. Craft a book with wheat for the **Farmer's Field Guide**.
2. Select your intended biome in the guide. Meet its native-floor coverage and **every listed material count**. Counts scale with the scanned room's volume and floor area.
3. Build a closed room of any shape with a solid floor, walls, and roof. By default, at least 50% of its exposed ceiling area and 10% of its wall area must be glass, with at least four glass wall blocks. Glass blocks and panes count in every orientation, including coloured and tagged mod glass. Close doors and trapdoors.
4. Build the required native ground. Place the listed habitat materials inside the room above its floor, with tree canopies above the trunks. The guide shows floor and material progress separately.
5. Match the required temperature and air conditions. A naturally suitable room needs no machine. Otherwise, place a powered **Thermoregulator** inside the connected room, choose a Celsius target, and wait for temperature and oxygen to settle.
6. Use the probe inside the room to check the detected biome, enclosure, temperature, and individual material progress. In hostile atmospheres, room oxygen must reach 95% before it is breathable.

### Native floors and material scaling

Each default biome requires native ground across its measured floor area. Listed ground blocks are alternatives and may be mixed. Forest needs **90%** grass blocks, dirt, rooted dirt, or coarse dirt; Desert needs **100% sand**. One lowest supporting floor position counts per room column.

For an **8 x 8 Desert floor**, all **64 positions** must register as sand. Put crop farmland directly on a sand underlay; the scanner checks exactly one block beneath farmland or a climate machine. Other floor blocks use their own material. Sandstone can form the wall frame. Tree logs and plants count above ground inside the room, with leaf canopies at least two blocks above the floor.

Examples at **512 interior blocks** and **64 floor positions**:

| Biome | Native ground | Required habitat materials |
| --- | --- | --- |
| Desert | All 64 positions sand | 16 sandstone, 4 cactus, 8 dead bushes |
| Forest | At least 58 positions native soil | 16 oak logs, 64 oak leaves, 8 birch logs |

Larger rooms increase each feature count using the greatest of its base count, volume requirement, and floor-density requirement:

```text
required = max(base count, ceil(base count * room volume / reference volume), floor-density count)
```

Default density adds one log per eight floor positions, one canopy block per two, and one plant or other feature per sixteen. Base minima still apply. Floor coverage remains a percentage of actual ground area, independent of room height. `materialReferenceVolume` controls the volume term and defaults to **512**.

The English guide shows native-floor coverage, every required material, temperature, and short building instructions on one right-hand page. After a scan, quantities use that room's volume and floor area. Large count overlays are abbreviated, with exact counts and found progress in ingredient tooltips. Left-click for recipes or right-click for uses in an installed JEI, EMI, or REI viewer; without one, ordinary item tooltips remain available.

Vanilla biomes have curated habitats; installed mod biomes derive materials from native vegetation and terrain. Custom recipes can replace them. If several recipes fit, the most specific completed recipe takes priority. Remove unrelated materials when changing the intended greenhouse. Existing greenhouse cores remain optional inspection stations.

### Thermoregulator

The **Thermoregulator** retains the registry ID `biome_based_crops:air_conditioner` for saved-world compatibility. Supply Forge Energy (FE) from any side. It stores **100,000,000 FE** and accepts up to **20,000,000 FE per transfer**. A redstone signal stops conditioning.

Right-click to set its centered Celsius dial from **-20 to 50 C**, default **20 C**. Hover the top-right Thermometer for current temperature. The vertical red FE gauge shows stored energy; hover it for exact energy, power use, and room volume. The screen uses a vanilla container background with custom dial and gauge textures. Room oxygen remains part of conditioning and can be checked with the Thermometer or probe.

The machine is a full block with original 16-pixel painted-metal textures, intake and outlet grilles, a control lens, and rear mechanical details. Resource packs can replace its artwork.

While conditioning, each unit uses:

```text
FE/t = (2000 + ceil(50 * abs(target C - native C))) * ceil(room volume / 512)
```

Cost is capped at 100,000,000 FE/t. A 512-block room with native 20 C and target 30 C costs **2,500 FE/t**; a 1,024-block room costs **5,000 FE/t**. The power formula always uses 512 blocks, independently of the configurable material reference volume. A naturally breathable room idles at zero cost after it settles at its native temperature. A hostile atmosphere still needs powered air maintenance.

Temperature and oxygen change gradually throughout the shared room; selecting a dial target does not instantly qualify a biome. Larger rooms take longer, and multiple powered units contribute to the same room air. Power loss or redstone shutdown stops conditioning; stored heat and oxygen then drift toward native conditions. Opening the room lets air escape.

Actual Celsius temperatures map to crop climates as follows:

| Climate | Room temperature |
| --- | --- |
| Icy | 0 C or below |
| Cold | Above 0 through 10 C |
| Neutral | Above 10 through 25 C |
| Warm | Above 25 through 35 C |
| Hot | Above 35 C |

Craft a **Thermometer** with a vertical column of **glass, redstone, copper ingot**, from top to bottom. Use it to read current temperature and oxygen at your position, or at a clicked block face. Fire, lava, magma, ice, and snow do not condition rooms.

### Optional climate and atmosphere compatibility

Tough As Nails thermoregulators and Northstar's Temperature Regulator remain supported when genuinely active. Northstar regulators must be powered, unstressed, and sealing the queried position; unrelated fans, ice boxes, and reactor coolers are not room conditioners. The field guide displays this mod's Thermoregulator rather than listing optional machines.

On Northstar planets, greenhouse conditioning uses the native Celsius temperature and atmosphere. Current temperature and breathable room oxygen apply only to the exact connected interior; oxygen drains consume that room's stored air. Northstar's own providers retain priority. Outside controlled rooms, native planetary temperature, oxygen, and other dimensions' environments remain unchanged.

### Large rooms and glass

There are no fixed side-length or height limits. The default maximum is **262,144 connected interior blocks**, configurable up to **1,048,576**. All room chunks must already be loaded. Large rooms scan gradually; the probe shows a scanning status until ready. Ordinary energy updates do not repeat a large flood fill. Small air snapshots preserve produced heat and oxygen when geometry is evicted from the bounded scan cache.

Glass requirements scale with the actual room boundary: the default is 50% roof glass and 10% wall glass, with at least four glass wall blocks. A handful of windows cannot qualify a huge opaque barn. Supported glass is recognized through the common glass block/pane tags and `biome_based_crops:greenhouse_glass`; colour and orientation do not change whether it counts.

### Greenhouse settings

| Setting | Default | Effect |
| --- | --- | --- |
| `enableGreenhouses` | `true` | Recognize suitable enclosed greenhouse biomes. |
| `maxRoomBlocks` | `262144` | Maximum interior volume, from 64 to 1,048,576 blocks. |
| `scanBlocksPerTick` | `1024` | Background scan work per level tick, from 256 to 16,384. |
| `materialReferenceVolume` | `512` | Reference for volume-scaled material counts, from 64 to 1,048,576 blocks; native floor coverage and feature density are checked separately. |
| `minimumRoofGlassRatio` | `0.5` | Minimum fraction of exposed ceiling faces made from glass. |
| `minimumWallGlassRatio` | `0.1` | Minimum fraction of exposed wall faces made from glass; at least four glass wall blocks are required. |
| `requiredSpecimens` | `4` | Base count for each generated mod material and bare-ID override; default habitats also enforce vegetation minima. |
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

Every material in `biomeMaterialRules` is mandatory. Custom recipes deliberately replace the automatic habitat plan, including its floor; add a matching `biomeFloorRules` entry to require native ground. Custom base counts run from 1 to 64 for up to 32 distinct block IDs and scale with volume. Duplicate IDs are rejected.

Floor rules use `biome=percentage:block,block`. The percentage runs from 0 to 100; up to 16 listed block IDs are alternatives for that coverage. The last matching entry in each setting wins. Use installed IDs, then restart the world/server after editing.

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

Active cooling thermoregulators supply icy/cold/neutral climates; heating supplies neutral/warm/hot; both supply neutral. Empty, inactive, and redstone-disabled regulators supply none. In greenhouses, these modes condition the shared air gradually. The search covers one block below to three blocks above the plant in loaded chunks. Tough As Nails is optional.

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
