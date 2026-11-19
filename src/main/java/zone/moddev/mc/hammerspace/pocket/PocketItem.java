package zone.moddev.mc.hammerspace.pocket;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import zone.moddev.mc.hammerspace.pocket.PocketContents.Entry;

/**
 * A pocket. Right-click opens it; right-clicking a block places the selected item (blocks and fluid buckets),
 * keeping back as many as the slot's place mode says. Sneak-right-click a block to open it instead.
 */
public class PocketItem extends Item {
    public PocketItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int inventorySlot(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) PocketMenu.openHeld(serverPlayer, inventorySlot(player, hand));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null || player.isSecondaryUseActive()) return InteractionResult.PASS; // falls through to use()
        PocketHandler pocket = PocketHandler.of(ctx.getItemInHand());
        Entry selected = pocket.selectedEntry();
        if (selected == null) return InteractionResult.PASS;
        boolean free = player.hasInfiniteMaterials() || pocket.tier().infinite();
        if (!free && selected.count() <= selected.place()) return InteractionResult.FAIL;

        ItemStack one = selected.stack(1);
        BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), ctx.isInside());
        InteractionResult result = InteractionResult.PASS;
        if (one.getItem() instanceof BlockItem block) {
            result = block.place(new BlockPlaceContext(ctx.getLevel(), player, ctx.getHand(), one, hit));
        } else if (one.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) {
            result = emptyBucket(ctx, player, bucket, one, hit, free);
        }
        if (result.consumesAction() && !free && !ctx.getLevel().isClientSide) {
            pocket.take(pocket.selected(), 1, true, false);
        }
        return result;
    }

    private static InteractionResult emptyBucket(UseOnContext ctx, Player player, BucketItem bucket, ItemStack one,
                                                 BlockHitResult hit, boolean free) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(player, level, pos, level.getBlockState(pos), bucket.content))) {
            pos = pos.relative(ctx.getClickedFace());
        }
        if (!level.mayInteract(player, pos) || !bucket.emptyContents(player, level, pos, hit, one)) return InteractionResult.FAIL;
        bucket.checkExtraContent(player, level, one, pos);
        if (!free && !level.isClientSide) player.getInventory().placeItemBackInInventory(new ItemStack(Items.BUCKET));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PocketTier tier = PocketTier.of(stack);
        PocketHandler pocket = PocketHandler.of(stack);
        tooltip.add(Component.translatable(tier.infinite() ? "hammerspace.tooltip.size_infinite" : "hammerspace.tooltip.size",
                tier.slots(), Counts.format(tier.maxPerSlot())).withStyle(ChatFormatting.GRAY));
        int kinds = pocket.contents().entries().size();
        if (kinds > 0) tooltip.add(Component.translatable("hammerspace.tooltip.kinds", kinds).withStyle(ChatFormatting.GRAY));
        Entry selected = pocket.selectedEntry();
        if (selected != null) {
            tooltip.add(Component.translatable("hammerspace.tooltip.selected", selected.item().getHoverName(),
                    tier.infinite() ? "∞" : Counts.format(selected.count())).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (pocket.contents().locked()) tooltip.add(Component.translatable("hammerspace.tooltip.locked").withStyle(ChatFormatting.RED));
    }
}
