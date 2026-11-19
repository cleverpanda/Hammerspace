package zone.moddev.mc.hammerspace.dock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.hammerspace.HSRegistries;
import zone.moddev.mc.hammerspace.pocket.PocketHandler;

/** Holds one pocket and exposes its contents to pipes. */
public class PocketDockBlockEntity extends BlockEntity {
    private ItemStack pocket = ItemStack.EMPTY;

    public PocketDockBlockEntity(BlockPos pos, BlockState state) {
        super(HSRegistries.DOCK_ENTITY.get(), pos, state);
    }

    public ItemStack getPocket() {
        return pocket;
    }

    public void setPocket(ItemStack stack) {
        pocket = stack;
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public @Nullable PocketHandler handler() {
        return pocket.isEmpty() ? null : new PocketHandler(this::getPocket, this::setChanged);
    }

    /** One-slot view for the pocket menu's hidden slot. */
    public Container asContainer() {
        return new SimpleContainer(1) {
            @Override
            public ItemStack getItem(int slot) {
                return pocket;
            }

            @Override
            public void setItem(int slot, ItemStack stack) {
                setPocket(stack);
            }

            @Override
            public boolean isEmpty() {
                return pocket.isEmpty();
            }

            @Override
            public void setChanged() {
                PocketDockBlockEntity.this.setChanged();
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!pocket.isEmpty()) tag.put("pocket", pocket.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pocket = tag.contains("pocket") ? ItemStack.parseOptional(registries, tag.getCompound("pocket")) : ItemStack.EMPTY;
    }

    // The client only needs to know which pocket to draw, not what's in it.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!pocket.isEmpty()) tag.put("pocket", new ItemStack(pocket.getItem()).save(registries));
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
