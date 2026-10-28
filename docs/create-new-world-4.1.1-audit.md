# Create New World 4.1.1 crop compatibility audit

Inspection date: 2026-10-17. **Headless farming compatibility is verified** for the crop-mod candidate against both inspected Create New World gameplay sets used to develop 4.1.1: all 31 GameTests passed on each set. The six shared-mod version differences below remain relevant; these results do not certify manual multiplayer, client rendering, physics, or sustained performance.

## Inspected installations

Instance root: the local CurseForge Minecraft `Instances/` directory.

| Source directory | Minecraft | NeoForge | JARs at fixture capture | Current JARs |
| --- | --- | --- | --- | --- |
| `Create New World  A Locked Progression Experience` | 1.21.1 | 21.1.256 | 185 | 185 |
| `Server Pack v4.1.0+` | 1.21.1 | 21.1.259 | 159 | 159 |

Both live inventories gained `player_blocks-0.0.2.jar` after the first 184/158-JAR fixture capture, at 21:04 on the inspection date. The refreshed fixtures include it, and both new irrigation/growth GameTests passed on both gameplay sets. The baseline archive is `Create New World 4.1.0.zip`.

Player Blocks inspection found identical client/server JARs (SHA256 `35BBEFCCA40E71EB21CDA0F2560E501932833F6745F41CC8601418B57EA9EFBD`): 95 classes, 13 declared compatibility mixins, and 348 data files, with no biome definitions, crop rules, growth-event hooks, or regulator hooks. Its custom fluid is tagged as water with `canHydrate(true)`. Runtime tests verified farmland hydration, matching-biome wheat growth/bonemeal, and cold-biome blocking beside that fluid. Its Create/Thirst mixins preserve colored materials, schematic ownership, storage components, and custom liquid buckets.

Shared relevant versions: Create 6.0.10, Integrated Farming 1.4.3, Central Kitchen 2.6.2b, Slice & Dice 4.3.4, Farmer's Delight 1.3.4, Snow! Real Magic! 12.2.3, Biomes O' Plenty 21.1.0.14, TerraBlender 4.1.0.8, Tough As Nails 10.1.0.13, Thirst Was Reclaimed 3.0.6, Thirst Was Upgraded 0.0.5, Create New World Core 0.0.3, Northstar 0.6.6, Spawn 4.0.8, and Quark 4.1-487. The inspected TAN and Core JARs match between client/server; TAN also matches this workspace's bundled test JAR.

The client has 26 additional presentation/diagnostic JARs. Six shared mods also differ in version:

| Mod | Client | Server |
| --- | --- | --- |
| AeroClaims | 0.9.5 | 0.9.4 |
| Gadgets and Gizmos | 2.0 pre-release 3 | 2.0 pre-release 2 |
| Neruina | 3.3.5 | 3.3.4 |
| Open Parties and Claims | 0.33.0 | 0.32.7 |
| Streams Reflowing | 2.15.2 | 2.15.0 |
| Supplementaries | 4.0.2 | 4.0.1 |

## Climate and growth findings

All **175 registered biomes** resolve to exactly one crop climate, with no conflicting shipped TAN climate tags. Counts are Minecraft 64, BOP 69, Northstar 24, Spawn 10, Core 2, and one each from Quark, Create Nuclear, Create Teleporters, Create Abyss, Streams Reflowing, and TerraBlender (including its registry placeholder). BOP enables generation for 65 of its 69 registered biomes; auroral garden, mystic grove, origin valley, and wintry origin valley are disabled by the existing pack settings. Geophilic replaces 29 vanilla biome definitions without changing their temperature or precipitation metadata.

Explicit TAN biome climate tags take precedence over snow tags and base temperature. Core replaces the pack's climate tags: ordinary deserts and Nether biomes resolve **warm**, Deep Freeze resolves **cold**, and Scorched Earth resolves **hot**. The crop mod preserves these replacements. It keeps the existing **hot-only** preferences for cactus, nether wart, crimson fungus, and warped fungus; their warm native biomes therefore require a suitable active regulator or a changed crop rule.

The candidate adds warm compatibility to BOP pine, dead, and hellbark saplings so they can grow in Core-remapped dryland, wasteland, and undergrowth. Hellbark now accepts `warm,hot`; it is not one of the preserved hot-only gates. Plant defaults also cover Quark, Spawn, and Northstar plants identified during inspection.

No season mod was found in the inspected inventories. Classification uses biome tags/base temperature, without seasonal, altitude, weather, shade, or planetary HUD temperature changes. Core's player-temperature presets and Thirst Was Upgraded's drinking/backpack hooks do not change crop biome classification. A glass roof alone supplies no greenhouse override.

Northstar's martian peaks resolve icy through their snow tag, while its mercury icy caverns and venus lava caves resolve neutral from the actual biome data. Spawn's cold island also resolves neutral. Those names do not supply climate tags; the tests verify the loaded metadata rather than inferring temperature from names.

Regulator compatibility reads actual registered TAN state properties and active fuel timers. Cooling supports icy/cold/neutral, heating supports neutral/warm/hot, and both modes support neutral. Inactive or redstone-disabled machines supply no climate. All nearby candidates are considered, using loaded chunks only. Core's additional fuels work through TAN's normal fuel tags. Disabling TAN player temperature does not disable its regulator ticker.

