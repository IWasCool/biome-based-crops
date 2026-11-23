package com.ourcreation.biomebasedcrops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class GreenhouseBiomeNetwork {
    public static final int MAX_SECTIONS = 27;
    private static final int SMALL_ROOM = 8_192;
    private static final Map<ServerPlayer, SentRegion> LAST_SENT = new WeakHashMap<>();

    private GreenhouseBiomeNetwork() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("5").optional().playToClient(RoomPayload.TYPE, RoomPayload.CODEC,
                (payload, context) -> GreenhouseClientBiomes.accept(payload));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || !player.connection.hasChannel(RoomPayload.TYPE)) return;
        BlockPos sample = ShipGreenhouseQueries.knownRoomPosition(player);
        var region = GreenhouseEnvironment.atmosphereRegionAt(player.level(), sample);
        SentRegion next = snapshotFor(player.level(), sample, region);
        SentRegion last = LAST_SENT.get(player);
        if (last != null && last.geometry == next.geometry && last.playerSection == next.playerSection
                && last.dimension.equals(next.dimension) && Objects.equals(last.shipId, next.shipId)
                && Objects.equals(last.biome, next.biome) && last.climate == next.climate
                && last.oxygen == next.oxygen && last.sections.equals(next.sections)) return;
        LAST_SENT.put(player, next);
        PacketDistributor.sendToPlayer(player, next.payload());
    }

    static SentRegion snapshotFor(Level level, BlockPos sample, @Nullable GreenhouseEnvironment.AtmosphereRegion region) {
        ResourceLocation dimension = level.dimension().location();
        // The region resolves the final sample cell in the same coordinate space as its interior.
        if (region != null) {
            BlockPos center = region.position();
            List<SectionMask> sections = masksFor(region.interior(), center, region.coverage());
            if (!sections.isEmpty()) {
                float oxygen = (float) Math.floor(region.oxygenPercent() * 10) / 10.0F;
                return new SentRegion(dimension, region.biome(), sectionKey(center), region.interior(),
                        sections, region.climate(), oxygen, ShipCropEnvironment.spaceId(level, center));
            }
        }
        return new SentRegion(dimension, null, sectionKey(sample), Set.of(), List.of(), CropClimate.NEUTRAL, 100, null);
    }

    /** Build only the player's section and its 26 neighbors, never the entire barn. */
    static List<SectionMask> masksFor(Set<Long> geometry, BlockPos player) {
        return masksFor(geometry, player, unused -> true);
    }

    static List<SectionMask> masksFor(Set<Long> geometry, BlockPos player, java.util.function.Predicate<BlockPos> coverage) {
        Long2ObjectOpenHashMap<long[]> masks = new Long2ObjectOpenHashMap<>();
        int centerX = player.getX() >> 4, centerY = player.getY() >> 4, centerZ = player.getZ() >> 4;
        if (geometry.size() < SMALL_ROOM) {
            if (geometry instanceof LongSet primitive) {
                var positions = primitive.iterator();
                while (positions.hasNext()) {
                    long packed = positions.nextLong();
                    addLocalPosition(masks, packed, centerX, centerY, centerZ, coverage);
                }
            } else {
                for (long packed : geometry) addLocalPosition(masks, packed, centerX, centerY, centerZ, coverage);
            }
        } else {
            LongSet primitive = geometry instanceof LongSet values ? values : null;
            for (int sx = centerX - 1; sx <= centerX + 1; sx++) {
                for (int sy = centerY - 1; sy <= centerY + 1; sy++) {
                    for (int sz = centerZ - 1; sz <= centerZ + 1; sz++) {
                        long[] words = new long[64];
                        boolean any = false;
                        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                            long position = BlockPos.asLong((sx << 4) + x, (sy << 4) + y, (sz << 4) + z);
                            if ((primitive != null ? primitive.contains(position) : geometry.contains(position)) && coverage.test(BlockPos.of(position))) {
                                int index = (y << 8) | (z << 4) | x;
                                words[index >>> 6] |= 1L << (index & 63);
                                any = true;
                            }
                        }
                        if (any) masks.put(SectionPos.asLong(sx, sy, sz), words);
                    }
                }
            }
        }
        List<SectionMask> sections = new ArrayList<>(masks.size());
        masks.long2ObjectEntrySet().forEach(entry -> sections.add(new SectionMask(entry.getLongKey(), entry.getValue())));
        sections.sort(java.util.Comparator.comparingLong(SectionMask::sectionKey));
        return List.copyOf(sections);
    }

    private static void addLocalPosition(Long2ObjectOpenHashMap<long[]> masks, long packed, int centerX, int centerY, int centerZ,
                                         java.util.function.Predicate<BlockPos> coverage) {
        int x = BlockPos.getX(packed), y = BlockPos.getY(packed), z = BlockPos.getZ(packed);
        int sx = x >> 4, sy = y >> 4, sz = z >> 4;
        if (Math.abs(sx - centerX) > 1 || Math.abs(sy - centerY) > 1 || Math.abs(sz - centerZ) > 1) return;
        // Coverage may query local controllers. Cells outside the transmitted window need no such work.
        if (!coverage.test(BlockPos.of(packed))) return;
        long section = SectionPos.asLong(sx, sy, sz);
        long[] words = masks.get(section);
        if (words == null) { words = new long[64]; masks.put(section, words); }
        int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        words[index >>> 6] |= 1L << (index & 63);
    }

    static long sectionKey(BlockPos pos) {
        return SectionPos.asLong(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
    }

    public record SectionMask(long sectionKey, long[] bits) {
        public SectionMask {
            if (bits.length != 64) throw new IllegalArgumentException("A section needs 64 mask words");
            bits = bits.clone();
            boolean any = false;
            for (long word : bits) any |= word != 0;
            if (!any) throw new IllegalArgumentException("Empty greenhouse section");
        }
        @Override public long[] bits() { return bits.clone(); }
        public boolean contains(BlockPos pos) {
            if (sectionKey != GreenhouseBiomeNetwork.sectionKey(pos)) return false;
            int index = ((pos.getY() & 15) << 8) | ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
            return (bits[index >>> 6] & (1L << (index & 63))) != 0;
        }
        boolean full() {
            for (long word : bits) if (word != -1L) return false;
            return true;
        }
        @Override public boolean equals(Object other) {
            return other instanceof SectionMask mask && sectionKey == mask.sectionKey && Arrays.equals(bits, mask.bits);
        }
        @Override public int hashCode() { return 31 * Long.hashCode(sectionKey) + Arrays.hashCode(bits); }
    }

    public record RoomPayload(ResourceLocation dimension, @Nullable ResourceLocation biome,
                              List<SectionMask> sections, CropClimate climate, float oxygenPercent,
                              @Nullable UUID shipId) implements CustomPacketPayload {
        public static final Type<RoomPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "greenhouse_biome"));
        public static final StreamCodec<FriendlyByteBuf, RoomPayload> CODEC = StreamCodec.of(RoomPayload::write, RoomPayload::read);
        public RoomPayload {
            Objects.requireNonNull(dimension, "dimension");
            sections = List.copyOf(sections);
            if (sections.size() > MAX_SECTIONS || biome != null && sections.isEmpty()) {
                throw new IllegalArgumentException("Invalid greenhouse biome section count");
            }
            if (shipId != null && sections.isEmpty()) throw new IllegalArgumentException("Ship atmosphere needs local room sections");
            Objects.requireNonNull(climate, "climate");
            if (!Float.isFinite(oxygenPercent) || oxygenPercent < 0 || oxygenPercent > 100)
                throw new IllegalArgumentException("Invalid room atmosphere");
            Set<Long> unique = new HashSet<>();
            int minX = Integer.MAX_VALUE, minY = minX, minZ = minX;
            int maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxX;
            for (SectionMask section : sections) {
                if (!unique.add(section.sectionKey)) throw new IllegalArgumentException("Duplicate greenhouse section");
                int x = SectionPos.x(section.sectionKey), y = SectionPos.y(section.sectionKey), z = SectionPos.z(section.sectionKey);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
            if (!sections.isEmpty() && (maxX - minX > 2 || maxY - minY > 2 || maxZ - minZ > 2)) {
                throw new IllegalArgumentException("Greenhouse sections exceed the local window");
            }
        }
        public RoomPayload(ResourceLocation dimension, @Nullable ResourceLocation biome, List<SectionMask> sections) {
            this(dimension, biome, sections, CropClimate.NEUTRAL, 100);
        }
        public RoomPayload(ResourceLocation dimension, @Nullable ResourceLocation biome, List<SectionMask> sections,
                           CropClimate climate, float oxygenPercent) {
            this(dimension, biome, sections, climate, oxygenPercent, null);
        }
        private static void write(FriendlyByteBuf buf, RoomPayload payload) {
            buf.writeResourceLocation(payload.dimension);
            buf.writeNullable(payload.biome, FriendlyByteBuf::writeResourceLocation);
            buf.writeVarInt(payload.sections.size());
            for (SectionMask section : payload.sections) {
                buf.writeLong(section.sectionKey);
                boolean full = section.full();
                buf.writeBoolean(full);
                if (full) continue;
                int count = 0;
                for (long word : section.bits) if (word != 0) count++;
                buf.writeVarInt(count);
                for (int i = 0; i < 64; i++) if (section.bits[i] != 0) {
                    buf.writeByte(i);
                    buf.writeLong(section.bits[i]);
                }
            }
            buf.writeEnum(payload.climate);
            buf.writeFloat(payload.oxygenPercent);
            buf.writeNullable(payload.shipId, (buffer, id) -> buffer.writeUUID(id));
        }
        private static RoomPayload read(FriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            ResourceLocation biome = buf.readNullable(FriendlyByteBuf::readResourceLocation);
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_SECTIONS) throw new IllegalArgumentException("Invalid greenhouse section count");
            List<SectionMask> sections = new ArrayList<>(size);
            Set<Long> unique = new HashSet<>();
            for (int section = 0; section < size; section++) {
                long key = buf.readLong();
                if (!unique.add(key)) throw new IllegalArgumentException("Duplicate greenhouse section");
                long[] words = new long[64];
                if (buf.readBoolean()) Arrays.fill(words, -1L);
                else {
                    int count = buf.readVarInt();
                    if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid greenhouse mask word count");
                    for (int word = 0; word < count; word++) {
                        int index = buf.readUnsignedByte();
                        long value = buf.readLong();
                        if (index >= 64 || value == 0 || words[index] != 0) throw new IllegalArgumentException("Invalid or duplicate greenhouse mask word");
                        words[index] = value;
                    }
                }
                sections.add(new SectionMask(key, words));
            }
            int climate = buf.readVarInt();
            if (climate < 0 || climate >= CropClimate.values().length) throw new IllegalArgumentException("Invalid room climate");
            return new RoomPayload(dimension, biome, sections, CropClimate.values()[climate], buf.readFloat(),
                    buf.readNullable(buffer -> buffer.readUUID()));
        }
        @Override public Type<RoomPayload> type() { return TYPE; }
    }

    record SentRegion(ResourceLocation dimension, ResourceLocation biome, long playerSection, Set<Long> geometry,
                      List<SectionMask> sections, CropClimate climate, float oxygen, UUID shipId) {
        RoomPayload payload() { return new RoomPayload(dimension, biome, sections, climate, oxygen, shipId); }
    }
}
