package com.ourcreation.biomebasedcrops;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Keeps connected clients' climate tooltips in step with command changes. */
public record GrowthSettingsPayload(boolean cropsEnabled, boolean treesEnabled) implements CustomPacketPayload {
    public static final Type<GrowthSettingsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BiomeBasedCrops.MODID, "growth_settings"));
    public static final StreamCodec<FriendlyByteBuf, GrowthSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GrowthSettingsPayload::cropsEnabled,
            ByteBufCodecs.BOOL, GrowthSettingsPayload::treesEnabled,
            GrowthSettingsPayload::new);

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        // Existing clients can still connect; only clients with this channel receive live updates.
        event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC, GrowthSettingsPayload::handle);
    }

    public static void syncToPlayers(MinecraftServer server) {
        if (!Config.SPEC.isLoaded()) {
            return;
        }
        GrowthSettingsPayload payload = new GrowthSettingsPayload(
                Config.ENABLE_CROP_BIOME_DEPENDENCIES.get(), Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get());
        for (var player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(TYPE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void handle(GrowthSettingsPayload payload, IPayloadContext context) {
        // The local host already shares the authoritative config with its server.
        // Applying queued snapshots there could overwrite a newer server-side change.
        if (context.connection().isMemoryConnection() || !Config.SPEC.isLoaded()) {
            return;
        }
        // The registrar runs this on the main thread. Remote SERVER configs are in memory;
        // never save them to the player's local config files.
        Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(payload.cropsEnabled());
        Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(payload.treesEnabled());
    }

    @Override
    public Type<GrowthSettingsPayload> type() {
        return TYPE;
    }
}
