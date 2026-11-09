package com.ourcreation.biomebasedcrops;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GreenhouseBiomeNetworkTest {
    @Test
    void maximumRoomAndLeavingRoomSurviveNetworkRoundTrip() {
        Set<Long> interior = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(-6, -20, -6), new BlockPos(6, -16, 6))) {
            interior.add(pos.asLong());
        }
        assertEquals(845, interior.size());
        var room = new GreenhouseBiomeNetwork.RoomPayload(id("minecraft:overworld"), id("minecraft:desert"), interior);
        assertEquals(room, roundTrip(room));
        var outside = new GreenhouseBiomeNetwork.RoomPayload(id("minecraft:overworld"), null, Set.of());
        assertEquals(outside, roundTrip(outside));
    }

    @Test
    void oversizedAndDuplicatePositionsAreRejectedBeforeUse() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(id("minecraft:overworld"));
            buffer.writeNullable(id("minecraft:desert"), FriendlyByteBuf::writeResourceLocation);
            int sizeOffset = buffer.writerIndex();
            buffer.writeVarInt(846);
            assertThrows(IllegalArgumentException.class, () -> GreenhouseBiomeNetwork.RoomPayload.CODEC.decode(buffer));
            buffer.readerIndex(0);
            buffer.writerIndex(sizeOffset);
            buffer.writeVarInt(2);
            buffer.writeLong(BlockPos.ZERO.asLong());
            buffer.writeLong(BlockPos.ZERO.asLong());
            assertThrows(IllegalArgumentException.class, () -> GreenhouseBiomeNetwork.RoomPayload.CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void clientBiomeIsExactPositionScopedAndClearsOnExitOrDisconnect() {
        var dimension = id("minecraft:overworld");
        var desert = id("minecraft:desert");
        BlockPos pos = new BlockPos(3, 60, 3);
        try {
            GreenhouseClientBiomes.accept(new GreenhouseBiomeNetwork.RoomPayload(dimension,
                    id("minecraft:desert"), Set.of(pos.asLong())));
            assertEquals(desert, GreenhouseClientBiomes.biomeIdAt(dimension, pos));
            assertNull(GreenhouseClientBiomes.biomeIdAt(dimension, pos.east()));
            GreenhouseClientBiomes.accept(new GreenhouseBiomeNetwork.RoomPayload(id("minecraft:the_nether"),
                    id("minecraft:desert"), Set.of(pos.asLong())));
            assertNull(GreenhouseClientBiomes.biomeIdAt(dimension, pos));
            GreenhouseClientBiomes.accept(new GreenhouseBiomeNetwork.RoomPayload(dimension, null, Set.of()));
            assertNull(GreenhouseClientBiomes.biomeIdAt(dimension, pos));
            GreenhouseClientBiomes.accept(new GreenhouseBiomeNetwork.RoomPayload(dimension,
                    id("minecraft:desert"), Set.of(pos.asLong())));
            GreenhouseClientBiomes.clear();
            assertNull(GreenhouseClientBiomes.biomeIdAt(dimension, pos));
        } finally {
            GreenhouseClientBiomes.clear();
        }
    }

    private static GreenhouseBiomeNetwork.RoomPayload roundTrip(GreenhouseBiomeNetwork.RoomPayload payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GreenhouseBiomeNetwork.RoomPayload.CODEC.encode(buffer, payload);
            var decoded = GreenhouseBiomeNetwork.RoomPayload.CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
