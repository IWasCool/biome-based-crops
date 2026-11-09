package com.ourcreation.biomebasedcrops;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class GreenhouseBiomeNetwork {
    private static final Map<ServerPlayer, RoomPayload> LAST_SENT = new WeakHashMap<>();
    private static final int MAX_INTERIOR = 13 * 13 * 5;

    private GreenhouseBiomeNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(RoomPayload.TYPE, RoomPayload.CODEC,
                (payload, context) -> GreenhouseClientBiomes.accept(payload));
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || !player.connection.hasChannel(RoomPayload.TYPE)) return;
        var region = GreenhouseEnvironment.regionAt(player.level(), player.blockPosition());
        RoomPayload payload = new RoomPayload(player.level().dimension().location(),
                region == null ? null : region.biome(), region == null ? Set.of() : region.interior());
        if (!payload.equals(LAST_SENT.put(player, payload))) PacketDistributor.sendToPlayer(player, payload);
    }

    public record RoomPayload(ResourceLocation dimension, @Nullable ResourceLocation biome,
                              Set<Long> interior) implements CustomPacketPayload {
        public static final Type<RoomPayload> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "greenhouse_biome"));
        public static final StreamCodec<FriendlyByteBuf, RoomPayload> CODEC =
                StreamCodec.of(RoomPayload::write, RoomPayload::read);

        public RoomPayload {
            interior = Set.copyOf(interior);
            if (interior.size() > MAX_INTERIOR || (biome == null) != interior.isEmpty()) {
                throw new IllegalArgumentException("Invalid greenhouse biome region");
            }
        }

        private static void write(FriendlyByteBuf buf, RoomPayload payload) {
            buf.writeResourceLocation(payload.dimension);
            buf.writeNullable(payload.biome, FriendlyByteBuf::writeResourceLocation);
            buf.writeVarInt(payload.interior.size());
            payload.interior.forEach(buf::writeLong);
        }

        private static RoomPayload read(FriendlyByteBuf buf) {
            ResourceLocation dimension = buf.readResourceLocation();
            ResourceLocation biome = buf.readNullable(FriendlyByteBuf::readResourceLocation);
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_INTERIOR) throw new IllegalArgumentException("Invalid greenhouse biome region size");
            Set<Long> interior = new HashSet<>();
            for (int i = 0; i < size; i++) {
                if (!interior.add(buf.readLong())) throw new IllegalArgumentException("Duplicate greenhouse biome position");
            }
            return new RoomPayload(dimension, biome, interior);
        }

        @Override public Type<RoomPayload> type() {
            return TYPE;
        }
    }
}
