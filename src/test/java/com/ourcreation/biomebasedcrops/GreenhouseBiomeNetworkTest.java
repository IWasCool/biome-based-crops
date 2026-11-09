package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GreenhouseBiomeNetworkTest {
    private static final ResourceLocation OVERWORLD = id("minecraft:overworld");
    private static final ResourceLocation DESERT = id("minecraft:desert");

    @Test
    void enormousRoomSendsOnly27NearbySectionsAndFullSectionsCompressUnder300Bytes() {
        LongOpenHashSet geometry = new LongOpenHashSet(900_000);
        for (int x = -48; x < 48; x++) for (int y = 16; y < 112; y++) for (int z = -48; z < 48; z++) {
            geometry.add(BlockPos.asLong(x, y, z));
        }
        assertEquals(884_736, geometry.size());
        BlockPos player = new BlockPos(0, 64, 0);
        var masks = GreenhouseBiomeNetwork.masksFor(geometry, player);
        assertEquals(27, masks.size());
        assertTrue(masks.stream().allMatch(GreenhouseBiomeNetwork.SectionMask::full));
        var payload = new GreenhouseBiomeNetwork.RoomPayload(OVERWORLD, DESERT, masks);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GreenhouseBiomeNetwork.RoomPayload.CODEC.encode(buffer, payload);
            assertTrue(buffer.readableBytes() < 300, "Full section regions should not serialize all block coordinates");
            assertEquals(payload, GreenhouseBiomeNetwork.RoomPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        try {
            GreenhouseClientBiomes.accept(payload);
            assertEquals(DESERT, GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(-16, 48, -16)));
            assertEquals(DESERT, GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(31, 95, 31)));
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(32, 64, 0)), "Client must not invent coverage beyond its synced section window");
        } finally { GreenhouseClientBiomes.clear(); }
    }

    @Test
    void sparseMasksHandleNegativeEdgesAndRemainExactWithinTheSameSection() {
        Set<Long> geometry = Set.of(new BlockPos(-1, -1, -1).asLong(), BlockPos.ZERO.asLong(),
                new BlockPos(16, 16, 16).asLong(), new BlockPos(32, 0, 0).asLong());
        var payload = new GreenhouseBiomeNetwork.RoomPayload(OVERWORLD, DESERT,
                GreenhouseBiomeNetwork.masksFor(geometry, BlockPos.ZERO));
        assertEquals(3, payload.sections().size());
        assertEquals(payload, roundTrip(payload));
        try {
            GreenhouseClientBiomes.accept(roundTrip(payload));
            assertEquals(DESERT, GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(-1, -1, -1)));
            assertEquals(DESERT, GreenhouseClientBiomes.biomeIdAt(OVERWORLD, BlockPos.ZERO));
            assertEquals(DESERT, GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(16, 16, 16)));
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, BlockPos.ZERO.east()));
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(-2, -1, -1)));
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(32, 0, 0)));
            assertNull(GreenhouseClientBiomes.biomeIdAt(id("minecraft:the_nether"), BlockPos.ZERO));
            GreenhouseClientBiomes.accept(roundTrip(new GreenhouseBiomeNetwork.RoomPayload(OVERWORLD, null, List.of())));
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, BlockPos.ZERO));
            GreenhouseClientBiomes.accept(payload);
            GreenhouseClientBiomes.clear();
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, BlockPos.ZERO));
        } finally { GreenhouseClientBiomes.clear(); }
    }

    @Test
    void movingTheSectionWindowIncludesTheNewAreaWithoutSendingTheWholeRoom() {
        LongOpenHashSet geometry = new LongOpenHashSet();
        for (int x = 0; x < 80; x++) geometry.add(BlockPos.asLong(x, 64, 0));
        var first = new GreenhouseBiomeNetwork.RoomPayload(OVERWORLD, DESERT,
                GreenhouseBiomeNetwork.masksFor(geometry, new BlockPos(0, 64, 0)));
        var next = new GreenhouseBiomeNetwork.RoomPayload(OVERWORLD, DESERT,
                GreenhouseBiomeNetwork.masksFor(geometry, new BlockPos(32, 64, 0)));
        try {
            GreenhouseClientBiomes.accept(first);
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(40, 64, 0)));
            GreenhouseClientBiomes.accept(roundTrip(next));
            assertEquals(DESERT, GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(40, 64, 0)));
            assertNull(GreenhouseClientBiomes.biomeIdAt(OVERWORLD, new BlockPos(0, 64, 0)));
        } finally { GreenhouseClientBiomes.clear(); }
    }

    @Test
    void masksAndPayloadListsCannotBeMutatedThroughCallerArrays() {
        long[] bits = new long[64];
        bits[0] = 1;
        var mask = new GreenhouseBiomeNetwork.SectionMask(SectionPos.asLong(0, 0, 0), bits);
        bits[0] = 0;
        assertTrue(mask.contains(BlockPos.ZERO));
        mask.bits()[0] = 0;
        assertTrue(mask.contains(BlockPos.ZERO));
        assertFalse(mask.contains(BlockPos.ZERO.east()));
        var payload = new GreenhouseBiomeNetwork.RoomPayload(OVERWORLD, DESERT, List.of(mask));
        assertThrows(UnsupportedOperationException.class, () -> payload.sections().clear());
        assertThrows(IllegalArgumentException.class, () -> new GreenhouseBiomeNetwork.SectionMask(0, new long[63]));
        assertThrows(IllegalArgumentException.class, () -> new GreenhouseBiomeNetwork.SectionMask(0, new long[64]));
    }

    @Test
    void decoderRejectsOversizedDuplicateAndMalformedSectionsBeforeUse() {
        reject(buffer -> header(buffer, 28));
        reject(buffer -> {
            header(buffer, 2);
            full(buffer, SectionPos.asLong(0, 0, 0));
            full(buffer, SectionPos.asLong(0, 0, 0));
        });
        reject(buffer -> {
            header(buffer, 1);
            partial(buffer, 65);
        });
        reject(buffer -> {
            header(buffer, 1);
            partial(buffer, 1);
            buffer.writeByte(64); buffer.writeLong(1);
        });
        reject(buffer -> {
            header(buffer, 1);
            partial(buffer, 2);
            buffer.writeByte(0); buffer.writeLong(1);
            buffer.writeByte(0); buffer.writeLong(2);
        });
        reject(buffer -> {
            header(buffer, 1);
            partial(buffer, 1);
            buffer.writeByte(0); buffer.writeLong(0);
        });
        reject(buffer -> {
            header(buffer, 2);
            full(buffer, SectionPos.asLong(0, 0, 0));
            full(buffer, SectionPos.asLong(3, 0, 0));
        });
        reject(buffer -> {
            buffer.writeResourceLocation(OVERWORLD);
            buffer.writeNullable(null, FriendlyByteBuf::writeResourceLocation);
            buffer.writeVarInt(1);
            full(buffer, 0);
        });
        reject(buffer -> header(buffer, 0));
    }

    private static void header(FriendlyByteBuf buffer, int count) {
        buffer.writeResourceLocation(OVERWORLD);
        buffer.writeNullable(DESERT, FriendlyByteBuf::writeResourceLocation);
        buffer.writeVarInt(count);
    }

    private static void full(FriendlyByteBuf buffer, long section) {
        buffer.writeLong(section);
        buffer.writeBoolean(true);
    }

    private static void partial(FriendlyByteBuf buffer, int words) {
        buffer.writeLong(SectionPos.asLong(0, 0, 0));
        buffer.writeBoolean(false);
        buffer.writeVarInt(words);
    }

    private static void reject(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            assertThrows(IllegalArgumentException.class, () -> GreenhouseBiomeNetwork.RoomPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

    private static GreenhouseBiomeNetwork.RoomPayload roundTrip(GreenhouseBiomeNetwork.RoomPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GreenhouseBiomeNetwork.RoomPayload.CODEC.encode(buffer, payload);
            var decoded = GreenhouseBiomeNetwork.RoomPayload.CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally { buffer.release(); }
    }

    private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
