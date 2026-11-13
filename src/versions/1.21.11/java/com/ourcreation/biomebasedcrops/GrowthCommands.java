package com.ourcreation.biomebasedcrops;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** World-wide switches: natural growth has no owning player to apply a personal preference to. */
public final class GrowthCommands {
    private static final SimpleCommandExceptionType CONFIG_NOT_LOADED = new SimpleCommandExceptionType(
            Component.translatable("biome_based_crops.command.config_not_loaded"));
    private static final SimpleCommandExceptionType SAVE_FAILED = new SimpleCommandExceptionType(
            Component.translatable("biome_based_crops.command.save_failed"));

    private GrowthCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("biomebasedcrops")
                .executes(context -> status(context.getSource(), Target.ALL))
                .then(Commands.literal("status").executes(context -> status(context.getSource(), Target.ALL)))
                .then(changeCommand("enable", Target.ALL, true))
                .then(changeCommand("disable", Target.ALL, false))
                .then(categoryCommand("crops", Target.CROPS))
                .then(categoryCommand("trees", Target.TREES)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> categoryCommand(String name, Target target) {
        return Commands.literal(name)
                .executes(context -> status(context.getSource(), target))
                .then(Commands.literal("status").executes(context -> status(context.getSource(), target)))
                .then(changeCommand("enable", target, true))
                .then(changeCommand("disable", target, false));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> changeCommand(String name, Target target, boolean enabled) {
        return Commands.literal(name)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(context -> change(context.getSource(), target, enabled));
    }

    private static int status(CommandSourceStack source, Target target) throws CommandSyntaxException {
        requireConfig();
        source.sendSuccess(() -> target == Target.ALL
                ? Component.translatable("biome_based_crops.command.status",
                        stateComponent(Config.ENABLE_CROP_BIOME_DEPENDENCIES.get()),
                        stateComponent(Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get()))
                : Component.translatable("biome_based_crops.command.category_status", targetComponent(target),
                        stateComponent(target == Target.CROPS ? Config.ENABLE_CROP_BIOME_DEPENDENCIES.get()
                                : Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get())), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int change(CommandSourceStack source, Target target, boolean enabled) throws CommandSyntaxException {
        requireConfig();
        boolean previousCrops = Config.ENABLE_CROP_BIOME_DEPENDENCIES.get();
        boolean previousTrees = Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.get();
        if (target != Target.TREES) {
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(enabled);
        }
        if (target != Target.CROPS) {
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(enabled);
        }

        try {
            // Saves the active server/world config and fires the cache-invalidation event.
            Config.SPEC.save();
        } catch (RuntimeException exception) {
            Config.ENABLE_CROP_BIOME_DEPENDENCIES.set(previousCrops);
            Config.ENABLE_SAPLING_BIOME_DEPENDENCIES.set(previousTrees);
            BiomeBasedCrops.LOGGER.error("Could not save biome growth settings", exception);
            throw SAVE_FAILED.create();
        }

        GrowthSettingsPayload.syncToPlayers(source.getServer());
        source.sendSuccess(() -> Component.translatable("biome_based_crops.command.changed",
                targetComponent(target), stateComponent(enabled)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static void requireConfig() throws CommandSyntaxException {
        if (!Config.SPEC.isLoaded()) {
            throw CONFIG_NOT_LOADED.create();
        }
    }

    private static Component stateComponent(boolean enabled) {
        return Component.translatable("biome_based_crops.command." + (enabled ? "enabled" : "disabled"));
    }

    private static Component targetComponent(Target target) {
        return Component.translatable("biome_based_crops.command.target." + target.id);
    }

    private enum Target {
        ALL("all"), CROPS("crops"), TREES("trees");

        private final String id;

        Target(String id) {
            this.id = id;
        }
    }
}