Natural ticks are checked before growth. Separate fertilizer handling covers ordinary bonemeal, Create tree fertilizer, and Slice & Dice sprinkler targets. Snow! Real Magic! wrappers resolve their contained plants for fertilizer/soil-boost checks while retaining snow melting behavior. Other growth APIs that bypass these paths need separate integration.

## Configuration migration

The installed `config/biome_based_crops-common.toml` matches the baseline archive's unchanged defaults. The candidate splits it into:

- Global synchronized gameplay: `config/biome_based_crops-server.toml`.
- Optional world-specific override: `<world>/serverconfig/biome_based_crops-server.toml`.
- Local tooltip preference: `config/biome_based_crops-client.toml`.

Ship the new server/client files for 4.1.1. Transfer intentional gameplay edits into the server file and `enableTooltips` into the client file. Use the corrected new crop-rule defaults instead of copying the entire old rule list, which would retain earlier sapling/plant mismatches. Migration does not automatically rewrite old configs or worlds.

## Verification scope and release follow-up

[Fixture preparation](../tools/prepare-pack-tests.ps1) uses [bundled audit rules](../tools/pack-test-fixture-rules.json), without a sibling build-cache dependency. Each refreshed gameplay fixture contains 158 JARs: the installed crop JAR is omitted because Gradle supplies the candidate sources, and the client fixture also omits the 26 presentation JARs. Each fixture retains installed Core 0.0.3 and Player Blocks 0.0.2 and copies 157 gameplay config files. Existing runtime-generated configs are preserved and recorded; stale generated crop-server config was archived and reset so current candidate defaults regenerate. Source instances and saved worlds remain unchanged. Preparation rejects unexpected mod inventories.

The [server](../build/compat/server/manifest.json) and [client](../build/compat/client/manifest.json) manifests record complete hashes. Each has 156 unchanged whole-JAR matches and two audited test-only filters. The isolated Central Kitchen copy removes only nine bundled optional-integration GameTest classes plus their directory; all 404 retained ZIP entries match their source bytes. Neruina changes only its mixin configuration as described below. Excluded client JARs contain no crop/biome data; their sole data resource is a spyglass zoom tag.

The harness supplies an offline profile cache for OpenPAC on GameTest servers, URI-form Log4j configuration properties for Numismatics, and scoped AStages permissions for automation-test players. Debugger evidence showed that a progression-locked test player generated 65 tree block snapshots and received a successful tree-fertilizer result before AStages' `ItemServerEvents.onBlockPlaced` rolled placement back. The harness grants the required stages only for each test and restores the exact original stage IDs afterward; both Create positive controls now pass. These accommodations and GameTest classes are excluded from the release mod. Headless tests exercise gameplay registries, growth, automation, snow wrappers, and actual TAN fuel/redstone ticks; they do not establish client rendering, cross-version multiplayer, physics stability, or long-running performance.

Neruina 3.3.4/3.3.5 includes a `@DevOnly` FarmBlock mixin that deliberately throws an empty RuntimeException at the start of every farmland random tick. Its plugin enables it whenever FML is not in production; there is no separate disable property. Debugger evidence traced both initial Player Blocks failures to that injected crash. The isolated Neruina copy removes only `test.FarmBlockMixin` from `neruina.mixins.json`, matching its production-disabled behavior for the real hydration test. All 136 ZIP entries remain; the other 135 entries, including every class file and production crash catcher, are byte-identical. Other developer-test mixin registrations remain unchanged. Installed Neruina JARs are untouched.

Final pack runs passed all 31 GameTests with no optional skips: server NeoForge 21.1.259 at 21:21:46 and client-gameplay NeoForge 21.1.256 at 21:22:25 on the inspection date. This includes both Player Blocks checks, both Create positive controls, and all seven pack-climate checks. Evidence: [server log](../build/reports/create-new-world-server-gametest.log) and [client-gameplay log](../build/reports/create-new-world-client-gameplay-gametest.log).

| Verification | Result |
| --- | --- |
| Captured server gameplay set, NeoForge 21.1.259 | 31/31 GameTests passed; no optional skips |
| Captured client gameplay set, headless, NeoForge 21.1.256 | 31/31 GameTests passed; no optional skips |
| Production and standalone JUnit, NeoForge 21.1.251 | Each passed: 43 total, 35 passed, 8 unavailable optional checks skipped, zero failures/errors; [validation summary](../build/reports/final-validation-summary.json) |
| Standalone GameTest suite without local optional mods | Passed: 10 vanilla behavior checks ran; 21 unavailable optional checks skipped |
| Production classes against both tested pack profiles | All 23 main class files byte-identical; [comparison](../build/reports/production-class-comparison.json) |
| Release JAR contents | 36 entries; zero GameTest, profile, or test-helper classes |
| Final live-source verification | All 344 JARs and 157 unique config sources match their captured hashes; [verification](../build/reports/live-inventory-verification.json) |
| Manual multiplayer, rendering, physics, sustained performance | Outside the verified headless farming scope |

No live instance JARs, configs, or worlds were edited. Revalidate if release inventories or gameplay configs change. Core 0.0.3's survival guide still promises fuel-free neutral crop support; update that wording for the active-regulator requirement. BOP's existing mob-spawn diagnostics are upstream of this crop mod and were not changed here.
