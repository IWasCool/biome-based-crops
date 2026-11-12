package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AirConditionerMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.MENU, BiomeBasedCrops.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<AirConditionerMenu>> TYPE = MENUS.register(
            "air_conditioner", () -> IMenuTypeExtension.create(AirConditionerMenu::new));
    public static final int MIN_TARGET = 0;
    public static final int MAX_TARGET = CropClimate.values().length - 1;
    public static final int TOGGLE_POWER = MAX_TARGET + 1;
    private static final int DATA_COUNT = 27;
    private final BlockPos position;
    private final Level level;
    private final Player owner;
    private final ContainerData data;

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    public AirConditionerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), clientData());
    }

    public AirConditionerMenu(int id, Inventory inventory, AirConditionerBlockEntity unit) {
        this(id, inventory, unit.getBlockPos(), new ContainerData() {
            private long inspectedTick = Long.MIN_VALUE;
            private GreenhouseEnvironment.ControllerRoomInfo frontRoom;
            private boolean roofSkyExposed;

            private GreenhouseEnvironment.ControllerRoomInfo frontRoom() {
                long tick = inventory.player.level().getGameTime();
                if (frontRoom == null || tick != inspectedTick) {
                    frontRoom = GreenhouseEnvironment.controllerRoomInfo(inventory.player.level(), unit.getBlockPos());
                    roofSkyExposed = GreenhouseEnvironment.hasSkyGlassAbove(inventory.player.level(), unit.getBlockPos()
                            .relative(unit.getBlockState().getValue(AirConditionerBlock.FACING)));
                    inspectedTick = tick;
                }
                return frontRoom;
            }

            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> unit.selectedClimate().ordinal();
                    case 1 -> unit.energyStored() & 0xFFFF;
                    case 2 -> unit.energyStored() >>> 16;
                    case 3 -> unit.currentEnergyCost() & 0xFFFF;
                    case 4 -> unit.currentEnergyCost() >>> 16;
                    case 5 -> unit.currentClimate().ordinal();
                    case 6 -> Math.round(unit.oxygenPercent() * 100.0F);
                    case 7 -> unit.getBlockState().getValue(AirConditionerBlock.ACTIVE) ? 1 : 0;
                    case 8 -> frontRoom().inspection().roomVolume() & 0xFFFF;
                    case 9 -> frontRoom().inspection().roomVolume() >>> 16;
                    case 10 -> unit.selectedClimate().ordinal();
                    case 11 -> unit.localClimate().ordinal();
                    case 12 -> RoomStatus.fromKey(frontRoom().inspection().status()).ordinal();
                    case 13 -> frontRoom().inspection().roomVolume() & 0xFFFF;
                    case 14 -> frontRoom().inspection().roomVolume() >>> 16;
                    case 15 -> frontRoom().interiorCapacity() & 0xFFFF;
                    case 16 -> frontRoom().interiorCapacity() >>> 16;
                    case 17 -> frontRoom().installedUnits();
                    case 18 -> frontRoom().requiredUnits();
                    case 19 -> frontRoom().inspection().climate().ordinal();
                    case 20 -> frontRoom().inspection().enclosed() ? 1 : 0;
                    case 21 -> frontRoom().inspection().glazing().roofFound() & 0xFFFF;
                    case 22 -> frontRoom().inspection().glazing().roofFound() >>> 16;
                    case 23 -> frontRoom().inspection().glazing().roofRequired() & 0xFFFF;
                    case 24 -> frontRoom().inspection().glazing().roofRequired() >>> 16;
                    case 25 -> { frontRoom(); yield roofSkyExposed ? 1 : 0; }
                    case 26 -> unit.enabled() ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return DATA_COUNT; }
        });
        GreenhouseRoomScanner.prioritize(inventory.player.level(), unit.getBlockPos()
                .relative(unit.getBlockState().getValue(AirConditionerBlock.FACING)));
    }

    private AirConditionerMenu(int id, Inventory inventory, BlockPos position, ContainerData data) {
        super(TYPE.get(), id);
        this.position = position.immutable();
        this.level = inventory.player.level();
        this.owner = inventory.player;
        this.data = data;
        addDataSlots(data);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 121 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 179));
    }

    private static ContainerData clientData() {
        SimpleContainerData data = new SimpleContainerData(DATA_COUNT);
        data.set(0, CropClimate.NEUTRAL.ordinal());
        data.set(5, CropClimate.NEUTRAL.ordinal());
        data.set(6, 10000);
        data.set(10, CropClimate.NEUTRAL.ordinal());
        data.set(11, CropClimate.NEUTRAL.ordinal());
        data.set(15, 256);
        data.set(19, CropClimate.NEUTRAL.ordinal());
        data.set(26, 1);
        return data;
    }

    public CropClimate currentClimate() { return climate(data.get(5)); }
    public float oxygenPercent() { return Math.clamp(data.get(6) / 100.0F, 0.0F, 100.0F); }
    public int energyStored() { return Math.max(0, combined(data.get(1), data.get(2))); }
    public int energyCost() { return Math.max(0, combined(data.get(3), data.get(4))); }
    public int roomVolume() { return Math.max(0, combined(data.get(8), data.get(9))); }
    public boolean active() { return data.get(7) != 0; }
    public boolean enabled() { return data.get(26) != 0; }
    public CropClimate targetClimate() { return climate(data.get(0)); }
    public CropClimate localClimate() { return climate(data.get(11)); }
    public RoomStatus roomStatus() { return RoomStatus.values()[Math.clamp(data.get(12), 0, RoomStatus.values().length - 1)]; }
    public int roomInteriorBlocks() { return Math.max(0, combined(data.get(13), data.get(14))); }
    public int roomInteriorCapacity() { return Math.max(0, combined(data.get(15), data.get(16))); }
    public int installedUnits() { return Math.max(0, data.get(17)); }
    public int requiredUnits() { return Math.max(0, data.get(18)); }
    public CropClimate roomClimate() { return climate(data.get(19)); }
    public boolean roomEnclosed() { return data.get(20) != 0; }
    public int roofGlassFound() { return Math.max(0, combined(data.get(21), data.get(22))); }
    public int roofGlassRequired() { return Math.max(0, combined(data.get(23), data.get(24))); }
    public boolean roofSkyExposed() { return data.get(25) != 0; }

    public enum RoomStatus {
        SCANNING("scanning"), VALID("valid"), LEAKING("leaking"), OPEN("open"), NO_GLASS("no_glass"),
        MISSING_CONTROLLER("missing_controller"), MISSING_SPECIMENS("missing_specimens"), WRONG_CLIMATE("wrong_climate"),
        MISSING_FLOOR("missing_floor"), FILLING_AIR("filling_air"), AMBIGUOUS("ambiguous"),
        TOO_LARGE("too_large"), INSUFFICIENT_CAPACITY("insufficient_capacity"),
        UNLOADED("unloaded"), DISABLED("disabled"), BLOCKED_OUTLET("blocked_outlet"), OUTSIDE("outside");

        private final String status;
        RoomStatus(String status) { this.status = status; }
        public Component message() { return Component.translatable("biome_based_crops.greenhouse.status." + status); }
        private static RoomStatus fromKey(String key) {
            for (RoomStatus value : values()) if (key.endsWith("." + value.status)) return value;
            return OPEN;
        }
    }

    private static int combined(int low, int high) {
        return (low & 0xFFFF) | ((high & 0xFFFF) << 16);
    }

    private static CropClimate climate(int ordinal) {
        return CropClimate.values()[Math.clamp(ordinal, 0, CropClimate.values().length - 1)];
    }

    @Override public boolean stillValid(Player player) {
        return player == owner && player.level() == level && player.level().getBlockEntity(position) instanceof AirConditionerBlockEntity
                && player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) <= 64;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide || button < 0 || button > TOGGLE_POWER || !stillValid(player)) return false;
        if (player.level().getBlockEntity(position) instanceof AirConditionerBlockEntity unit) {
            if (button == TOGGLE_POWER) unit.setEnabled(!unit.enabled());
            else unit.setClimate(CropClimate.values()[button]);
            broadcastChanges();
            return true;
        }
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (!(index < 27 ? moveItemStackTo(stack, 27, 36, false) : moveItemStackTo(stack, 0, 27, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
