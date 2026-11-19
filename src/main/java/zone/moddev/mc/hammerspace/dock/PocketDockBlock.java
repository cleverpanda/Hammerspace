package zone.moddev.mc.hammerspace.dock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import zone.moddev.mc.hammerspace.pocket.PocketItem;
import zone.moddev.mc.hammerspace.pocket.PocketMenu;

/**
 * Right-click with a pocket to dock it, right-click to open the docked pocket, sneak-right-click with an empty
 * hand to take it back. Hoppers and pipes reach the pocket's contents through the block.
 */
public class PocketDockBlock extends BaseEntityBlock {
    public static final MapCodec<PocketDockBlock> CODEC = simpleCodec(PocketDockBlock::new);

    public PocketDockBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PocketDockBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof PocketItem) || !(level.getBlockEntity(pos) instanceof PocketDockBlockEntity dock)
                || !dock.getPocket().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            dock.setPocket(stack.copy());
            stack.setCount(0);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PocketDockBlockEntity dock) || dock.getPocket().isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (player.isSecondaryUseActive()) {
                if (!player.getInventory().add(dock.getPocket())) player.drop(dock.getPocket(), false);
                dock.setPocket(ItemStack.EMPTY);
            } else if (player instanceof ServerPlayer serverPlayer) {
                PocketMenu.openDock(serverPlayer, dock);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PocketDockBlockEntity dock) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), dock.getPocket());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PocketDockBlockEntity dock && !dock.getPocket().isEmpty() ? 15 : 0;
    }
}
