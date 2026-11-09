package com.ourcreation.biomebasedcrops;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.SoulSandBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;

public final class GreenhouseEnvironment {
    private static final TagKey<Block> HEAT = tag("heat_sources");
    private static final TagKey<Block> COOLING = tag("cooling_sources");
    private static final TagKey<Block> GLASS = tag("greenhouse_glass");
    private static final Map<Level, Cache> CACHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final CropClimate[] CLIMATES = CropClimate.values();

    private GreenhouseEnvironment() {
    }

    public static Inspection inspect(Level level, BlockPos pos) {
        if (!loaded(level, pos)) {
            return result(false, null, CropClimate.NEUTRAL, new Temperature(0, 0), 0, 0, List.of(), "unloaded");
        }
        CropClimate nativeClimate = CropClimate.at(level.getBiome(pos), pos);
        if (!GreenhouseSettings.enabled()) {
            Temperature temperature = temperature(level, pos);
            return result(false, null, shifted(nativeClimate, temperature, 2), temperature, 0, 0, List.of(), "disabled");
        }
        GreenhouseCoreBlockEntity core = nearestCore(level, pos);
        if (core == null) {
            Temperature temperature = temperature(level, pos);
            return result(false, null, shifted(nativeClimate, temperature, 2), temperature, 0, 0, List.of(), "no_core");
        }
        Room room = room(level, core);
        if (room.inspection.enclosed() && !pos.equals(core.getBlockPos()) && !room.interior.contains(pos.asLong())) {
            Inspection original = room.inspection;
            return new Inspection(original.enclosed(), original.targetBiome(), effectiveOutdoorClimate(level, pos),
                    original.heat(), original.cooling(), original.glass(), original.specimens(), original.requiredSpecimens(),
                    key("outside"), original.specimenBlocks());
        }
        return room.inspection;
    }

    /** Exact biome rules can only be satisfied by a complete greenhouse. */
    public static boolean canSupport(Level level, BlockPos pos, CropProfile profile) {
        Inspection inspection = inspect(level, pos);
        if (!inspection.valid()) {
            return false;
        }
        return level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(inspection.targetBiome())
                .map(biome -> profile.allows(biome, pos)).orElse(false);
    }

    public static CropClimate effectiveClimate(Level level, BlockPos pos) {
        if (!loaded(level, pos)) return CropClimate.NEUTRAL;
        if (GreenhouseSettings.enabled()) {
            GreenhouseCoreBlockEntity core = nearestCore(level, pos);
            if (core != null) {
                Room room = room(level, core);
                if (room.inspection.enclosed() && (pos.equals(core.getBlockPos()) || room.interior.contains(pos.asLong()))) {
                    return room.inspection.climate();
                }
            }
        }
        return effectiveOutdoorClimate(level, pos);
    }

    public static void clearCache() {
        CACHES.clear();
    }

    private static CropClimate effectiveOutdoorClimate(Level level, BlockPos pos) {
        return shifted(CropClimate.at(level.getBiome(pos), pos), temperature(level, pos), 2);
    }

    private static Room room(Level level, GreenhouseCoreBlockEntity core) {
        Cache cache = cache(level);
        long now = level.getGameTime();
        long key = core.getBlockPos().asLong();
        Room existing = cache.rooms.get(key);
        if (existing != null && now >= existing.tick && now - existing.tick < GreenhouseSettings.cacheTicks()
                && java.util.Objects.equals(existing.inspection.targetBiome(), core.targetBiome())) {
            return existing;
        }
        Room room = scanRoom(level, core, now);
        cache.rooms.put(key, room);
        return room;
    }

