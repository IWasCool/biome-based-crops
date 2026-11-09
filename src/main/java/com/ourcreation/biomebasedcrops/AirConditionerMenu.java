package com.ourcreation.biomebasedcrops;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AirConditionerMenu extends AbstractContainerMenu {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.MENU, BiomeBasedCrops.MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<AirConditionerMenu>> TYPE = MENUS.register(
            "air_conditioner", () -> IMenuTypeExtension.create(AirConditionerMenu::new));
    private final BlockPos position;
    private final ContainerData data;

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }

    public AirConditionerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), new SimpleContainerData(5));
    }

    public AirConditionerMenu(int id, Inventory inventory, AirConditionerBlockEntity unit) {
        this(id, inventory, unit.getBlockPos(), new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> unit.selectedClimate().ordinal();
                    case 1 -> unit.energyStored();
                    case 2 -> unit.currentEnergyCost();
                    case 3 -> unit.localClimate().ordinal();
                    case 4 -> unit.getBlockState().getValue(AirConditionerBlock.ACTIVE) ? 1 : 0;
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 5; }
        });
    }

    private AirConditionerMenu(int id, Inventory inventory, BlockPos position, ContainerData data) {
        super(TYPE.get(), id);
        this.position = position.immutable();
        this.data = data;
        addDataSlots(data);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 121 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 8 + column * 18, 179));
    }

    public CropClimate targetClimate() { return climate(data.get(0)); }
    public int energyStored() { return data.get(1); }
    public int energyCost() { return data.get(2); }
    public CropClimate localClimate() { return climate(data.get(3)); }
    public boolean active() { return data.get(4) != 0; }

    private static CropClimate climate(int ordinal) {
        return CropClimate.values()[Math.clamp(ordinal, 0, CropClimate.values().length - 1)];
    }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(position) instanceof AirConditionerBlockEntity
                && player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) <= 64;
    }

    @Override public boolean clickMenuButton(Player player, int target) {
        if (!stillValid(player) || target < 0 || target >= CropClimate.values().length) return false;
        if (player.level().getBlockEntity(position) instanceof AirConditionerBlockEntity unit) {
            unit.setClimate(CropClimate.values()[target]);
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