    private static Room scanRoom(Level level, GreenhouseCoreBlockEntity core, long tick) {
        BlockPos anchor = core.getBlockPos();
        int radius = GreenhouseSettings.radius();
        Set<Long> interior = new HashSet<>();
        Set<Long> shell = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = anchor.above();
        boolean enclosed = loaded(level, start) && !sealed(level, start);
        if (enclosed) {
            queue.add(start);
            interior.add(start.asLong());
        }
        while (!queue.isEmpty() && enclosed) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!loaded(level, next)) {
                    enclosed = false;
                    break;
                }
                if (sealed(level, next)) {
                    shell.add(next.asLong());
                    continue;
                }
                if (Math.abs(next.getX() - anchor.getX()) > radius
                        || Math.abs(next.getZ() - anchor.getZ()) > radius
                        || next.getY() < anchor.getY() || next.getY() > anchor.getY() + 4) {
                    enclosed = false;
                    break;
                }
                if (interior.add(next.asLong())) {
                    queue.addLast(next);
                }
            }
        }

        ResourceLocation target = core.targetBiome();
        var targetHolder = target == null ? java.util.Optional.<net.minecraft.core.Holder.Reference<net.minecraft.world.level.biome.Biome>>empty()
                : level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(target);
        GreenhouseRecipes.Recipe recipe = targetHolder.map(GreenhouseRecipes::forServerBiome).orElse(null);
        Set<ResourceLocation> specimenIds = recipe == null ? Set.of() : Set.copyOf(recipe.specimens());
        int glass = 0;
        boolean glassRoof = false;
        int specimens = 0;
        Temperature temperature;
        if (enclosed) {
            int heat = 0;
            int cooling = 0;
            Set<Long> observed = new HashSet<>(interior);
            observed.addAll(shell);
            for (long packed : observed) {
                BlockPos at = BlockPos.of(packed);
                BlockState state = level.getBlockState(at);
                if (isGlass(state)) {
                    glass++;
                    if (shell.contains(packed) && interior.contains(at.below().asLong()) && !interior.contains(at.above().asLong())) {
                        glassRoof = true;
                    }
                }
                if (specimenIds.contains(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                    specimens++;
                }
                if (Math.abs(at.getX() - anchor.getX()) <= radius && Math.abs(at.getZ() - anchor.getZ()) <= radius
                        && at.getY() >= anchor.getY() - 1 && at.getY() <= anchor.getY() + 2) {
                    heat += heatStrength(state);
                    cooling += coolingStrength(state);
                }
            }
            temperature = GreenhouseSettings.ambientEnabled() ? new Temperature(heat, cooling) : new Temperature(0, 0);
        } else {
            temperature = temperature(level, anchor);
        }
        CropClimate actual = shifted(CropClimate.at(level.getBiome(anchor), anchor), temperature, enclosed ? 4 : 2);
        String status;
        if (!enclosed) status = "open";
        else if (glass < 4 || !glassRoof) status = "no_glass";
        else if (target == null) status = "unsampled";
        else if (recipe == null) status = "unknown_biome";
        else if (specimens < recipe.count()) status = "missing_specimens";
        else if (actual != recipe.climate() && !ThermoregulatorDetector.canSupportProfile(level, anchor,
                new CropProfile(Set.of(recipe.climate())))) status = "wrong_temperature";
        else status = "valid";
        Inspection inspection = new Inspection(enclosed, target, status.equals("valid") ? recipe.climate() : actual,
                temperature.heat, temperature.cooling, glass,
                specimens, recipe == null ? GreenhouseSettings.requiredSpecimens() : recipe.count(), key(status),
                recipe == null ? List.of() : recipe.specimens());
        return new Room(tick, inspection, Set.copyOf(interior));
    }

    private static GreenhouseCoreBlockEntity nearestCore(Level level, BlockPos pos) {
        int radius = GreenhouseSettings.radius();
        GreenhouseCoreBlockEntity nearest = null;
        GreenhouseCoreBlockEntity connected = null;
        double distance = Double.MAX_VALUE;
        double connectedDistance = Double.MAX_VALUE;
        for (int chunkX = (pos.getX() - radius) >> 4; chunkX <= (pos.getX() + radius) >> 4; chunkX++) {
            for (int chunkZ = (pos.getZ() - radius) >> 4; chunkZ <= (pos.getZ() + radius) >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof GreenhouseCoreBlockEntity core) || core.isRemoved()) continue;
                    BlockPos at = core.getBlockPos();
                    if (Math.abs(at.getX() - pos.getX()) > radius || Math.abs(at.getZ() - pos.getZ()) > radius
                            || pos.getY() < at.getY() || pos.getY() > at.getY() + 4) continue;
                    double candidate = at.distSqr(pos);
                    if (at.equals(pos)) return core;
                    Room room = room(level, core);
                    if (room.inspection.enclosed() && room.interior.contains(pos.asLong()) && candidate < connectedDistance) {
                        connected = core;
                        connectedDistance = candidate;
                    }
                    if (candidate < distance || candidate == distance && at.asLong() < nearest.getBlockPos().asLong()) {
                        distance = candidate;
                        nearest = core;
                    }
                }
            }
        }
        return connected != null ? connected : nearest;
    }

    private static Temperature temperature(Level level, BlockPos pos) {
        if (!GreenhouseSettings.ambientEnabled()) return new Temperature(0, 0);
        Cache cache = cache(level);
        long now = level.getGameTime();
        long key = pos.asLong();
        TimedTemperature cached = cache.temperatures.get(key);
        if (cached != null && now >= cached.tick && now - cached.tick < GreenhouseSettings.cacheTicks()) return cached.temperature;
        int radius = GreenhouseSettings.radius();
        int heat = 0;
        int cooling = 0;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-radius, -1, -radius), pos.offset(radius, 2, radius))) {
            if (loaded(level, at)) {
                BlockState state = level.getBlockState(at);
                heat += heatStrength(state);
                cooling += coolingStrength(state);
            }
        }
        Temperature result = new Temperature(heat, cooling);
        cache.temperatures.put(key, new TimedTemperature(now, result));
        return result;
    }

    static CropClimate shifted(CropClimate original, int heat, int cooling, int cap) {
        int steps = Mth.clamp((heat - cooling) / 8, -cap, cap);
        return CLIMATES[Mth.clamp(original.ordinal() + steps, 0, CLIMATES.length - 1)];
    }

    private static CropClimate shifted(CropClimate original, Temperature temperature, int cap) {
        return shifted(original, temperature.heat, temperature.cooling, cap);
    }

    static int heatStrength(BlockState state) {
        if (state.hasProperty(BlockStateProperties.LIT) && !state.getValue(BlockStateProperties.LIT)) return 0;
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.LAVA)) return 8;
        return state.is(Blocks.MAGMA_BLOCK) || state.is(HEAT) ? 4 : 0;
    }

    static int coolingStrength(BlockState state) {
        if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)) return 8;
        if (state.is(Blocks.SNOW)) return 1;
        return state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(COOLING) ? 4 : 0;
    }

    private static boolean sealed(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(BlockStateProperties.OPEN);
        }
        return isGlass(state) || state.getBlock() instanceof FarmBlock || state.getBlock() instanceof DirtPathBlock
                || state.getBlock() instanceof SoulSandBlock || state.isCollisionShapeFullBlock(level, pos);
    }

    private static boolean isGlass(BlockState state) {
        return state.is(GLASS) || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS);
    }

    private static boolean loaded(Level level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private static Cache cache(Level level) {
        return CACHES.computeIfAbsent(level, unused -> new Cache());
    }

    private static Inspection result(boolean enclosed, ResourceLocation target, CropClimate climate, Temperature temperature,
            int glass, int specimens, List<ResourceLocation> materials, String status) {
        return new Inspection(enclosed, target, climate, temperature.heat, temperature.cooling, glass, specimens,
                GreenhouseSettings.requiredSpecimens(), key(status), materials);
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, path));
    }

    private static String key(String status) {
        return "biome_based_crops.greenhouse.status." + status;
    }

    public record Inspection(boolean enclosed, ResourceLocation targetBiome, CropClimate climate, int heat, int cooling,
            int glass, int specimens, int requiredSpecimens, String status, List<ResourceLocation> specimenBlocks) {
        public Inspection {
            specimenBlocks = List.copyOf(specimenBlocks);
        }

        public boolean valid() {
            return status.equals(key("valid"));
        }
    }

    private record Temperature(int heat, int cooling) {
    }

    private record TimedTemperature(long tick, Temperature temperature) {
    }

    private record Room(long tick, Inspection inspection, Set<Long> interior) {
    }

    private static final class Cache {
        final Map<Long, Room> rooms = boundedMap(256);
        final Map<Long, TimedTemperature> temperatures = boundedMap(4096);
    }

    private static <V> Map<Long, V> boundedMap(int limit) {
        return Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, V> eldest) {
                return size() > limit;
            }
        });
    }
}
